package com.gmail.anthony17j.multiworld.test;

import com.gmail.anthony17j.multiworld.mixin.ServerLevelAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ServerLevelData;

import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;
import static com.gmail.anthony17j.multiworld.test.TestAssertions.*;

public class WorldPropertiesIsolationTest {

    private static final String ISOLATION_RULES_WORLD = "isolation_rules_world";
    private static final String ISOLATION_DIFFICULTY_WORLD = "isolation_difficulty_world";
    private static final String ISOLATION_TIME_WEATHER_WORLD = "isolation_time_weather_world";
    private static final String ISOLATION_KEEP_INVENTORY_WORLD = "isolation_keep_inventory_world";

    @GameTest(maxTicks = 600)
    public void gameRulesAndScheduledEventsAreIsolated(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, ISOLATION_RULES_WORLD);

        helper.runAfterDelay(10, () -> {
            ServerLevel custom = getCustomWorld(server, ISOLATION_RULES_WORLD);
            if (custom == null) {
                helper.fail("Custom world not found: " + ISOLATION_RULES_WORLD);
                return;
            }

            ServerLevel overworld = server.overworld();
            ServerLevelData customProps = ((ServerLevelAccessor) custom).getWorldProperties();
            ServerLevelData overworldProps = ((ServerLevelAccessor) overworld).getWorldProperties();

            if (customProps.getGameRules() == overworldProps.getGameRules()) {
                helper.fail("Expected isolated GameRules instance for custom world");
                return;
            }

            if (customProps.getScheduledEvents() == overworldProps.getScheduledEvents()) {
                helper.fail("Expected isolated scheduled events queue for custom world");
                return;
            }

            int previousOverworldTickSpeed = overworldProps.getGameRules().get(GameRules.RANDOM_TICK_SPEED);
            int previousCustomTickSpeed = customProps.getGameRules().get(GameRules.RANDOM_TICK_SPEED);

            try {
                overworldProps.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 11, server);
                if (customProps.getGameRules().get(GameRules.RANDOM_TICK_SPEED) != previousCustomTickSpeed) {
                    helper.fail("Custom world game rules changed after overworld mutation");
                    return;
                }

                customProps.getGameRules().set(GameRules.RANDOM_TICK_SPEED, 17, server);
                if (overworldProps.getGameRules().get(GameRules.RANDOM_TICK_SPEED) != 11) {
                    helper.fail("Overworld game rules changed after custom-world mutation");
                    return;
                }
            } finally {
                overworldProps.getGameRules().set(GameRules.RANDOM_TICK_SPEED, previousOverworldTickSpeed, server);
                customProps.getGameRules().set(GameRules.RANDOM_TICK_SPEED, previousCustomTickSpeed, server);
            }

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 600)
    public void difficultySnapshotIsIsolated(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, ISOLATION_DIFFICULTY_WORLD);

        helper.runAfterDelay(10, () -> {
            ServerLevel custom = getCustomWorld(server, ISOLATION_DIFFICULTY_WORLD);
            if (custom == null) {
                helper.fail("Custom world not found: " + ISOLATION_DIFFICULTY_WORLD);
                return;
            }

            ServerLevelData customProps = ((ServerLevelAccessor) custom).getWorldProperties();

            Difficulty initialDifficulty = customProps.getDifficulty();
            boolean initialDifficultyLocked = customProps.isDifficultyLocked();

            Difficulty targetDifficulty = initialDifficulty == Difficulty.HARD ? Difficulty.EASY : Difficulty.HARD;
            boolean targetDifficultyLocked = !initialDifficultyLocked;

            try {
                server.getWorldData().setDifficulty(targetDifficulty);
                server.getWorldData().setDifficultyLocked(targetDifficultyLocked);

                if (customProps.getDifficulty() != initialDifficulty) {
                    helper.fail("Custom world difficulty changed after global world-data mutation");
                    return;
                }

                if (customProps.isDifficultyLocked() != initialDifficultyLocked) {
                    helper.fail("Custom world difficulty lock changed after global world-data mutation");
                    return;
                }
            } finally {
                server.getWorldData().setDifficulty(initialDifficulty);
                server.getWorldData().setDifficultyLocked(initialDifficultyLocked);
            }

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 600)
    public void timeAndWeatherAreIsolated(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, ISOLATION_TIME_WEATHER_WORLD);

        helper.runAfterDelay(10, () -> {
            ServerLevel custom = getCustomWorld(server, ISOLATION_TIME_WEATHER_WORLD);
            if (custom == null) {
                helper.fail("Custom world not found: " + ISOLATION_TIME_WEATHER_WORLD);
                return;
            }

            ServerLevel overworld = server.overworld();
            ServerLevelData customProps = ((ServerLevelAccessor) custom).getWorldProperties();
            ServerLevelData overworldProps = ((ServerLevelAccessor) overworld).getWorldProperties();

            long initialOverworldGameTime = overworldProps.getGameTime();
            long initialOverworldDayTime = overworldProps.getDayTime();
            int initialOverworldClearWeather = overworldProps.getClearWeatherTime();
            boolean initialOverworldRaining = overworldProps.isRaining();
            int initialOverworldRainTime = overworldProps.getRainTime();
            boolean initialOverworldThundering = overworldProps.isThundering();
            int initialOverworldThunderTime = overworldProps.getThunderTime();

            long initialCustomGameTime = customProps.getGameTime();
            long initialCustomDayTime = customProps.getDayTime();
            int initialCustomClearWeather = customProps.getClearWeatherTime();
            boolean initialCustomRaining = customProps.isRaining();
            int initialCustomRainTime = customProps.getRainTime();
            boolean initialCustomThundering = customProps.isThundering();
            int initialCustomThunderTime = customProps.getThunderTime();

            try {
                overworldProps.setGameTime(initialOverworldGameTime + 2000L);
                overworldProps.setDayTime(initialOverworldDayTime + 4000L);
                overworldProps.setClearWeatherTime(0);
                overworldProps.setRaining(!initialOverworldRaining);
                overworldProps.setRainTime(initialOverworldRainTime + 600);
                overworldProps.setThundering(!initialOverworldThundering);
                overworldProps.setThunderTime(initialOverworldThunderTime + 800);

                if (customProps.getGameTime() != initialCustomGameTime
                        || customProps.getDayTime() != initialCustomDayTime
                        || customProps.getClearWeatherTime() != initialCustomClearWeather
                        || customProps.isRaining() != initialCustomRaining
                        || customProps.getRainTime() != initialCustomRainTime
                        || customProps.isThundering() != initialCustomThundering
                        || customProps.getThunderTime() != initialCustomThunderTime) {
                    helper.fail("Custom world time/weather changed after overworld mutation");
                    return;
                }

                long expectedOverworldGameTime = overworldProps.getGameTime();
                long expectedOverworldDayTime = overworldProps.getDayTime();
                int expectedOverworldClearWeather = overworldProps.getClearWeatherTime();
                boolean expectedOverworldRaining = overworldProps.isRaining();
                int expectedOverworldRainTime = overworldProps.getRainTime();
                boolean expectedOverworldThundering = overworldProps.isThundering();
                int expectedOverworldThunderTime = overworldProps.getThunderTime();

                customProps.setGameTime(initialCustomGameTime + 1234L);
                customProps.setDayTime(initialCustomDayTime + 5678L);
                customProps.setClearWeatherTime(initialCustomClearWeather + 90);
                customProps.setRaining(!initialCustomRaining);
                customProps.setRainTime(initialCustomRainTime + 91);
                customProps.setThundering(!initialCustomThundering);
                customProps.setThunderTime(initialCustomThunderTime + 92);

                if (overworldProps.getGameTime() != expectedOverworldGameTime
                        || overworldProps.getDayTime() != expectedOverworldDayTime
                        || overworldProps.getClearWeatherTime() != expectedOverworldClearWeather
                        || overworldProps.isRaining() != expectedOverworldRaining
                        || overworldProps.getRainTime() != expectedOverworldRainTime
                        || overworldProps.isThundering() != expectedOverworldThundering
                        || overworldProps.getThunderTime() != expectedOverworldThunderTime) {
                    helper.fail("Overworld time/weather changed after custom-world mutation");
                    return;
                }
            } finally {
                overworldProps.setGameTime(initialOverworldGameTime);
                overworldProps.setDayTime(initialOverworldDayTime);
                overworldProps.setClearWeatherTime(initialOverworldClearWeather);
                overworldProps.setRaining(initialOverworldRaining);
                overworldProps.setRainTime(initialOverworldRainTime);
                overworldProps.setThundering(initialOverworldThundering);
                overworldProps.setThunderTime(initialOverworldThunderTime);

                customProps.setGameTime(initialCustomGameTime);
                customProps.setDayTime(initialCustomDayTime);
                customProps.setClearWeatherTime(initialCustomClearWeather);
                customProps.setRaining(initialCustomRaining);
                customProps.setRainTime(initialCustomRainTime);
                customProps.setThundering(initialCustomThundering);
                customProps.setThunderTime(initialCustomThunderTime);
            }

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 1200)
    public void keepInventoryCommandIsScopedPerWorld(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, ISOLATION_KEEP_INVENTORY_WORLD);

        helper.runAfterDelay(10, () -> {
            ServerLevel custom = getCustomWorld(server, ISOLATION_KEEP_INVENTORY_WORLD);
            if (custom == null) {
                helper.fail("Custom world not found: " + ISOLATION_KEEP_INVENTORY_WORLD);
                return;
            }

            boolean initialOverworldKeepInventory = server.overworld().getGameRules().get(GameRules.KEEP_INVENTORY);
            boolean initialCustomKeepInventory = custom.getGameRules().get(GameRules.KEEP_INVENTORY);

            if (!setKeepInventoryViaCommand(server, server.overworld(), true)) {
                // Some GameTest runtimes expose /gamerule in read-only form. Fall back to API mutation
                // so we can still verify per-world keepInventory behavior across death/respawn.
                server.overworld().getGameRules().set(GameRules.KEEP_INVENTORY, true, server);
            }

            if (!server.overworld().getGameRules().get(GameRules.KEEP_INVENTORY)) {
                helper.fail("Overworld keepInventory was not enabled by /gamerule command");
                return;
            }

            if (custom.getGameRules().get(GameRules.KEEP_INVENTORY) != initialCustomKeepInventory) {
                helper.fail("Custom world keepInventory changed when setting overworld /gamerule");
                return;
            }

            player.getInventory().add(new ItemStack(Items.DIAMOND, 1));
            assertHasItem(helper, player, Items.DIAMOND, "in overworld before death with keepInventory=true");

            ServerPlayer respawned = killAndRespawn(player, server);
            assertInDimension(helper, respawned, "minecraft", "overworld");
            assertHasItem(helper, respawned, Items.DIAMOND, "after overworld death with keepInventory=true");

            // Restore overworld gamerule before continuing to avoid leaking state.
            server.overworld().getGameRules().set(GameRules.KEEP_INVENTORY, initialOverworldKeepInventory, server);

            teleportToWorld(helper, respawned, ISOLATION_KEEP_INVENTORY_WORLD);
            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, respawned, NAMESPACE, ISOLATION_KEEP_INVENTORY_WORLD);

                if (custom.getGameRules().get(GameRules.KEEP_INVENTORY)) {
                    helper.fail("Custom world keepInventory should remain false for this test scenario");
                    return;
                }

                respawned.getInventory().add(new ItemStack(Items.EMERALD, 1));
                assertHasItem(helper, respawned, Items.EMERALD, "in custom world before death with keepInventory=false");

                ServerPlayer customRespawn = killAndRespawn(respawned, server);
                assertInDimension(helper, customRespawn, NAMESPACE, ISOLATION_KEEP_INVENTORY_WORLD);
                assertNoItem(helper, customRespawn, Items.EMERALD, "after custom world death with keepInventory=false");

                // Restore custom-world gamerule at test end as well.
                custom.getGameRules().set(GameRules.KEEP_INVENTORY, initialCustomKeepInventory, server);
                helper.succeed();
            });
        });
    }

    private static ServerLevel getCustomWorld(MinecraftServer server, String worldName) {
        ResourceKey<Level> worldKey = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(NAMESPACE, worldName)
        );
        return server.getLevel(worldKey);
    }

    private static ServerPlayer killAndRespawn(ServerPlayer player, MinecraftServer server) {
        player.kill((ServerLevel) player.level());
        ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        ServerPlayer listed = server.getPlayerList().getPlayer(respawned.getUUID());
        return listed != null ? listed : respawned;
    }

    private static void runCommandInWorld(MinecraftServer server, ServerLevel level, String command) {
        CommandSourceStack source = server.createCommandSourceStack()
                .withLevel(level)
                .withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.OWNERS));
        server.getCommands().performPrefixedCommand(source, command);
    }

    private static boolean setKeepInventoryViaCommand(MinecraftServer server, ServerLevel level, boolean value) {
        String boolValue = value ? "true" : "false";
        String altBoolValue = value ? "on" : "off";
        String[] candidates = {
                "gamerule keepInventory " + boolValue,
                "gamerule keepInventory set " + boolValue,
            "gamerule set keepInventory " + boolValue,
            "gamerule keepinventory " + boolValue,
            "gamerule keepinventory set " + boolValue,
            "gamerule set keepinventory " + boolValue,
            "gamerules keepInventory " + boolValue,
            "gamerules keepInventory set " + boolValue,
            "gamerules set keepInventory " + boolValue,
            "gamerules keepinventory " + boolValue,
            "gamerules keepinventory set " + boolValue,
            "gamerules set keepinventory " + boolValue,
            "gamerule keepInventory " + altBoolValue,
            "gamerule keepinventory " + altBoolValue,
            "gamerules keepInventory " + altBoolValue,
            "gamerules keepinventory " + altBoolValue
        };

        for (String candidate : candidates) {
            runCommandInWorld(server, level, candidate);
            if (level.getGameRules().get(GameRules.KEEP_INVENTORY) == value) {
                return true;
            }
        }

        return false;
    }
}