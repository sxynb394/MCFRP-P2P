package com.mcfrp.frpc;

import com.mcfrp.McFrpClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * frpc 进程管理器
 *
 * 负责启动/停止 frpc 二进制，以及等待访客侧本地端口就绪。
 */
public class FrpcProcess {
    private static final FrpcProcess INSTANCE = new FrpcProcess();

    public static FrpcProcess getInstance() {
        return INSTANCE;
    }

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        "(auth\\.token\\s*=\\s*)\"[^\"]+\"|" +
        "(secretKey\\s*=\\s*)\"[^\"]+\"|" +
        "(token\\s*=\\s*)[^\\s,;]+",
        Pattern.CASE_INSENSITIVE
    );

    private Process process;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<Thread> readerThread = new AtomicReference<>(null);
    private final Queue<String> startupLogLines = new ConcurrentLinkedQueue<>();
    private volatile boolean captureStartupLogs = false;

    private FrpcProcess() {
        // 注册 JVM 退出钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running.get()) {
                McFrpClient.LOGGER.info("[MCFRP] Shutdown hook: 正在停止 frpc...");
                stop();
            }
        }));
    }

    /**
     * 启动 frpc。
     * @param configPath frpc.toml 配置路径
     */
    public synchronized void start(Path configPath) throws IOException {
        stop();

        Path frpcPath = FrpcBinary.extractFrpc();

        Path workDir = frpcPath.getParent();
        if (workDir == null) {
            workDir = FabricLoader.getInstance().getGameDir().resolve(".mcfrp");
            Files.createDirectories(workDir);
        }

        ProcessBuilder pb;
        if (FrpcBinary.isAndroidEnvironment()) {
            String tmpFrpc = "/data/local/tmp/mcfrp/frpc";
            List<String> cmd = new ArrayList<>();
            cmd.add("su");
            cmd.add("-c");
            cmd.add("mkdir -p /data/local/tmp/mcfrp && rm -f " + tmpFrpc + " && cp " + frpcPath.toString() + " " + tmpFrpc + " && chmod 755 " + tmpFrpc + " && exec " + tmpFrpc + " -c " + configPath.toString());
            pb = new ProcessBuilder(cmd);
            McFrpClient.LOGGER.info("[MCFRP] Android su: cp到{}并exec", tmpFrpc);
        } else {
            pb = new ProcessBuilder(
                frpcPath.toString(),
                "-c",
                configPath.toString()
            );
        }
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        process = pb.start();
        running.set(true);

        McFrpClient.LOGGER.info("[MCFRP] frpc 进程已启动 (pid={}), config={}",
            process.pid(), configPath);

        // 启动前清空上次启动日志
        startupLogLines.clear();
        captureStartupLogs = true;

        // 启动日志读取线程
        Thread rt = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String safe = redactSensitive(line);
                    McFrpClient.LOGGER.info("[frpc] {}", safe);
                    if (captureStartupLogs) {
                        startupLogLines.offer(line);
                    }
                }
            } catch (IOException e) {
                McFrpClient.LOGGER.error("[MCFRP] frpc 输出读取异常: 读取 frpc 进程输出时发生错误", e);
            }
        }, "frpc-log-reader");
        rt.setDaemon(true);
        rt.start();
        readerThread.set(rt);

        // 等待进程退出
        process.onExit().thenRun(() -> {
            running.set(false);
            McFrpClient.LOGGER.info("[MCFRP] frpc 进程已退出 (exitCode={})", process.exitValue());
        });
    }

    /** 停止 frpc 进程 */
    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            McFrpClient.LOGGER.info("[MCFRP] 正在停止 frpc 进程...");
            process.destroy();
            try {
                if (!process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    McFrpClient.LOGGER.info("[MCFRP] frpc 未能优雅退出，强制终止...");
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        if (FrpcBinary.isAndroidEnvironment()) {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -9 frpc"}).waitFor();
                McFrpClient.LOGGER.info("[MCFRP] Android su: pkill -9 frpc 已执行");
            } catch (Exception e) {
                McFrpClient.LOGGER.warn("[MCFRP] Android su kill 失败: {}", e.getMessage());
            }
        }
        running.set(false);
        process = null;
    }

    public boolean isRunning() {
        return running.get() && process != null && process.isAlive();
    }

    /**
     * 等待本地 TCP 端口就绪（用于 visitor 打洞成功后本地端口可用时再连接游戏）
     * @param port 本地端口
     * @param timeoutSeconds 最长等待秒数
     * @return 就绪返回 true，超时返回 false
     */
    public boolean waitForLocalPort(int port, int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (!isRunning()) {
                McFrpClient.LOGGER.warn("[MCFRP] 进程异常: frpc 进程在等待端口 {} 时意外退出", port);
                return false;
            }
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", port), 500);
                McFrpClient.LOGGER.info("[MCFRP] 本地端口 {} 已就绪", port);
                return true;
            } catch (IOException ignore) {
                // 未就绪
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        McFrpClient.LOGGER.warn("[MCFRP] 端口等待超时: 本地端口 {} 在 {} 秒内未就绪", port, timeoutSeconds);
        return false;
    }

    /**
     * 启动后等待 frpc 连接 FRPS，检测日志中的成功/失败模式。
     * 用于房主侧验证 frps 服务器是否可达。
     *
     * @param timeoutSeconds 最长等待秒数
     * @return null 表示连接正常；非 null 为错误描述字符串
     */
    public String waitForConnection(int timeoutSeconds) {
        long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
        String error = null;

        while (System.currentTimeMillis() < deadline) {
            if (!isRunning()) {
                // 进程已经死了，从启动日志中找最后一条错误
                String lastErr = drainErrorFromLogs();
                if (lastErr != null) return lastErr;
                return "frpc 进程意外退出，请检查日志";
            }

            String err = drainErrorFromLogs();
            if (err != null) {
                error = err;
                break;
            }

            // 检测成功标志
            String line;
            while ((line = startupLogLines.poll()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains("start proxy success")
                    || lower.contains("login to server success")) {
                    captureStartupLogs = false;
                    startupLogLines.clear();
                    return null;
                }
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                captureStartupLogs = false;
                return "连接检测被中断";
            }
        }

        captureStartupLogs = false;

        if (error != null) {
            stop();
            return error;
        }

        // 超时但进程还活着 → 视为连上（日志中没有明显报错）
        startupLogLines.clear();
        return null;
    }

    public String drainErrorFromLogs() {
        String line;
        while ((line = startupLogLines.poll()) != null) {
            String lower = line.toLowerCase();
            if (lower.contains("connect to server error")
                || lower.contains("connection refused")
                || lower.contains("connection timeout")
                || lower.contains("i/o timeout")
                || lower.contains("authorization failed")
                || lower.contains("token invalid")
                || lower.contains("no such host")
                || lower.contains("dial tcp")
                || lower.contains("login to server failed")
                || lower.contains("start error")
                || lower.contains("xtcp server") && lower.contains("doesn't exist")
                || lower.contains("open tunnel error")) {
                return redactSensitive(line);
            }
        }
        return null;
    }

    /**
     * 对 frpc 输出行中的敏感信息进行脱敏。
     * 匹配 token / auth.token / secretKey 等模式并替换为 [REDACTED]。
     */
    private static String redactSensitive(String line) {
        if (line == null) return null;
        return SENSITIVE_PATTERN.matcher(line).replaceAll("$1$2$3[REDACTED]");
    }
}
