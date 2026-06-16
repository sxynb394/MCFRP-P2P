package com.mcfrp.frpc;

import com.mcfrp.McFrpClient;
import com.mcfrp.config.McFrpConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FrpcConfig {
    private static final String HOST_TEMPLATE =
        "serverAddr = \"{frpsHost}\"\n" +
        "serverPort = {frpsPort}\n" +
        "auth.method = \"token\"\n" +
        "auth.token = \"{token}\"\n" +
        "\n" +
        "[[proxies]]\n" +
        "name = \"mc-{shortId}\"\n" +
        "type = \"xtcp\"\n" +
        "secretKey = \"{secretKey}\"\n" +
        "localIP = \"127.0.0.1\"\n" +
        "localPort = {lanPort}\n";

    private static final String VISITOR_TEMPLATE =
        "serverAddr = \"{frpsHost}\"\n" +
        "serverPort = {frpsPort}\n" +
        "auth.method = \"token\"\n" +
        "auth.token = \"{token}\"\n" +
        "\n" +
        "[[visitors]]\n" +
        "name = \"mc-visitor-{shortId}\"\n" +
        "type = \"xtcp\"\n" +
        "serverName = \"mc-{shortId}\"\n" +
        "secretKey = \"{secretKey}\"\n" +
        "bindAddr = \"127.0.0.1\"\n" +
        "bindPort = {localBindPort}\n" +
        "keepTunnelOpen = true\n";

    public static Path writeHostConfig(String secretKey, int lanPort) throws IOException {
        return writeHostConfig(secretKey, getFrpsHost(), getFrpsPort(), getFrpsToken(), lanPort);
    }

    public static Path writeHostConfig(String secretKey, String frpsHost, int frpsPort, String token, int lanPort) throws IOException {
        String shortId = secretKey.length() >= 8 ? secretKey.substring(0, 8) : secretKey;

        String content = HOST_TEMPLATE
            .replace("{frpsHost}", toTomlString(frpsHost))
            .replace("{frpsPort}", String.valueOf(frpsPort))
            .replace("{token}", toTomlString(token))
            .replace("{shortId}", shortId)
            .replace("{secretKey}", toTomlString(secretKey))
            .replace("{lanPort}", String.valueOf(lanPort));

        return writeConfig("frpc-host.toml", content);
    }

    public static Path writeVisitorConfig(String secretKey, String frpsHost, int frpsPort, String token, int localBindPort) throws IOException {
        String shortId = secretKey.length() >= 8 ? secretKey.substring(0, 8) : secretKey;

        String content = VISITOR_TEMPLATE
            .replace("{frpsHost}", toTomlString(frpsHost))
            .replace("{frpsPort}", String.valueOf(frpsPort))
            .replace("{token}", toTomlString(token))
            .replace("{shortId}", shortId)
            .replace("{secretKey}", toTomlString(secretKey))
            .replace("{localBindPort}", String.valueOf(localBindPort));

        return writeConfig("frpc-visitor.toml", content);
    }

    private static Path writeConfig(String filename, String content) throws IOException {
        FabricLoader loader = FabricLoader.getInstance();
        Path mcfrpDir = loader.getGameDir().resolve(".mcfrp");

        if (!Files.exists(mcfrpDir)) {
            Files.createDirectories(mcfrpDir);
        }

        Path configPath = mcfrpDir.resolve(filename);
        Files.writeString(configPath, content);
        McFrpClient.LOGGER.info("Written frpc config to: {}", configPath);
        return configPath;
    }

    private static String toTomlString(String s) {
        if (s == null) return "";
        if (s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\\\"") + "\"";
        }
        return s;
    }

    private static String getFrpsHost() {
        return McFrpConfig.instance.frpsHost;
    }

    private static int getFrpsPort() {
        return McFrpConfig.instance.frpsPort;
    }

    private static String getFrpsToken() {
        return McFrpConfig.instance.frpsToken;
    }
}
