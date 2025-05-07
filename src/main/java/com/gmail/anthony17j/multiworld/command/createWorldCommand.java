package com.gmail.anthony17j.multiworld.command;

import com.gmail.anthony17j.multiworld.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.*;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getBaseWorldName;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;
import static net.minecraft.server.command.CommandManager.literal;

public final class createWorldCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("mw")
                .then(CommandManager.argument("dimension", StringArgumentType.word())
                        .suggests((context, builder) -> CommandSource.suggestMatching(
                                context.getSource().getWorldKeys().stream()
                                        .map(RegistryKey::getValue)
                                        .filter(id -> id.getNamespace().equals(NAMESPACE) || id.getNamespace().equals("minecraft"))
                                        .filter(id -> !id.getPath().endsWith("_nether") && !id.getPath().endsWith("_end"))
                                        .map(Identifier::getPath)
                                        .collect(Collectors.toList()),
                                builder
                        ))
                        .executes(ctx -> {
                            String worldName = StringArgumentType.getString(ctx, "dimension");
                            return command(ctx.getSource(), worldName);
                        }))
        );
    }

    static int command(ServerCommandSource source, String worldString) {
        worldString = getBaseWorldName(worldString);
        ServerWorld worldTarget = source.getServer().getOverworld();
        if (!worldString.equals("overworld")) {
          Identifier key = Identifier.of(NAMESPACE, worldString);
            RegistryKey<World> worldKey = RegistryKey.of(
                    RegistryKeys.WORLD,
                    key
            );
            worldTarget = source.getServer().getWorld(worldKey);
        }

        if (worldTarget == null) {
            source.sendMessage(Text.literal("World " + worldString + " not found!"));
            return 0;
        }

        String sourceWorldName = getSourceWorldName(source);
        String targetWorldName = worldTarget.getRegistryKey().getValue().getPath();

        if (sourceWorldName.equals(targetWorldName)) {
            source.sendMessage(Text.literal("You are already in this world!"));
            return 0;
        }

        ServerPlayerEntity player = source.getPlayer();

        Utils.saveInv(player, sourceWorldName);
        Utils.loadInv(player, targetWorldName);

        player.sendMessage(Text.literal("Teleported to " + worldTarget.getRegistryKey().getValue().toString() + " !"), false);
        // /execute in multiworld:test run tp ~ ~ ~

        return 0;
    }

    public static @NotNull String getSourceWorldName(ServerCommandSource source) {
        ServerWorld sourceWorld = source.getWorld();
        RegistryKey<World> sourceWorldKey = sourceWorld.getRegistryKey() == World.END || sourceWorld.getRegistryKey() == World.NETHER ? World.OVERWORLD : sourceWorld.getRegistryKey();
        String sourceWorldName = sourceWorldKey.getValue().getPath();
        return getBaseWorldName(sourceWorldName);
    }
}
