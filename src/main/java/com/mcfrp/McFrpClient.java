package com.mcfrp;

import com.mcfrp.config.McFrpConfig;
import com.mcfrp.frpc.FrpcProcess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McFrpClient implements ClientModInitializer {
    public static final String MOD_ID = "mcfrp";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("MC FRP P2P initializing...");

        // Load config
        McFrpConfig.load();

        // Register client stopping handler to clean up frpc
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            LOGGER.info("Client stopping, cleaning up frpc...");
            FrpcProcess.getInstance().stop();
        });

        // Register disconnect handler for visitor side cleanup
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOGGER.info("Disconnected, cleaning up visitor frpc...");
            FrpcProcess.getInstance().stop();
        });

        LOGGER.info("MC FRP P2P initialized.");
    }
}
