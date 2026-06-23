package com.mcfrp.frpc;

import com.mcfrp.McFrpClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class FrpcBinary {
    private static final String NATIVE_RESOURCE_PATH = "/assets/mcfrp/native/";
    private static Boolean androidDetected = null;

    public static Path extractFrpc() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        String sourceName;
        String targetName;
        boolean compressed = false;

        boolean isAndroid = isAndroidEnvironment();
        boolean isArm64 = osArch.contains("aarch64") || osArch.contains("arm64") || isAndroid;

        if (osName.contains("win")) {
            sourceName = "frpc-windows.exe";
            targetName = "frpc.exe";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            sourceName = "frpc-macos";
            targetName = "frpc";
        } else if (isArm64) {
            sourceName = "frpc-linux-arm64.gz";
            targetName = "frpc";
            compressed = true;
        } else {
            sourceName = "frpc-linux";
            targetName = "frpc";
        }

        FabricLoader loader = FabricLoader.getInstance();
        Path gameDir = loader.getGameDir();
        Path extractDir = gameDir.resolve(".mcfrp");

        if (!Files.exists(extractDir)) {
            Files.createDirectories(extractDir);
        }

        Path targetPath = extractDir.resolve(targetName);

        if (Files.exists(targetPath)) {
            McFrpClient.LOGGER.info("[MCFRP] frpc 二进制已存在: {}", targetPath);
            if (isAndroid) {
                ensureAndroidExecutable(targetPath);
            }
            return targetPath;
        }

        String resourcePath = NATIVE_RESOURCE_PATH + sourceName;
        try (InputStream is = FrpcBinary.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Frpc binary not found in classpath: " + resourcePath);
            }
            if (compressed) {
                try (GZIPInputStream gis = new GZIPInputStream(is);
                     OutputStream os = Files.newOutputStream(targetPath)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = gis.read(buf)) != -1) {
                        os.write(buf, 0, n);
                    }
                }
            } else {
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            McFrpClient.LOGGER.info("[MCFRP] frpc 二进制已解压: {} ({} bytes)", targetPath,
                Files.size(targetPath));
        }

        if (isAndroid) {
            ensureAndroidExecutable(targetPath);
        } else if (!osName.contains("win")) {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
            Files.setPosixFilePermissions(targetPath, perms);
            McFrpClient.LOGGER.info("[MCFRP] 已设置 frpc 执行权限: {}", targetPath);
        }

        return targetPath;
    }

    public static boolean isAndroidEnvironment() {
        if (androidDetected != null) {
            return androidDetected;
        }

        try {
            Class.forName("android.os.Build");
            androidDetected = true;
            return true;
        } catch (ClassNotFoundException ignored) {
        }

        String vendor = System.getProperty("java.vendor");
        if (vendor != null && vendor.toLowerCase().contains("android")) {
            androidDetected = true;
            return true;
        }

        String osName = System.getProperty("os.name");
        if (osName != null && osName.toLowerCase().contains("linux")) {
            if (Files.exists(Paths.get("/data/data/"))) {
                androidDetected = true;
                return true;
            }
        }

        androidDetected = false;
        return false;
    }

    private static void ensureAndroidExecutable(Path targetPath) {
        try {
            Runtime.getRuntime().exec(new String[]{
                "su", "-c", "chmod 755 " + targetPath.toString()
            }).waitFor();
            McFrpClient.LOGGER.info("[MCFRP] Android chmod 755: {}", targetPath);
        } catch (Exception e) {
            McFrpClient.LOGGER.warn("[MCFRP] Android chmod 失败: {}", e.getMessage());
        }
    }
}
