package com.gmail.anthony17j.multiworld.command;

import com.gmail.anthony17j.multiworld.Utils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.isCreativeWorld;
import static com.gmail.anthony17j.multiworld.MultiWorld.CREATIVE_WORLD_NAME;
import static com.gmail.anthony17j.multiworld.Utils.getMostRecentWorldSaved;
import static com.gmail.anthony17j.multiworld.command.MultiWorldCommand.getSourceWorldName;
import static net.minecraft.commands.Commands.literal;

public class CreativeCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(literal("creative").executes(context -> {
            try {
                return creative(context);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    static int creative(CommandContext<CommandSourceStack> context) throws CommandSyntaxException, IOException {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return 1;
        }

        String sourceWorld = getSourceWorldName(context.getSource());

        if (!isCreativeWorld(player.level().dimension())) {
            Utils.saveInv(player, sourceWorld);
            Utils.loadInv(player, CREATIVE_WORLD_NAME);
        } else {
            String mostRecentWorldSaved = getMostRecentWorldSaved(player, sourceWorld);
            Utils.saveInv(player, CREATIVE_WORLD_NAME);
            Utils.loadInv(player, mostRecentWorldSaved);
        }
        return 0;
    }
}
