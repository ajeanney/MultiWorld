package com.gmail.anthony17j.multiworld.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;
import static com.gmail.anthony17j.multiworld.test.TestAssertions.*;

public class WorldManagementCommandTest {

    private static final String DELETE_WORLD_A = "delete_test_world_a";
    private static final String DELETE_WORLD_B = "delete_test_world_b";
    private static final String IMPORT_WORLD = "imported_test_world";
    private static final String IMPORT_LEGACY_WORLD = "imported_legacy_world";
    private static final String LEGACY_PLAYER_UUID = "abcdefgh-ijkl-mnop-qrst-uvwxyz123456";

    @GameTest(maxTicks = 600)
    public void deleteCommandRemovesWorldAndSubDimensions(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, DELETE_WORLD_A);

        runServerCommand(helper, player, "mw delete " + DELETE_WORLD_A);

        helper.runAfterDelay(10, () -> {
            assertNoDimension(helper, server, DELETE_WORLD_A);
            assertNoDimension(helper, server, DELETE_WORLD_A + "_nether");
            assertNoDimension(helper, server, DELETE_WORLD_A + "_end");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 800)
    public void deleteCommandTeleportsPlayersOutOfDeletedWorld(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, DELETE_WORLD_B);
        teleportToWorld(helper, player, DELETE_WORLD_B);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, DELETE_WORLD_B);

            runServerCommand(helper, player, "mw delete " + DELETE_WORLD_B);

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, "minecraft", "overworld");
                assertNoDimension(helper, server, DELETE_WORLD_B);
                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 800)
    public void importCommandCreatesWorldFromSourceFolder(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);

        String sourceFolder = resolveCurrentWorldImportSource(server).toString();
        runServerCommand(helper, player, "mw import \"" + sourceFolder + "\" " + IMPORT_WORLD);

        helper.runAfterDelay(20, () -> {
            ServerLevel imported = getDimension(server, IMPORT_WORLD);
            if (imported == null) {
                helper.fail("Imported world missing: " + IMPORT_WORLD);
                return;
            }

            teleportToWorld(helper, player, IMPORT_WORLD);

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, NAMESPACE, IMPORT_WORLD);
                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 1200)
    public void importLegacyFixtureMigratesPlayerData(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);

        Path fixturePath = resolveLegacyFixturePath(server);
        if (!Files.exists(fixturePath.resolve("level.dat"))) {
            helper.fail("Legacy fixture missing level.dat at " + fixturePath);
            return;
        }

        runServerCommand(helper, player, "mw import \"" + fixturePath + "\" " + IMPORT_LEGACY_WORLD);

        helper.runAfterDelay(30, () -> {
            ServerLevel imported = getDimension(server, IMPORT_LEGACY_WORLD);
            if (imported == null) {
                helper.fail("Legacy fixture import did not create world: " + IMPORT_LEGACY_WORLD);
                return;
            }

            Path importedPlayerJson = server.getWorldPath(LevelResource.ROOT)
                    .resolve(NAMESPACE)
                    .resolve(IMPORT_LEGACY_WORLD)
                    .resolve(LEGACY_PLAYER_UUID + ".json");

            if (!Files.exists(importedPlayerJson)) {
                helper.fail("Imported legacy player data file missing: " + importedPlayerJson);
                return;
            }

            try {
                String payload = Files.readString(importedPlayerJson);

                // Dimension tag should be rewritten during import.
                if (!payload.contains("multiworld:" + IMPORT_LEGACY_WORLD)) {
                    helper.fail("Imported legacy player Dimension/respawn data was not rewritten to world namespace");
                    return;
                }

                // Inventory tag should survive migration and import serialization.
                if (!payload.contains("Inventory")) {
                    helper.fail("Imported legacy player data is missing Inventory after migration");
                    return;
                }

                helper.succeed();
            } catch (Exception ex) {
                helper.fail("Failed to read imported legacy player data: " + ex.getMessage());
            }
        });
    }

    @GameTest(maxTicks = 1600)
    public void importedLegacyWorldRespawnStaysInExpectedSpawnZone(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);

        Path fixturePath = resolveLegacyFixturePath(server);
        if (!Files.exists(fixturePath.resolve("level.dat"))) {
            helper.fail("Legacy fixture missing level.dat at " + fixturePath);
            return;
        }

        runServerCommand(helper, player, "mw import \"" + fixturePath + "\" " + IMPORT_LEGACY_WORLD);

        helper.runAfterDelay(30, () -> {
            ServerLevel imported = getDimension(server, IMPORT_LEGACY_WORLD);
            if (imported == null) {
                helper.fail("Legacy fixture import did not create world: " + IMPORT_LEGACY_WORLD);
                return;
            }

            teleportToWorld(helper, player, IMPORT_LEGACY_WORLD);

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, NAMESPACE, IMPORT_LEGACY_WORLD);

                ServerPlayer respawned = killAndRespawn(player, server);
                assertInDimension(helper, respawned, NAMESPACE, IMPORT_LEGACY_WORLD);

                BlockPos importedSpawn = imported.getRespawnData().pos();
                assertNearPosition(helper, respawned, importedSpawn.getX(), importedSpawn.getY(), importedSpawn.getZ(), 40.0, "legacy import respawn zone");

                if (Math.abs(importedSpawn.getX()) <= 8 && Math.abs(importedSpawn.getZ()) <= 8) {
                    helper.fail("Imported legacy world spawn should not be near 0,0 but was " + importedSpawn);
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 1800)
    public void overworldRespawnUsesZoneNotFixedBlock(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        runServerCommand(helper, player, "gamerule keepInventory false");

        helper.runAfterDelay(10, () -> {
            Set<String> uniqueRespawnBlocks = new HashSet<>();
            ServerPlayer current = player;

            for (int i = 0; i < 6; i++) {
                current = killAndRespawn(current, server);
                assertInDimension(helper, current, "minecraft", "overworld");
                uniqueRespawnBlocks.add(blockKey(current.position()));
            }

            if (uniqueRespawnBlocks.size() < 2) {
                helper.fail("Overworld respawn should vary within spawn zone, but got fixed block: " + uniqueRespawnBlocks);
                return;
            }

            helper.succeed();
        });
    }

    private static void assertNoDimension(GameTestHelper helper, MinecraftServer server, String worldName) {
        if (getDimension(server, worldName) != null) {
            helper.fail("Expected dimension to be deleted: " + worldName);
        }
    }

    private static ServerLevel getDimension(MinecraftServer server, String worldName) {
        ResourceKey<Level> worldKey = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(NAMESPACE, worldName)
        );
        return server.getLevel(worldKey);
    }

    private static Path resolveCurrentWorldImportSource(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (Files.exists(root.resolve("level.dat"))) {
            return root;
        }

        Path parent = root.getParent();
        if (parent != null && Files.exists(parent.resolve("level.dat"))) {
            return parent;
        }

        Path nestedWorld = root.resolve("world");
        if (Files.exists(nestedWorld.resolve("level.dat"))) {
            return nestedWorld;
        }

        return resolveLegacyFixturePath(server);
    }

    private static Path resolveLegacyFixturePath(MinecraftServer server) {
        try {
            var resource = WorldManagementCommandTest.class.getClassLoader().getResource("fixtures/import_legacy_1_17_1/level.dat");
            if (resource != null) {
                Path fromResources = Path.of(resource.toURI()).getParent();
                if (Files.exists(fromResources.resolve("level.dat"))) {
                    return fromResources;
                }
            }
        } catch (Exception ignored) {
            // Fall back to filesystem probing below.
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path current = cwd;
        for (int i = 0; i < 8 && current != null; i++) {
            Path candidate = current.resolve("src").resolve("gametest").resolve("resources")
                    .resolve("fixtures").resolve("import_legacy_1_17_1");
            if (Files.exists(candidate.resolve("level.dat"))) {
                return candidate;
            }
            current = current.getParent();
        }

        return cwd.resolve("src").resolve("gametest").resolve("resources")
                .resolve("fixtures").resolve("import_legacy_1_17_1");
    }

    private static ServerPlayer killAndRespawn(ServerPlayer player, MinecraftServer server) {
        player.kill((ServerLevel) player.level());
        ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        ServerPlayer listed = server.getPlayerList().getPlayer(respawned.getUUID());
        return listed != null ? listed : respawned;
    }

    private static String blockKey(Vec3 pos) {
        return ((int) Math.floor(pos.x)) + "," + ((int) Math.floor(pos.y)) + "," + ((int) Math.floor(pos.z));
    }
}
