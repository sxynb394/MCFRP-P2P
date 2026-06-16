package com.mcfrp.config;

import com.mcfrp.McFrpClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class McFrpConfig {
    public String frpsHost;
    public int frpsPort;
    public String frpsToken;

    private static final String CONFIG_FILE_NAME = "mcfrp.properties";
    private static final String DEFAULT_CONFIG =
        "frps.host=your.vps.com\n" +
        "frps.port=7000\n" +
        "frps.token=mcfrp_default\n";

    public static McFrpConfig instance;

    public static void load() {
        FabricLoader loader = FabricLoader.getInstance();
        Path configDir = loader.getConfigDir();
        Path configFile = configDir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configFile)) {
            try {
                Files.createDirectories(configDir);
                Files.writeString(configFile, DEFAULT_CONFIG);
                McFrpClient.LOGGER.warn("Config file not found, created default at: {}", configFile);
            } catch (IOException e) {
                McFrpClient.LOGGER.error("Failed to create default config file", e);
                return;
            }
        }

        try {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile.toFile())) {
                props.load(fis);
            }

            instance = new McFrpConfig();
            instance.frpsHost = props.getProperty("frps.host");
            String portStr = props.getProperty("frps.port", "7000");
            instance.frpsToken = props.getProperty("frps.token");

            if (instance.frpsHost == null || instance.frpsHost.isEmpty() ||
                instance.frpsToken == null || instance.frpsToken.isEmpty()) {
                McFrpClient.LOGGER.error("Config missing required fields (frps.host, frps.token). MC FRP P2P disabled.");
                instance = null;
                return;
            }

            try {
                instance.frpsPort = Integer.parseInt(portStr.trim());
            } catch (NumberFormatException e) {
                instance.frpsPort = 7000;
                McFrpClient.LOGGER.warn("Invalid frps.port value, using default 7000");
            }

            McFrpClient.LOGGER.info("Config loaded: frpsHost={}, frpsPort={}", instance.frpsHost, instance.frpsPort);
        } catch (Exception e) {
            McFrpClient.LOGGER.error("Failed to read config file", e);
            instance = null;
        }
    }

    public static boolean isEnabled() {
        return instance != null;
    }
}
