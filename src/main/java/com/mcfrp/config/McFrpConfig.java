package com.mcfrp.config;

import com.mcfrp.McFrpClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 统一的 Properties 配置文件管理。
 *
 * FrpsConfigManager 和 P2PEntryManager 共用同一个 mcfrp.properties，
 * 避免各自独立 load-modify-store 导致的丢失更新问题。
 */
public final class McfrpConfig {

    private static final String FILE_NAME = "mcfrp.properties";
    private static final String STORE_COMMENT = "MC FRP P2P configuration";

    private static Path configPath;

    private McfrpConfig() {}

    /**
     * 获取配置文件路径（懒初始化）。
     */
    public static synchronized Path getConfigPath() {
        if (configPath == null) {
            FabricLoader loader = FabricLoader.getInstance();
            Path configDir = loader.getConfigDir();
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                throw new RuntimeException("[MCFRP] 无法创建配置目录: " + configDir, e);
            }
            configPath = configDir.resolve(FILE_NAME);
        }
        return configPath;
    }

    /**
     * 加载全部 Properties，调用方自行解读所需 key。
     * 文件不存在时返回空的 Properties。
     */
    public static synchronized Properties loadAll() {
        Properties props = new Properties();
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                props.load(fis);
            } catch (IOException e) {
                McFrpClient.LOGGER.warn("[MCFRP] 配置文件读取失败，已退回空配置", e);
            }
        }
        return props;
    }

    /**
     * 原子地读取-修改-写回。
     *
     * 先加载已有 Properties，回调 updater 修改，再写回文件。
     * 整个过程持有类锁，保证 FrpsConfigManager 和 P2PEntryManager 的并发写不会互相覆盖。
     */
    public static synchronized void store(PropertiesUpdater updater) {
        Path path = getConfigPath();
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (FileInputStream fis = new FileInputStream(path.toFile())) {
                props.load(fis);
            } catch (IOException e) {
                McFrpClient.LOGGER.warn("[MCFRP] 配置文件读取失败，将覆盖写入", e);
            }
        }
        updater.update(props);
        try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
            props.store(fos, STORE_COMMENT);
        } catch (IOException e) {
            McFrpClient.LOGGER.error("[MCFRP] 配置文件写入失败", e);
        }
    }

    @FunctionalInterface
    public interface PropertiesUpdater {
        void update(Properties props);
    }
}
