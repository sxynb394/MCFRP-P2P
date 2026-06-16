package com.mcfrp.frpc;

import com.mcfrp.McFrpClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

public class FrpcBinary {
    private static final String NATIVE_RESOURCE_PATH = "/assets/mcfrp/native/";

    public static Path extractFrpc() throws IOException {
        FabricLoader loader = FabricLoader.getInstance();
        Path gameDir = loader.getGameDir();
        Path mcfrpDir = gameDir.resolve(".mcfrp");

        String osName = System.getProperty("os.name").toLowerCase();
        String sourceName;
        String targetName;

        if (osName.contains("win")) {
            sourceName = "frpc-windows.exe";
            targetName = "frpc.exe";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            sourceName = "frpc-macos";
            targetName = "frpc";
        } else {
            sourceName = "frpc-linux";
            targetName = "frpc";
        }

        Path targetPath = mcfrpDir.resolve(targetName);

        // Create .mcfrp dir if not exists
        if (!Files.exists(mcfrpDir)) {
            Files.createDirectories(mcfrpDir);
        }

        // Skip if already extracted
        if (Files.exists(targetPath)) {
            McFrpClient.LOGGER.info("Frpc binary already exists at: {}", targetPath);
            return targetPath;
        }

        // Extract from classpath
        String resourcePath = NATIVE_RESOURCE_PATH + sourceName;
        try (InputStream is = FrpcBinary.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Frpc binary not found in classpath: " + resourcePath);
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            McFrpClient.LOGGER.info("Extracted frpc binary to: {}", targetPath);
        }

        // Set permissions on non-Windows
        if (!osName.contains("win")) {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(targetPath, perms);
            McFrpClient.LOGGER.info("Set executable permissions on: {}", targetPath);
        }

        return targetPath;
    }
}
