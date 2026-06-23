package com.mcfrp.command;

import com.mcfrp.host.HostManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class FrpcCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                 CommandRegistryAccess registryAccess,
                                 CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            CommandManager.literal("mcfrp")
                .then(CommandManager.literal("restart")
                    .executes(ctx -> {
                        ServerCommandSource source = ctx.getSource();
                        HostManager host = HostManager.getInstance();
                        HostManager.Status status = host.getStatus();
                        if (status == HostManager.Status.IDLE) {
                            source.sendFeedback(
                                () -> Text.literal("[MCFRP] 联机尚未启动，请先使用 [对局域网开放]"),
                                false);
                            return 0;
                        }
                        host.restart();
                        return 1;
                    })
                )
        );
    }
}
