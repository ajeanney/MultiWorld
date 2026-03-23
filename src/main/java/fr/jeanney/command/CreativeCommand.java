package fr.jeanney.command;

import fr.jeanney.Utils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;

import static fr.jeanney.CustomServerWorld.isCreativeWorld;
import static fr.jeanney.MultiWorld.getCreativeWorldName;
import static fr.jeanney.Utils.getMostRecentWorldSaved;
import static fr.jeanney.command.MultiWorldCommand.getSourceWorldName;
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
        String creativeWorld = getCreativeWorldName(context.getSource().getServer()).orElse(null);

        if (creativeWorld == null) {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal("No creative-mode world exists. Create one with /mw create <name> [preset] creative [seed]."));
            return 0;
        }

        if (!isCreativeWorld(player.level().dimension())) {
            Utils.saveInv(player, sourceWorld);
            Utils.loadInv(player, creativeWorld);
        } else {
            String mostRecentWorldSaved = getMostRecentWorldSaved(player, sourceWorld);
            Utils.saveInv(player, creativeWorld);
            Utils.loadInv(player, mostRecentWorldSaved);
        }
        return 0;
    }
}
