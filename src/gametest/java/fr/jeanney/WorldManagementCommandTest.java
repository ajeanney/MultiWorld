package fr.jeanney;

import fr.jeanney.command.MultiWorldCommand;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import static fr.jeanney.MultiWorld.NAMESPACE;
import static fr.jeanney.TestAssertions.*;

public class WorldManagementCommandTest {

    private static final String DELETE_WORLD_A = "delete_test_world_a";
    private static final String DELETE_WORLD_B = "delete_test_world_b";
    private static final String IMPORT_WORLD = "imported_test_world";
    private static final String IMPORT_LEGACY_WORLD = "imported_legacy_world";
    private static final String IMPORT_26_1_WORLD = "imported_26_1_world";
    private static final String IMPORT_26_1_SAVED_DATA_SEED_WORLD = "imported_26_1_saved_data_seed_world";
    private static final String IMPORT_26_1_PLAYERDATA_WORLD = "imported_26_1_playerdata_world";
    private static final String SCOREBOARD_ISOLATION_WORLD = "scoreboard_isolation_world";
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

    @GameTest(maxTicks = 1200)
    public void import26FixturePreservesSeedTimeAndWeather(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        final int assertionDelayTicks = 30;
        final int maxTickDrift = assertionDelayTicks + 5;

        ensureAdvancementsDir(server);

        Path fixturePath = resolve26FixturePath(server);
        if (!Files.exists(fixturePath.resolve("level.dat"))) {
            helper.fail("26.1 fixture missing level.dat at " + fixturePath);
            return;
        }

        FixtureExpectedState expected;
        try {
            expected = loadFixtureExpectedState(server, fixturePath);
        } catch (Exception ex) {
            helper.fail("Failed to load expected fixture state: " + ex.getMessage());
            return;
        }

        runServerCommand(helper, player, "mw import \"" + fixturePath + "\" " + IMPORT_26_1_WORLD);

        helper.runAfterDelay(assertionDelayTicks, () -> {
            ServerLevel imported = getDimension(server, IMPORT_26_1_WORLD);
            if (imported == null) {
                helper.fail("26.1 fixture import did not create world: " + IMPORT_26_1_WORLD);
                return;
            }

            BlockPos importedSpawn = imported.getRespawnData().pos();
            if (expected.spawnPos().isPresent()) {
                BlockPos explicitExpectedSpawn = expected.spawnPos().get();
                if (!importedSpawn.equals(explicitExpectedSpawn)) {
                    helper.fail("Imported world spawn mismatch: expected " + explicitExpectedSpawn + " but got "
                            + importedSpawn);
                    return;
                }
            } else {
                helper.fail("Fixture expected state did not provide an explicit spawn position");
                return;
            }

            if (imported.getSeed() != expected.seed()) {
                helper.fail(
                        "Imported world seed mismatch: expected " + expected.seed() + " but got " + imported.getSeed());
                return;
            }

            long importedGameTime = imported.getGameTime();
            if (importedGameTime < expected.gameTime() || importedGameTime > expected.gameTime() + maxTickDrift) {
                helper.fail("Imported world gameTime mismatch: expected around " + expected.gameTime()
                        + " (+0.." + maxTickDrift + " ticks), but got " + importedGameTime);
                return;
            }

            if (expected.overworldClockTicks().isPresent()) {
                Holder<net.minecraft.world.clock.WorldClock> overworldClock = imported.registryAccess()
                        .lookupOrThrow(Registries.WORLD_CLOCK)
                        .getOrThrow(ResourceKey.create(Registries.WORLD_CLOCK,
                                Identifier.fromNamespaceAndPath("minecraft", "overworld")));
                long importedOverworldClockTicks = MultiWorld.getClockManagerForLevel(imported)
                        .getTotalTicks(overworldClock);
                long expectedOverworldClockTicks = expected.overworldClockTicks().getAsLong();
                if (importedOverworldClockTicks < expectedOverworldClockTicks
                        || importedOverworldClockTicks > expectedOverworldClockTicks + maxTickDrift) {
                    helper.fail("Imported overworld clock mismatch: expected around " + expectedOverworldClockTicks
                            + " (+0.." + maxTickDrift + " ticks), but got " + importedOverworldClockTicks);
                    return;
                }
            }

            WeatherData weather = MultiWorld.getCustomWorldWeatherData(imported);
            if (weather == null) {
                helper.fail("Imported world weather state not available for custom world");
                return;
            }

            int clearWeatherDiff = Math.abs(weather.getClearWeatherTime() - expected.clearWeatherTime());
            if (clearWeatherDiff > maxTickDrift) {
                helper.fail("clearWeatherTime mismatch: expected around " + expected.clearWeatherTime()
                        + " (+/-" + maxTickDrift + "), but got " + weather.getClearWeatherTime());
                return;
            }
            int rainDiff = Math.abs(weather.getRainTime() - expected.rainTime());
            if (rainDiff > maxTickDrift) {
                helper.fail("rainTime mismatch: expected around " + expected.rainTime()
                        + " (+/-" + maxTickDrift + "), but got " + weather.getRainTime());
                return;
            }
            int thunderDiff = Math.abs(weather.getThunderTime() - expected.thunderTime());
            if (thunderDiff > maxTickDrift) {
                helper.fail("thunderTime mismatch: expected around " + expected.thunderTime()
                        + " (+/-" + maxTickDrift + "), but got " + weather.getThunderTime());
                return;
            }
            if (weather.isRaining() != expected.raining()) {
                helper.fail("raining mismatch: expected " + expected.raining() + " but got " + weather.isRaining());
                return;
            }
            if (weather.isThundering() != expected.thundering()) {
                helper.fail("thundering mismatch: expected " + expected.thundering() + " but got "
                        + weather.isThundering());
                return;
            }

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 1400)
    public void import26FixtureKeepsWeatherPacketsScopedPerWorld(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);

        Path fixturePath = resolve26FixturePath(server);
        if (!Files.exists(fixturePath.resolve("level.dat"))) {
            helper.fail("26.1 fixture missing level.dat at " + fixturePath);
            return;
        }

        FixtureExpectedState expected;
        try {
            expected = loadFixtureExpectedState(server, fixturePath);
        } catch (Exception ex) {
            helper.fail("Failed to load expected fixture weather state: " + ex.getMessage());
            return;
        }

        var overworldWeather = server.overworld().getWeatherData();
        overworldWeather.setClearWeatherTime(6000);
        overworldWeather.setRaining(false);
        overworldWeather.setThundering(false);
        overworldWeather.setRainTime(0);
        overworldWeather.setThunderTime(0);

        runServerCommand(helper, player, "mw import \"" + fixturePath + "\" " + IMPORT_26_1_WORLD);

        helper.runAfterDelay(40, () -> {
            ServerLevel imported = getDimension(server, IMPORT_26_1_WORLD);
            if (imported == null) {
                helper.fail("26.1 fixture import did not create world: " + IMPORT_26_1_WORLD);
                return;
            }

            var currentOverworldWeather = server.overworld().getWeatherData();
            if (currentOverworldWeather.isRaining() || currentOverworldWeather.isThundering()) {
                helper.fail("Overworld weather state changed after import (expected clear)");
                return;
            }

            ClientboundGameEventPacket overworldTogglePacket = MultiWorld.createWeatherSyncPacketForLevel(
                    server.overworld(), ClientboundGameEventPacket.START_RAINING);
            if (overworldTogglePacket.getEvent() != ClientboundGameEventPacket.STOP_RAINING) {
                helper.fail("Overworld weather sync packet should be STOP_RAINING when overworld is clear");
                return;
            }

            ClientboundGameEventPacket importedTogglePacket = MultiWorld.createWeatherSyncPacketForLevel(
                    imported, ClientboundGameEventPacket.START_RAINING);
            ClientboundGameEventPacket.Type expectedImportedToggle = expected.raining()
                    ? ClientboundGameEventPacket.START_RAINING
                    : ClientboundGameEventPacket.STOP_RAINING;
            if (importedTogglePacket.getEvent() != expectedImportedToggle) {
                helper.fail("Imported weather sync packet mismatch: expected " + expectedImportedToggle
                        + " but got " + importedTogglePacket.getEvent());
                return;
            }

            ClientboundGameEventPacket importedRainLevelPacket = MultiWorld.createWeatherSyncPacketForLevel(
                    imported, ClientboundGameEventPacket.RAIN_LEVEL_CHANGE);
            float expectedImportedRain = imported.getRainLevel(1.0F);
            if (Math.abs(importedRainLevelPacket.getParam() - expectedImportedRain) > 0.0001F) {
                helper.fail("Imported rain-level packet param mismatch: expected " + expectedImportedRain
                        + " but got " + importedRainLevelPacket.getParam());
                return;
            }

            ClientboundGameEventPacket importedThunderLevelPacket = MultiWorld.createWeatherSyncPacketForLevel(
                    imported, ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE);
            float expectedImportedThunder = imported.getThunderLevel(1.0F);
            if (Math.abs(importedThunderLevelPacket.getParam() - expectedImportedThunder) > 0.0001F) {
                helper.fail("Imported thunder-level packet param mismatch: expected " + expectedImportedThunder
                        + " but got " + importedThunderLevelPacket.getParam());
                return;
            }

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 1400)
    public void import26FixtureUsesSavedDataSeedWhenLevelDatSeedIsWrong(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);

        Path fixturePath = resolve26FixturePath(server);
        Path fixtureWorldGenDat = fixturePath.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat");
        if (!Files.exists(fixturePath.resolve("level.dat"))) {
            helper.fail("26.1 fixture missing level.dat at " + fixturePath);
            return;
        }
        if (!Files.exists(fixtureWorldGenDat)) {
            helper.fail("26.1 fixture missing world_gen_settings.dat at " + fixtureWorldGenDat);
            return;
        }

        OptionalLong expectedSavedDataSeed = readSeedFromWorldGenSettingsDat(fixturePath);
        if (expectedSavedDataSeed.isEmpty()) {
            helper.fail("Could not resolve expected seed from world_gen_settings.dat in fixture " + fixturePath);
            return;
        }

        long expectedSeed = expectedSavedDataSeed.getAsLong();
        long wrongLevelDatSeed = expectedSeed == 1L ? 2L : 1L;

        Path tempSource;
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            tempSource = worldRoot.resolve("gametest_tmp_import_26_seed_prefer_saved_data");
            deleteRecursivelyIfExists(tempSource);
            copyDirectoryRecursively(fixturePath, tempSource);
            overwriteAllSeedLikeLongsInLevelDat(tempSource.resolve("level.dat"), wrongLevelDatSeed);
        } catch (Exception ex) {
            helper.fail("Failed to prepare mutated import fixture: " + ex.getMessage());
            return;
        }

        runServerCommand(helper, player, "mw import \"" + tempSource + "\" " + IMPORT_26_1_SAVED_DATA_SEED_WORLD);

        helper.runAfterDelay(30, () -> {
            ServerLevel imported = getDimension(server, IMPORT_26_1_SAVED_DATA_SEED_WORLD);
            if (imported == null) {
                helper.fail("Mutated 26.1 import did not create world: " + IMPORT_26_1_SAVED_DATA_SEED_WORLD);
                return;
            }

            if (imported.getSeed() != expectedSeed) {
                helper.fail("Imported seed should come from world_gen_settings.dat: expected "
                        + expectedSeed + " but got " + imported.getSeed());
                return;
            }

            if (imported.getSeed() == wrongLevelDatSeed) {
                helper.fail("Imported seed incorrectly came from tampered level.dat seed " + wrongLevelDatSeed);
                return;
            }

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 1600)
    public void import26FixtureLoadsMatchingPlayerDataOnTeleport(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);

        Path fixturePath = resolve26FixturePath(server);
        if (!Files.exists(fixturePath.resolve("level.dat"))) {
            helper.fail("26.1 fixture missing level.dat at " + fixturePath);
            return;
        }

        Path tempSource;
        String playerUuid = player.getStringUUID();
        try {
            Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            tempSource = worldRoot.resolve("gametest_tmp_import_26_player_profile");
            deleteRecursivelyIfExists(tempSource);
            copyDirectoryRecursively(fixturePath, tempSource);
            prepareFixturePlayerProfileForUuid(tempSource, LEGACY_PLAYER_UUID, playerUuid);
        } catch (Exception ex) {
            helper.fail("Failed to prepare import fixture with matching player UUID: " + ex.getMessage());
            return;
        }

        runServerCommand(helper, player, "mw import \"" + tempSource + "\" " + IMPORT_26_1_PLAYERDATA_WORLD);

        helper.runAfterDelay(30, () -> {
            Path importedPlayerJson = server.getWorldPath(LevelResource.ROOT)
                    .resolve(NAMESPACE)
                    .resolve(IMPORT_26_1_PLAYERDATA_WORLD)
                    .resolve(playerUuid + ".json");

            if (!Files.exists(importedPlayerJson)) {
                helper.fail("Imported player profile missing for UUID " + playerUuid + " at " + importedPlayerJson);
                return;
            }

            teleportToWorld(helper, player, IMPORT_26_1_PLAYERDATA_WORLD);

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, NAMESPACE, IMPORT_26_1_PLAYERDATA_WORLD);
                assertHasItem(helper, player, Items.OAK_LOG, "after importing matching player data");

                AdvancementHolder recipeAdvancement = server.getAdvancements()
                        .get(Identifier.parse("minecraft:recipes/decorations/crafting_table"));
                if (recipeAdvancement == null) {
                    helper.fail("Advancement 'minecraft:recipes/decorations/crafting_table' not found");
                    return;
                }

                AdvancementProgress progress = player.getAdvancements().getOrStartProgress(recipeAdvancement);
                if (!progress.isDone()) {
                    helper.fail("Expected imported advancement to be completed after teleport");
                    return;
                }

                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 1400)
    public void import26FixtureRestoresEntitiesGameRulesAndScoreboard(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);

        Path fixturePath = resolve26FixturePath(server);
        if (!Files.exists(fixturePath.resolve("level.dat"))) {
            helper.fail("26.1 fixture missing level.dat at " + fixturePath);
            return;
        }

        // Ensure import is the source of this objective.
        var existingTestObjective = server.getScoreboard().getObjective("test");
        if (existingTestObjective != null) {
            server.getScoreboard().removeObjective(existingTestObjective);
        }

        runServerCommand(helper, player, "mw import \"" + fixturePath + "\" " + IMPORT_26_1_WORLD);

        helper.runAfterDelay(40, () -> {
            ServerLevel imported = getDimension(server, IMPORT_26_1_WORLD);
            if (imported == null) {
                helper.fail("26.1 fixture import did not create world: " + IMPORT_26_1_WORLD);
                return;
            }

            if (!imported.getGameRules().get(GameRules.KEEP_INVENTORY)) {
                helper.fail("Imported keepInventory gamerule should be true but was false");
                return;
            }

            Path importedScoreboard = server.getWorldPath(LevelResource.ROOT)
                    .resolve("dimensions")
                    .resolve(NAMESPACE)
                    .resolve(IMPORT_26_1_WORLD)
                    .resolve("data")
                    .resolve("minecraft")
                    .resolve("scoreboard.dat");

            if (!hasObjectiveInScoreboardDat(importedScoreboard, "test")) {
                helper.fail("Imported scoreboard.dat should contain objective 'test'");
                return;
            }

            Path sourceOverworldEntities = fixturePath
                    .resolve("dimensions")
                    .resolve("minecraft")
                    .resolve("overworld")
                    .resolve("entities");

            long sourceEntityRegionCount;
            try (var stream = Files.list(sourceOverworldEntities)) {
                sourceEntityRegionCount = stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".mca"))
                        .count();
            } catch (Exception ex) {
                helper.fail("Failed to inspect source entities folder: " + ex.getMessage());
                return;
            }

            if (sourceEntityRegionCount <= 0) {
                helper.fail("Fixture should contain at least one overworld entities .mca file");
                return;
            }

            Path importedEntities = server.getWorldPath(LevelResource.ROOT)
                    .resolve("dimensions")
                    .resolve(NAMESPACE)
                    .resolve(IMPORT_26_1_WORLD)
                    .resolve("entities");

            long importedEntityRegionCount;
            try (var stream = Files.list(importedEntities)) {
                importedEntityRegionCount = stream
                        .filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.endsWith(".mca"))
                        .count();
            } catch (Exception ex) {
                helper.fail("Failed to inspect imported entities folder: " + ex.getMessage());
                return;
            }

            if (importedEntityRegionCount <= 0) {
                helper.fail("No entity region files were imported into the target world");
                return;
            }

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 1600)
    public void import26FixtureDoesNotLeakScoreboardObjectivesToOtherWorlds(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);

        Path fixturePath = resolve26FixturePath(server);
        if (!Files.exists(fixturePath.resolve("level.dat"))) {
            helper.fail("26.1 fixture missing level.dat at " + fixturePath);
            return;
        }

        var existingTestObjective = server.getScoreboard().getObjective("test");
        if (existingTestObjective != null) {
            server.getScoreboard().removeObjective(existingTestObjective);
        }

        runServerCommand(helper, player, "mw import \"" + fixturePath + "\" " + IMPORT_26_1_WORLD);

        helper.runAfterDelay(40, () -> {
            ServerLevel imported = getDimension(server, IMPORT_26_1_WORLD);
            if (imported == null) {
                helper.fail("26.1 fixture import did not create world: " + IMPORT_26_1_WORLD);
                return;
            }

            Path importedScoreboard = server.getWorldPath(LevelResource.ROOT)
                    .resolve("dimensions")
                    .resolve(NAMESPACE)
                    .resolve(IMPORT_26_1_WORLD)
                    .resolve("data")
                    .resolve("minecraft")
                    .resolve("scoreboard.dat");
            if (!hasObjectiveInScoreboardDat(importedScoreboard, "test")) {
                helper.fail("Imported scoreboard.dat should contain objective 'test'");
                return;
            }

            if (server.getScoreboard().getObjective("test") != null) {
                helper.fail("Imported objective 'test' leaked into global scoreboard state");
                return;
            }

            createWorld(helper, player, SCOREBOARD_ISOLATION_WORLD);
            teleportToWorld(helper, player, SCOREBOARD_ISOLATION_WORLD);

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, NAMESPACE, SCOREBOARD_ISOLATION_WORLD);
                if (server.getScoreboard().getObjective("test") != null) {
                    helper.fail("Objective 'test' should not appear after moving to another multiworld");
                    return;
                }

                teleportToWorld(helper, player, "overworld");
                helper.runAfterDelay(10, () -> {
                    assertInDimension(helper, player, "minecraft", "overworld");
                    if (server.getScoreboard().getObjective("test") != null) {
                        helper.fail("Objective 'test' should not appear in vanilla overworld scoreboard");
                        return;
                    }
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks = 1200)
    public void debugCommandIncludesRuntimeAndSavedDataInfo(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);

        Path fixturePath = resolve26FixturePath(server);
        if (!Files.exists(fixturePath.resolve("level.dat"))) {
            helper.fail("26.1 fixture missing level.dat at " + fixturePath);
            return;
        }

        runServerCommand(helper, player, "mw import \"" + fixturePath + "\" " + IMPORT_26_1_WORLD);

        helper.runAfterDelay(30, () -> {
            ServerLevel imported = getDimension(server, IMPORT_26_1_WORLD);
            if (imported == null) {
                helper.fail("Imported world missing: " + IMPORT_26_1_WORLD);
                return;
            }

            server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack(),
                    "mw debug " + IMPORT_26_1_WORLD);

            java.util.List<String> debugLines = MultiWorldCommand.collectWorldDebugInfo(server, imported);

            assertLineContains(helper, debugLines, "spawn:");
            assertLineContains(helper, debugLines, "time: game=");
            assertLineContains(helper, debugLines, "weather:");
            assertLineContains(helper, debugLines, "gamerules: total=");
            assertLineContains(helper, debugLines, "saved-data files(");
            assertLineContains(helper, debugLines, "world_gen_settings.dat");
            assertLineContains(helper, debugLines, "weather.dat");
            assertLineContains(helper, debugLines, "world_clocks.dat");

            helper.succeed();
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
                assertNearPosition(helper, respawned, importedSpawn.getX(), importedSpawn.getY(), importedSpawn.getZ(),
                        40.0, "legacy import respawn zone");

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
                helper.fail(
                        "Overworld respawn should vary within spawn zone, but got fixed block: " + uniqueRespawnBlocks);
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
                Identifier.fromNamespaceAndPath(NAMESPACE, worldName));
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
            var resource = WorldManagementCommandTest.class.getClassLoader()
                    .getResource("fixtures/import_legacy_1_17_1/level.dat");
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

    private static Path resolve26FixturePath(MinecraftServer server) {
        try {
            var resource = WorldManagementCommandTest.class.getClassLoader()
                    .getResource("fixtures/import_26_1/level.dat");
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
                    .resolve("fixtures").resolve("import_26_1");
            if (Files.exists(candidate.resolve("level.dat"))) {
                return candidate;
            }
            current = current.getParent();
        }

        return cwd.resolve("src").resolve("gametest").resolve("resources")
                .resolve("fixtures").resolve("import_26_1");
    }

    private static FixtureExpectedState loadFixtureExpectedState(MinecraftServer server, Path fixturePath)
            throws Exception {
        CompoundTag levelData = NbtIo.readCompressed(fixturePath.resolve("level.dat"), NbtAccounter.unlimitedHeap());
        CompoundTag dataTag = levelData.getCompound("Data").orElse(levelData);

        long gameTime = dataTag.getLong("Time")
                .or(() -> dataTag.getLong("DayTime"))
                .orElse(0L);

        Optional<BlockPos> spawnPos = readExplicitSpawnPos(dataTag);

        OptionalLong overworldClockTicks = readOverworldClockTicksFromDat(fixturePath);

        WeatherData expectedWeather = readWeatherFromSavedData(server, fixturePath).orElse(
                new WeatherData(
                        dataTag.getInt("clearWeatherTime").orElse(0),
                        dataTag.getInt("rainTime").orElse(0),
                        dataTag.getInt("thunderTime").orElse(0),
                        dataTag.getBooleanOr("raining", false),
                        dataTag.getBooleanOr("thundering", false)));

        long seed = extractFixtureSeed(server, fixturePath, dataTag);

        return new FixtureExpectedState(
                seed,
                gameTime,
                expectedWeather.getClearWeatherTime(),
                expectedWeather.getRainTime(),
                expectedWeather.getThunderTime(),
                expectedWeather.isRaining(),
                expectedWeather.isThundering(),
                spawnPos,
                overworldClockTicks);
    }

    private static long extractFixtureSeed(MinecraftServer server, Path fixturePath, CompoundTag dataTag) {
        OptionalLong seedFromDatFile = readSeedFromWorldGenSettingsDat(fixturePath);
        if (seedFromDatFile.isPresent()) {
            return seedFromDatFile.getAsLong();
        }

        OptionalLong seedFromSavedData = readSeedFromSavedData(server, fixturePath);
        if (seedFromSavedData.isPresent()) {
            return seedFromSavedData.getAsLong();
        }

        try {
            RegistryOps<net.minecraft.nbt.Tag> ops = RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE,
                    server.registryAccess());
            Optional<WorldGenSettings> fromCodec = dataTag.read("WorldGenSettings", WorldGenSettings.CODEC, ops);
            if (fromCodec.isPresent()) {
                return fromCodec.get().options().seed();
            }
        } catch (Exception ignored) {
        }

        return dataTag.getCompound("WorldGenSettings")
                .flatMap(tag -> tag.getLong("seed"))
                .or(() -> dataTag.getLong("RandomSeed"))
                .or(() -> dataTag.getLong("Seed"))
                .orElse(server.overworld().getSeed());
    }

    private static Optional<BlockPos> readExplicitSpawnPos(CompoundTag dataTag) {
        Optional<CompoundTag> spawnCompoundOpt = dataTag.getCompound("spawn");
        if (spawnCompoundOpt.isPresent()) {
            CompoundTag spawnCompound = spawnCompoundOpt.get();

            Optional<int[]> spawnPosArray = spawnCompound.getIntArray("pos");
            if (spawnPosArray.isPresent() && spawnPosArray.get().length >= 3) {
                int[] pos = spawnPosArray.get();
                return Optional.of(new BlockPos(pos[0], pos[1], pos[2]));
            }

            Optional<Integer> x = spawnCompound.getInt("x");
            Optional<Integer> y = spawnCompound.getInt("y");
            Optional<Integer> z = spawnCompound.getInt("z");
            if (x.isPresent() && y.isPresent() && z.isPresent()) {
                return Optional.of(new BlockPos(x.get(), y.get(), z.get()));
            }
        }

        if (!dataTag.contains("SpawnX") || !dataTag.contains("SpawnY") || !dataTag.contains("SpawnZ")) {
            return Optional.empty();
        }

        return Optional.of(new BlockPos(
                dataTag.getInt("SpawnX").orElse(0),
                dataTag.getInt("SpawnY").orElse(63),
                dataTag.getInt("SpawnZ").orElse(0)));
    }

    private static OptionalLong readSeedFromSavedData(MinecraftServer server, Path fixturePath) {
        Path parent = fixturePath.getParent();
        if (parent == null) {
            return OptionalLong.empty();
        }

        try {
            LevelStorageSource storageSource = LevelStorageSource.createDefault(parent);
            try (LevelStorageSource.LevelStorageAccess storageAccess = storageSource
                    .createAccess(fixturePath.getFileName().toString())) {
                var result = LevelStorageSource.readExistingSavedData(
                        storageAccess,
                        server.registryAccess(),
                        WorldGenSettings.TYPE);
                Optional<WorldGenSettings> worldGenSettings = result.result();
                if (worldGenSettings.isPresent()) {
                    return OptionalLong.of(worldGenSettings.get().options().seed());
                }
            }
        } catch (Exception ignored) {
        }

        return OptionalLong.empty();
    }

    private static OptionalLong readSeedFromWorldGenSettingsDat(Path fixturePath) {
        Path worldGenDat = fixturePath.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat");
        if (!Files.exists(worldGenDat)) {
            return OptionalLong.empty();
        }

        try {
            CompoundTag root = NbtIo.readCompressed(worldGenDat, NbtAccounter.unlimitedHeap());
            CompoundTag dataTag = root.getCompound("data").orElse(root);

            Optional<Long> seed = dataTag.getLong("seed")
                    .or(() -> dataTag.getLong("RandomSeed"))
                    .or(() -> dataTag.getLong("Seed"));
            if (seed.isPresent()) {
                return OptionalLong.of(seed.get());
            }

            Optional<Long> recursive = findSeedRecursively(dataTag, 0);
            return recursive.isPresent() ? OptionalLong.of(recursive.get()) : OptionalLong.empty();
        } catch (Exception ignored) {
            return OptionalLong.empty();
        }
    }

    private static Optional<WeatherData> readWeatherFromSavedData(MinecraftServer server, Path fixturePath) {
        Optional<WeatherData> direct = readWeatherFromDatFile(fixturePath);
        if (direct.isPresent()) {
            return direct;
        }

        Path parent = fixturePath.getParent();
        if (parent == null) {
            return Optional.empty();
        }

        try {
            LevelStorageSource storageSource = LevelStorageSource.createDefault(parent);
            try (LevelStorageSource.LevelStorageAccess storageAccess = storageSource
                    .createAccess(fixturePath.getFileName().toString())) {
                var result = LevelStorageSource.readExistingSavedData(
                        storageAccess,
                        server.registryAccess(),
                        WeatherData.TYPE);
                return result.result();
            }
        } catch (Exception ignored) {
        }

        return Optional.empty();
    }

    private static Optional<WeatherData> readWeatherFromDatFile(Path fixturePath) {
        Path weatherDat = fixturePath.resolve("data").resolve("minecraft").resolve("weather.dat");
        if (!Files.exists(weatherDat)) {
            return Optional.empty();
        }

        try {
            CompoundTag root = NbtIo.readCompressed(weatherDat, NbtAccounter.unlimitedHeap());
            CompoundTag dataTag = root.getCompound("data").orElse(root);

            return Optional.of(new WeatherData(
                    dataTag.getInt("clear_weather_time").or(() -> dataTag.getInt("clearWeatherTime")).orElse(0),
                    dataTag.getInt("rain_time").or(() -> dataTag.getInt("rainTime")).orElse(0),
                    dataTag.getInt("thunder_time").or(() -> dataTag.getInt("thunderTime")).orElse(0),
                    dataTag.getBooleanOr("raining", false),
                    dataTag.getBooleanOr("thundering", false)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static OptionalLong readOverworldClockTicksFromDat(Path fixturePath) {
        Path clocksDat = fixturePath.resolve("data").resolve("minecraft").resolve("world_clocks.dat");
        if (!Files.exists(clocksDat)) {
            return OptionalLong.empty();
        }

        try {
            CompoundTag root = NbtIo.readCompressed(clocksDat, NbtAccounter.unlimitedHeap());
            CompoundTag dataTag = root.getCompound("data").orElse(root);

            OptionalLong direct = dataTag.getCompound("minecraft:overworld")
                    .flatMap(clockTag -> clockTag.getLong("total_ticks"))
                    .map(OptionalLong::of)
                    .orElse(OptionalLong.empty());
            if (direct.isPresent()) {
                return direct;
            }

            return findClockTicksRecursively(dataTag, "minecraft:overworld", 0);
        } catch (Exception ignored) {
            return OptionalLong.empty();
        }
    }

    private static OptionalLong findClockTicksRecursively(CompoundTag tag, String keyName, int depth) {
        if (depth > 16) {
            return OptionalLong.empty();
        }

        for (var entry : tag.entrySet()) {
            String key = entry.getKey();
            var value = entry.getValue();
            if (!(value instanceof CompoundTag nested)) {
                continue;
            }

            if (keyName.equals(key)) {
                Optional<Long> ticks = nested.getLong("total_ticks");
                if (ticks.isPresent()) {
                    return OptionalLong.of(ticks.get());
                }
            }

            OptionalLong deeper = findClockTicksRecursively(nested, keyName, depth + 1);
            if (deeper.isPresent()) {
                return deeper;
            }
        }

        return OptionalLong.empty();
    }

    private static void overwriteAllSeedLikeLongsInLevelDat(Path levelDatPath, long seedValue) throws Exception {
        CompoundTag root = NbtIo.readCompressed(levelDatPath, NbtAccounter.unlimitedHeap());
        overwriteAllSeedLikeLongs(root, seedValue, 0);
        NbtIo.writeCompressed(root, levelDatPath);
    }

    private static void overwriteAllSeedLikeLongs(CompoundTag tag, long seedValue, int depth) {
        if (depth > 24) {
            return;
        }

        for (var entry : tag.entrySet()) {
            String key = entry.getKey();
            var value = entry.getValue();

            if (value instanceof net.minecraft.nbt.LongTag
                    && ("seed".equalsIgnoreCase(key) || "randomseed".equalsIgnoreCase(key))) {
                tag.putLong(key, seedValue);
                continue;
            }

            if (value instanceof CompoundTag nested) {
                overwriteAllSeedLikeLongs(nested, seedValue, depth + 1);
                tag.put(key, nested);
            }
        }
    }

    private static Optional<Long> findSeedRecursively(CompoundTag tag, int depth) {
        if (depth > 16) {
            return Optional.empty();
        }

        for (var entry : tag.entrySet()) {
            String key = entry.getKey();
            var value = entry.getValue();

            if (value instanceof net.minecraft.nbt.LongTag longTag
                    && ("seed".equalsIgnoreCase(key) || "randomseed".equalsIgnoreCase(key))) {
                return Optional.of(longTag.value());
            }

            if (value instanceof CompoundTag nested) {
                Optional<Long> nestedSeed = findSeedRecursively(nested, depth + 1);
                if (nestedSeed.isPresent()) {
                    return nestedSeed;
                }
            }
        }

        return Optional.empty();
    }

    private static void prepareFixturePlayerProfileForUuid(Path fixtureRoot, String sourceUuid, String targetUuid)
            throws Exception {
        copyIfExists(
                fixtureRoot.resolve("playerdata").resolve(sourceUuid + ".dat"),
                fixtureRoot.resolve("playerdata").resolve(targetUuid + ".dat"));
        copyIfExists(
                fixtureRoot.resolve("stats").resolve(sourceUuid + ".json"),
                fixtureRoot.resolve("stats").resolve(targetUuid + ".json"));
        copyIfExists(
                fixtureRoot.resolve("advancements").resolve(sourceUuid + ".json"),
                fixtureRoot.resolve("advancements").resolve(targetUuid + ".json"));

        copyIfExists(
                fixtureRoot.resolve("players").resolve("data").resolve(sourceUuid + ".dat"),
                fixtureRoot.resolve("players").resolve("data").resolve(targetUuid + ".dat"));
        copyIfExists(
                fixtureRoot.resolve("players").resolve("stats").resolve(sourceUuid + ".json"),
                fixtureRoot.resolve("players").resolve("stats").resolve(targetUuid + ".json"));
        copyIfExists(
                fixtureRoot.resolve("players").resolve("advancements").resolve(sourceUuid + ".json"),
                fixtureRoot.resolve("players").resolve("advancements").resolve(targetUuid + ".json"));
    }

    private static void copyIfExists(Path source, Path destination) throws Exception {
        if (!Files.exists(source)) {
            return;
        }

        Files.createDirectories(destination.getParent());
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static void copyDirectoryRecursively(Path source, Path target) throws Exception {
        try (var stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path destination = target.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING,
                                StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (RuntimeException wrapped) {
            if (wrapped.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw wrapped;
        }
    }

    private static void deleteRecursivelyIfExists(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }

        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        } catch (RuntimeException wrapped) {
            if (wrapped.getCause() instanceof Exception ex) {
                throw ex;
            }
            throw wrapped;
        }
    }

    private record FixtureExpectedState(
            long seed,
            long gameTime,
            int clearWeatherTime,
            int rainTime,
            int thunderTime,
            boolean raining,
            boolean thundering,
            Optional<BlockPos> spawnPos,
            OptionalLong overworldClockTicks) {
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

    private static void assertLineContains(GameTestHelper helper, java.util.List<String> lines, String needle) {
        boolean found = lines.stream().anyMatch(line -> line.contains(needle));
        if (!found) {
            helper.fail("Expected debug output to contain '" + needle + "' but got: " + lines);
        }
    }

    private static boolean hasObjectiveInScoreboardDat(Path scoreboardDat, String objectiveName) {
        if (!Files.exists(scoreboardDat)) {
            return false;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(scoreboardDat, NbtAccounter.unlimitedHeap());
            CompoundTag dataTag = root.getCompound("data").orElse(root);
            Optional<ListTag> objectivesOpt = dataTag.getList("Objectives");
            if (objectivesOpt.isEmpty()) {
                return false;
            }

            for (Tag objectiveTag : objectivesOpt.get()) {
                if (!(objectiveTag instanceof CompoundTag objectiveCompound)) {
                    continue;
                }

                Optional<String> nameOpt = objectiveCompound.getString("Name");
                if (nameOpt.isPresent() && objectiveName.equals(nameOpt.get())) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }
}
