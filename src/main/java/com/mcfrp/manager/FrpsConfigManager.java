package com.mcfrp.manager;

import com.mcfrp.McFrpClient;
import com.mcfrp.config.McfrpConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * FRPS 配置管理器
 *
 * 配置项（存储于 .minecraft/config/mcfrp.properties）：
 *  - frps.mode: "default" 或 "custom"
 *  - frps.custom.addr: 自定义 frps 地址
 *  - frps.custom.port: 自定义 frps 端口
 *  - frps.token: 认证 token（房主 frpc.toml 的 auth.token 与此一致）
 *
 * 默认模式下优先从远程拉取最新 frps 配置（内存缓存，不落盘）；
 * 拉取失败或自定义模式下降级到硬编码兜底值。
 */
public class FrpsConfigManager {

    public enum Mode { DEFAULT, CUSTOM }

    private static final String KEY_MODE = "frps.mode";
    private static final String KEY_CUSTOM_ADDR = "frps.custom.addr";
    private static final String KEY_CUSTOM_PORT = "frps.custom.port";
    private static final String KEY_TOKEN = "frps.token";

    private static final String REMOTE_CONFIG_URL =
        "https://你的域名/随机路径/frps-config.json";
    private static final int REMOTE_FETCH_TIMEOUT_SEC = 5;

    // 硬编码兜底值（远程拉取失败或自定义模式不使用远程时回退到此）
    private static final String FALLBACK_ADDR = "";  // ???????? FRPS ??
    private static final int FALLBACK_PORT = 0;  // ???????? FRPS ??
    private static final String FALLBACK_TOKEN = "";  // ???????? FRPS Token

    private static final FrpsConfigManager INSTANCE = new FrpsConfigManager();

    public static FrpsConfigManager getInstance() {
        return INSTANCE;
    }

    private Mode mode = Mode.DEFAULT;
    private String customAddr = "";
    private int customPort = FALLBACK_PORT;
    private String token = FALLBACK_TOKEN;

    // 远程配置内存缓存
    private String remoteCachedAddr = null;
    private int remoteCachedPort = 0;
    private String remoteCachedToken = null;
    private volatile boolean remoteConfigLoaded = false;

    private FrpsConfigManager() {}

    public synchronized void load() {
        try {
            java.nio.file.Path path = McfrpConfig.getConfigPath();
            if (!java.nio.file.Files.exists(path)) {
                McfrpConfig.store(props -> {
                    props.setProperty(KEY_MODE, "default");
                    props.setProperty(KEY_CUSTOM_ADDR, "");
                    props.setProperty(KEY_CUSTOM_PORT, String.valueOf(FALLBACK_PORT));
                    props.setProperty(KEY_TOKEN, FALLBACK_TOKEN);
                });
                McFrpClient.LOGGER.info("[MCFRP] 已创建默认 frps 配置: {}", path);
            }

            Properties props = McfrpConfig.loadAll();

            String modeStr = props.getProperty(KEY_MODE, "default").trim().toLowerCase();
            mode = "custom".equals(modeStr) ? Mode.CUSTOM : Mode.DEFAULT;
            customAddr = props.getProperty(KEY_CUSTOM_ADDR, "").trim();
            try {
                customPort = Integer.parseInt(props.getProperty(KEY_CUSTOM_PORT,
                    String.valueOf(FALLBACK_PORT)).trim());
            } catch (NumberFormatException e) {
                customPort = FALLBACK_PORT;
            }
            token = props.getProperty(KEY_TOKEN, FALLBACK_TOKEN).trim();
            if (token.isEmpty()) token = FALLBACK_TOKEN;

            McFrpClient.LOGGER.info("[MCFRP] frps 配置已加载: mode={}, addr={}:{}, token=[REDACTED]",
                mode, getFrpsAddr(), getFrpsPort());
        } catch (Exception e) {
            McFrpClient.LOGGER.error("[MCFRP] 配置加载失败: 无法读取 frps 配置，已回退默认值", e);
            mode = Mode.DEFAULT;
            customAddr = "";
            customPort = FALLBACK_PORT;
            token = FALLBACK_TOKEN;
        }
    }
    public synchronized void save() {
        McfrpConfig.store(props -> {
            props.setProperty(KEY_MODE, mode == Mode.CUSTOM ? "custom" : "default");
            props.setProperty(KEY_CUSTOM_ADDR, customAddr == null ? "" : customAddr);
            props.setProperty(KEY_CUSTOM_PORT, String.valueOf(customPort));
            props.setProperty(KEY_TOKEN, token);
        });
        McFrpClient.LOGGER.info("[MCFRP] frps 配置已保存: mode={}, addr={}:{}",
            mode, getFrpsAddr(), getFrpsPort());
    }
    // ─── 远程配置拉取 ───

    /**
     * 确保远程配置已加载（默认模式专用）。
     * 首次调用时发起网络请求，最多阻塞 {@link #REMOTE_FETCH_TIMEOUT_SEC} 秒。
     */
    private synchronized void ensureRemoteConfig() {
        if (remoteConfigLoaded) return;
        remoteConfigLoaded = true; // 只尝试一次，失败降级

        if (REMOTE_CONFIG_URL.contains("你的域名")) {
            McFrpClient.LOGGER.info("[MCFRP] 远程配置 URL 未设置，跳过拉取");
            return;
        }

        try {
            CompletableFuture<RemoteConfig> future = CompletableFuture.supplyAsync(
                FrpsConfigManager::doFetchRemoteConfig);
            RemoteConfig cfg = future.get(REMOTE_FETCH_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (cfg != null) {
                remoteCachedAddr = cfg.addr;
                remoteCachedPort = cfg.port;
                remoteCachedToken = cfg.token;
                McFrpClient.LOGGER.info("[MCFRP] 远程配置拉取成功");
            }
        } catch (Exception e) {
            McFrpClient.LOGGER.warn("[MCFRP] 远程配置拉取失败，使用硬编码配置: {}",
                e.getMessage());
        }
    }

    private static RemoteConfig doFetchRemoteConfig() {
        try {
            URI uri = URI.create(REMOTE_CONFIG_URL);
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.connect();

            int code = conn.getResponseCode();
            if (code != 200) {
                throw new IOException("HTTP " + code);
            }

            byte[] body;
            try (InputStream in = conn.getInputStream()) {
                body = in.readAllBytes();
            }
            String json = new String(body, StandardCharsets.UTF_8).trim();

            String addr = extractJsonString(json, "addr");
            int port = extractJsonInt(json, "port");
            String token = extractJsonString(json, "token");

            if (addr == null || addr.isEmpty() || token == null || token.isEmpty() || port <= 0) {
                throw new IOException("missing fields in remote config");
            }

            return new RemoteConfig(addr, port, token);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx < 0) return null;
        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx < 0) return null;
        int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) return null;
        return json.substring(startQuote + 1, endQuote);
    }

    private static int extractJsonInt(String json, String key) {
        String pattern = "\"" + key + "\"";
        int keyIdx = json.indexOf(pattern);
        if (keyIdx < 0) return 0;
        int colonIdx = json.indexOf(':', keyIdx + pattern.length());
        if (colonIdx < 0) return 0;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ─── 访问器 ───

    public Mode getMode() { return mode; }
    public void setMode(Mode mode) { this.mode = mode; }

    public String getCustomAddr() { return customAddr; }
    public void setCustomAddr(String addr) { this.customAddr = addr == null ? "" : addr.trim(); }

    public int getCustomPort() { return customPort; }
    public void setCustomPort(int port) { this.customPort = port; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = (token == null || token.isEmpty()) ? FALLBACK_TOKEN : token; }

    public String getFrpsAddr() {
        if (mode == Mode.CUSTOM && !customAddr.isEmpty()) return customAddr;
        ensureRemoteConfig();
        if (remoteCachedAddr != null && !remoteCachedAddr.isEmpty()) return remoteCachedAddr;
        return FALLBACK_ADDR;
    }

    public int getFrpsPort() {
        if (mode == Mode.CUSTOM && customPort > 0) return customPort;
        ensureRemoteConfig();
        if (remoteCachedPort > 0) return remoteCachedPort;
        return FALLBACK_PORT;
    }

    public String getDefaultAddr() {
        ensureRemoteConfig();
        if (remoteCachedAddr != null && !remoteCachedAddr.isEmpty()) return remoteCachedAddr;
        return FALLBACK_ADDR;
    }

    public int getDefaultPort() {
        ensureRemoteConfig();
        if (remoteCachedPort > 0) return remoteCachedPort;
        return FALLBACK_PORT;
    }

    public String getEffectiveToken() {
        if (mode == Mode.CUSTOM) return token;
        ensureRemoteConfig();
        if (remoteCachedToken != null && !remoteCachedToken.isEmpty()) return remoteCachedToken;
        return FALLBACK_TOKEN;
    }

    // ─── 内部类型 ───

    private static class RemoteConfig {
        final String addr;
        final int port;
        final String token;

        RemoteConfig(String addr, int port, String token) {
            this.addr = addr;
            this.port = port;
            this.token = token;
        }
    }
}
