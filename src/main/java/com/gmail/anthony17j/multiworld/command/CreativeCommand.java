package com.gmail.anthony17j.multiworld.command;

import com.gmail.anthony17j.multiworld.Utils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getRegistryKey;
import static com.gmail.anthony17j.multiworld.MultiWorld.CREATIVE_WORLD_NAME;
import static com.gmail.anthony17j.multiworld.Utils.getMostRecentWorldSaved;
import static com.gmail.anthony17j.multiworld.command.MultiWorldCommand.getSourceWorldName;
import static net.minecraft.server.command.CommandManager.literal;

public class CreativeCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("creative").executes(context -> {
            try {
                return creative(context);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    static int creative(CommandContext<ServerCommandSource> context) throws CommandSyntaxException, IOException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) {
            return 1;
        }

        String sourceWorld = getSourceWorldName(context.getSource());

        if (player.getWorld().getRegistryKey() != getRegistryKey(CREATIVE_WORLD_NAME)) {
            Utils.saveInv(player, sourceWorld);
            Utils.loadInv(player, CREATIVE_WORLD_NAME);
            //player.sendMessage(new LiteralText("Bienvenue dans le monde créatif"), false);
        } else {
            String mostRecentWorldSaved = getMostRecentWorldSaved(player);
            Utils.saveInv(player, CREATIVE_WORLD_NAME);
            Utils.loadInv(player, mostRecentWorldSaved);
        }
        return 0;
    }
}
