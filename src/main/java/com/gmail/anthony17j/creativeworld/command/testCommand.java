package com.gmail.anthony17j.creativeworld.command;

import com.gmail.anthony17j.creativeworld.Utils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import static net.minecraft.server.command.CommandManager.literal;

public class testCommand {
    public static LiteralCommandNode<ServerCommandSource> commandNode;
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated) {
        commandNode = dispatcher.register(literal("test").executes(testCommand::test));
    }

    static int test(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Utils.saveInv(player,"creative");
        return 0;
    }
}
