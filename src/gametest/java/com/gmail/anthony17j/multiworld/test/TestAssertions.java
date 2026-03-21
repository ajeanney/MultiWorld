package com.gmail.anthony17j.multiworld.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.UUID;

public class TestAssertions {

    public static final String SHARED_TEST_WORLD = "test_world";

    public static ServerPlayer createMockServerPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-mock-player"), false
        );
        ServerPlayer player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public @NonNull GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    public static void assertInDimension(GameTestHelper helper, ServerPlayer player, String expectedNamespace, String expectedPath) {
        ResourceKey<Level> dim = player.level().dimension();
        String ns = dim.identifier().getNamespace();
        String path = dim.identifier().getPath();
        if (!expectedNamespace.equals(ns) || !expectedPath.equals(path)) {
            helper.fail("Expected dimension " + expectedNamespace + ":" + expectedPath + ", but player is in " + ns + ":" + path);
        }
    }

    public static void assertHasItem(GameTestHelper helper, ServerPlayer player, Item item, String context) {
        if (!containsItem(player, item)) {
            helper.fail("Player should have " + item + " " + context);
        }
    }

    public static void assertNoItem(GameTestHelper helper, ServerPlayer player, Item item, String context) {
        if (containsItem(player, item)) {
            helper.fail("Player should NOT have " + item + " " + context);
        }
    }

    public static void assertNearPosition(GameTestHelper helper, ServerPlayer player, double expectedX, double expectedY, double expectedZ, double tolerance, String context) {
        double dx = player.getX() - expectedX;
        double dy = player.getY() - expectedY;
        double dz = player.getZ() - expectedZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > tolerance) {
            helper.fail("Expected player near (" + expectedX + ", " + expectedY + ", " + expectedZ + ") " + context
                    + ", but was at (" + player.getX() + ", " + player.getY() + ", " + player.getZ() + ") (distance=" + distance + ")");
        }
    }

    public static void ensureAdvancementsDir(MinecraftServer server) {
        String worldFolder = server.getWorldPath(LevelResource.ROOT).toString();
        new File(worldFolder + "/advancements").mkdirs();
    }

    public static void runPlayerCommand(GameTestHelper helper, ServerPlayer player, String command) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null) {
            helper.fail("Server was null while running command: " + command);
            return;
        }
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    public static void runServerCommand(GameTestHelper helper, ServerPlayer player, String command) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null) {
            helper.fail("Server was null while running command: " + command);
            return;
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }

    public static void createWorld(GameTestHelper helper, ServerPlayer player, String worldName) {
        if (hasCustomWorld(player, worldName)) {
            return;
        }
        runServerCommand(helper, player, "mw create " + worldName);
    }

    public static void createWorld(GameTestHelper helper, ServerPlayer player, String worldName, long seed) {
        if (hasCustomWorld(player, worldName)) {
            return;
        }
        runServerCommand(helper, player, "mw create " + worldName + " normal normal " + seed);
    }

    public static void createWorld(GameTestHelper helper, ServerPlayer player, String worldName, String preset, String mode) {
        if (hasCustomWorld(player, worldName)) {
            return;
        }
        runServerCommand(helper, player, "mw create " + worldName + " " + preset + " " + mode);
    }

    public static void createWorld(GameTestHelper helper, ServerPlayer player, String worldName, String preset, String mode, long seed) {
        if (hasCustomWorld(player, worldName)) {
            return;
        }
        runServerCommand(helper, player, "mw create " + worldName + " " + preset + " " + mode + " " + seed);
    }

    public static ServerLevel getCustomWorld(ServerPlayer player, String worldName) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        if (server == null) {
            return null;
        }
        ResourceKey<Level> worldKey = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.fromNamespaceAndPath("multiworld", worldName)
        );
        return server.getLevel(worldKey);
    }

    private static boolean hasCustomWorld(ServerPlayer player, String worldName) {
        return getCustomWorld(player, worldName) != null;
    }

    public static void teleportToWorld(GameTestHelper helper, ServerPlayer player, String worldName) {
        runPlayerCommand(helper, player, "mw tp " + worldName);
    }

    private static boolean containsItem(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return true;
            }
        }
        return false;
    }
}
