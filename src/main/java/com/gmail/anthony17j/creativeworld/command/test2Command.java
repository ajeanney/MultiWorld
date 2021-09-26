package com.gmail.anthony17j.creativeworld.command;

import com.github.cliftonlabs.json_simple.JsonArray;
import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.gmail.anthony17j.creativeworld.Utils;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;

import java.io.*;
import java.util.Objects;

import static net.minecraft.server.command.CommandManager.literal;

public class test2Command {
    public static LiteralCommandNode<ServerCommandSource> commandNode;
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, boolean dedicated) {
        commandNode = dispatcher.register(literal("test2").executes(test2Command::test2));
    }

    static int test2(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayer();
        Utils.loadInv(player,"creative");
        //Utils.FiletoList("mods/creativeworld/" + player.getUuidAsString() + ".json");
        return 0;
    }
}
