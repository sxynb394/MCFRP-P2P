package com.mcfrp.manager;

import com.mcfrp.McFrpClient;
import com.mcfrp.config.McfrpConfig;
import com.mcfrp.util.SimpleJson;
import com.mcfrp.util.SimpleJson.P2PEntry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class P2PEntryManager {

    private static final String KEY_ENTRIES = "p2p_entries";

    private static final P2PEntryManager INSTANCE = new P2PEntryManager();

    public static P2PEntryManager getInstance() {
        return INSTANCE;
    }

    private final List<P2PEntry> entries = new ArrayList<>();

    private P2PEntryManager() {}

    public synchronized void loadEntries() {
        entries.clear();
        try {
            java.nio.file.Path path = McfrpConfig.getConfigPath();
            if (!java.nio.file.Files.exists(path)) {
                McFrpClient.LOGGER.info("[MCFRP] P2P 条目文件不存在，初始化为空列表");
                return;
            }

            Properties props = McfrpConfig.loadAll();
            String json = props.getProperty(KEY_ENTRIES, "[]");
            List<P2PEntry> list = SimpleJson.entriesFromJson(json);
            entries.addAll(list);
            McFrpClient.LOGGER.info("[MCFRP] P2P 条目已加载: 共 {} 条", entries.size());
            migrateOldPrefixedEntries();
        } catch (Exception e) {
            McFrpClient.LOGGER.error("[MCFRP] 条目加载失败: 无法读取 P2P 条目列表", e);
        }
    }
    public synchronized void saveEntry(String serverName, String frpsAddr, int frpsPort,
                                       String secretKey) {
        Iterator<P2PEntry> it = entries.iterator();
        int keepPort = 0;
        while (it.hasNext()) {
            P2PEntry e = it.next();
            if (e.serverName.equals(serverName)) {
                keepPort = e.localPort;
                it.remove();
            }
        }
        int localPort = keepPort != 0 ? keepPort : allocateLocalPort();
        P2PEntry entry = new P2PEntry(serverName, frpsAddr, frpsPort, secretKey, localPort,
            System.currentTimeMillis());
        entries.add(entry);
        persist();
        upsertVanillaServer(entry);
    }

    private int allocateLocalPort() {
        int port = 25566;
        outer:
        while (port < 25600) {
            for (P2PEntry e : entries) {
                if (e.localPort == port) {
                    port++;
                    continue outer;
                }
            }
            return port;
        }
        return port;
    }

    private void migrateOldPrefixedEntries() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        try {
            ServerList list = new ServerList(client);
            list.loadFile();
            boolean changed = false;
            for (int i = 0; i < list.size(); i++) {
                ServerInfo info = list.get(i);
                if (info != null && info.name != null && info.name.startsWith("[P2P] ")) {
                    String realName = info.name.substring(6);
                    info.name = realName;
                    changed = true;
                }
            }
            if (changed) {
                list.saveFile();
                McFrpClient.LOGGER.info("[MCFRP] 已迁移旧格式 [P2P] 前缀条目");
            }
        } catch (Throwable t) {
            McFrpClient.LOGGER.warn("[MCFRP] 迁移旧条目失败", t);
        }
    }

    public synchronized void deleteEntry(String serverName) {
        entries.removeIf(e -> e.serverName.equals(serverName));
        persist();
        removeVanillaServer(serverName);
    }

    public synchronized List<P2PEntry> getAllEntries() {
        return new ArrayList<>(entries);
    }

    public synchronized P2PEntry findByServerName(String serverName) {
        if (serverName == null) return null;
        String clean = serverName.startsWith("[P2P] ") ? serverName.substring(6) : serverName;
        for (P2PEntry e : entries) {
            if (e.serverName.equals(clean)) return e;
        }
        return null;
    }

    private void upsertVanillaServer(P2PEntry entry) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        try {
            ServerList list = new ServerList(client);
            list.loadFile();
            String name = entry.serverName;
            String address = "127.0.0.1:" + (entry.localPort > 0 ? entry.localPort : 25566);
            boolean replaced = false;
            for (int i = 0; i < list.size(); i++) {
                ServerInfo info = list.get(i);
                if (info != null && name.equals(info.name)) {
                    info.address = address;
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                list.add(new ServerInfo(name, address, false), false);
            }
            list.saveFile();
        } catch (Throwable t) {
            McFrpClient.LOGGER.warn("[MCFRP] 同步失败: 无法将 P2P 条目同步到服务器列表", t);
        }
    }

    private void removeVanillaServer(String serverName) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        try {
            ServerList list = new ServerList(client);
            list.loadFile();
            String name = serverName;
            for (int i = list.size() - 1; i >= 0; i--) {
                ServerInfo info = list.get(i);
                if (info != null && name.equals(info.name)) {
                    list.remove(info);
                }
            }
            list.saveFile();
        } catch (Throwable t) {
            McFrpClient.LOGGER.warn("[MCFRP] 同步失败: 无法从服务器列表移除 P2P 条目", t);
        }
    }

    private void persist() {
        McfrpConfig.store(props -> {
            props.setProperty(KEY_ENTRIES, SimpleJson.entriesToJson(entries));
        });
        McFrpClient.LOGGER.info("[MCFRP] P2P 条目已持久化: 共 {} 条", entries.size());
    }}
