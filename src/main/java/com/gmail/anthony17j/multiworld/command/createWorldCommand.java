package com.gmail.anthony17j.multiworld.command;

import com.gmail.anthony17j.multiworld.*;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.registry.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import static com.gmail.anthony17j.multiworld.MultiWorld.namespace;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class createWorldCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("mvtp")
            .then(argument("world", greedyString())
            .executes(ctx -> command(ctx.getSource(), getString(ctx, "world")))));
    }

    static int command(ServerCommandSource source, String worldString) {
        if (worldString.endsWith("_nether")) {
            worldString = worldString.substring(0, worldString.length() - 7);
        } else if (worldString.endsWith("_end")) {
            worldString = worldString.substring(0, worldString.length() - 4);
        }

        ServerWorld worldTarget = source.getServer().getOverworld();
        if (!worldString.equals("overworld")) {
          Identifier key = Identifier.of(namespace, worldString);
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
        // if sourceWorldName ends with _nether or _end, remove it
        if (sourceWorldName.endsWith("_nether")) {
            sourceWorldName = sourceWorldName.substring(0, sourceWorldName.length() - 7);
        } else if (sourceWorldName.endsWith("_end")) {
            sourceWorldName = sourceWorldName.substring(0, sourceWorldName.length() - 4);
        }
        return sourceWorldName;
    }
}
