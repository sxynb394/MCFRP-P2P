package com.mcfrp.frpc;

import com.mcfrp.McFrpClient;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public class FrpcProcess {
    private static final FrpcProcess INSTANCE = new FrpcProcess();

    public static FrpcProcess getInstance() {
        return INSTANCE;
    }

    private Process process;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private FrpcProcess() {
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (running.get()) {
                McFrpClient.LOGGER.info("Shutdown hook: stopping frpc...");
                stop();
            }
        }));
    }

    public synchronized void start(Path configPath) throws IOException {
        // Stop existing process first
        stop();

        Path frpcPath = FrpcBinary.extractFrpc();

        ProcessBuilder pb = new ProcessBuilder(
            frpcPath.toString(),
            "-c",
            configPath.toString()
        );
        pb.redirectErrorStream(true);

        process = pb.start();
        running.set(true);

        McFrpClient.LOGGER.info("Frpc process started with config: {}", configPath);

        // Daemon thread to read output
        Thread readerThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    McFrpClient.LOGGER.info("[frpc] {}", line);
                }
            } catch (IOException e) {
                McFrpClient.LOGGER.error("Error reading frpc output", e);
            }
        });
        readerThread.setDaemon(true);
        readerThread.start();

        // Check if process dies immediately
        process.onExit().thenRun(() -> {
            running.set(false);
            McFrpClient.LOGGER.info("Frpc process exited");
        });
    }

    public synchronized void stop() {
        if (process != null && process.isAlive()) {
            McFrpClient.LOGGER.info("Stopping frpc process...");
            process.destroy();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (process.isAlive()) {
                McFrpClient.LOGGER.info("Frpc did not stop gracefully, forcing...");
                process.destroyForcibly();
            }
        }
        running.set(false);
        process = null;
    }

    public boolean isRunning() {
        return running.get() && process != null && process.isAlive();
    }
}
