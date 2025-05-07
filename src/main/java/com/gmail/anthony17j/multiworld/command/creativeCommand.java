package com.gmail.anthony17j.multiworld.command;

import com.gmail.anthony17j.multiworld.Utils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getRegistryKey;
import static com.gmail.anthony17j.multiworld.MultiWorld.CREATIVE_WORLD_NAME;
import static com.gmail.anthony17j.multiworld.Utils.getMostRecentWorldSaved;
import static com.gmail.anthony17j.multiworld.command.createWorldCommand.getSourceWorldName;
import static net.minecraft.server.command.CommandManager.literal;

public class creativeCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(literal("creative").executes(creativeCommand::creative));
    }

    static int creative(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();

        String sourceWorld = getSourceWorldName(context.getSource());

        if (player.getEntityWorld().getRegistryKey() != getRegistryKey(CREATIVE_WORLD_NAME)) {
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
