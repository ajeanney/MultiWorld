package fr.jeanney;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import static fr.jeanney.MultiWorld.NAMESPACE;
import static fr.jeanney.TestAssertions.*;

public class WorldCreationTest {

    private static final String RANDOM_WORLD = "created_random_seed";
    private static final String MATCHED_WORLD = "created_matching_seed";
    private static final String DIFFERENT_SEED_WORLD = "created_different_seed";

    @GameTest(maxTicks = 600)
    public void createWorldWithoutSeedUsesDifferentSeed(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        String randomWorld = uniqueWorldName(RANDOM_WORLD);

        ensureAdvancementsDir(server);
        createWorld(helper, player, randomWorld);
        teleportToWorld(helper, player, randomWorld);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, randomWorld);

            ResourceKey<Level> worldKey = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, randomWorld)
            );
            ServerLevel created = server.getLevel(worldKey);
            if (created == null) {
                helper.fail("Created world not found: " + randomWorld);
                return;
            }

            long overworldSeed = server.overworld().getSeed();
            long createdSeed = created.getSeed();
            if (overworldSeed == createdSeed) {
                helper.fail("World created without seed should use random seed, but seed matched overworld: " + createdSeed);
                return;
            }

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 600)
    public void createWorldWithOverworldSeedMatchesSpawn(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        String matchedWorld = uniqueWorldName(MATCHED_WORLD + "_a");
        String matchedWorldTwin = uniqueWorldName(MATCHED_WORLD + "_b");

        long overworldSeed = server.overworld().getSeed();
        ensureAdvancementsDir(server);
        createWorld(helper, player, matchedWorld, overworldSeed);
        createWorld(helper, player, matchedWorldTwin, overworldSeed);
        teleportToWorld(helper, player, matchedWorld);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, matchedWorld);

            ResourceKey<Level> worldKey = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, matchedWorld)
            );
            ResourceKey<Level> twinKey = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, matchedWorldTwin)
            );
            ServerLevel created = server.getLevel(worldKey);
            ServerLevel twin = server.getLevel(twinKey);
            if (created == null) {
                helper.fail("Created world not found: " + matchedWorld);
                return;
            }
            if (twin == null) {
                helper.fail("Created world not found: " + matchedWorldTwin);
                return;
            }

            if (created.getSeed() != overworldSeed) {
                helper.fail("Created world seed mismatch: expected " + overworldSeed + " but got " + created.getSeed());
                return;
            }

            BlockPos createdSpawn = created.getRespawnData().pos();
            BlockPos twinSpawn = twin.getRespawnData().pos();
            if (createdSpawn.getX() != twinSpawn.getX() || createdSpawn.getZ() != twinSpawn.getZ()) {
                helper.fail("Spawn X/Z mismatch for same seed worlds: first=" + createdSpawn
                        + ", second=" + twinSpawn);
                return;
            }

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 600)
    public void createWorldWithDifferentSeedHasDifferentSpawn(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        String differentSeedWorld = uniqueWorldName(DIFFERENT_SEED_WORLD + "_a");
        String differentSeedWorldB = uniqueWorldName(DIFFERENT_SEED_WORLD + "_b");

        long overworldSeed = server.overworld().getSeed();
        long differentSeed = overworldSeed ^ 0x5DEECE66DL;
        long anotherSeed = differentSeed ^ 0x9E3779B97F4A7C15L;
        ensureAdvancementsDir(server);
        createWorld(helper, player, differentSeedWorld, differentSeed);
        createWorld(helper, player, differentSeedWorldB, anotherSeed);
        teleportToWorld(helper, player, differentSeedWorld);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, differentSeedWorld);

            ResourceKey<Level> worldKey = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, differentSeedWorld)
            );
            ResourceKey<Level> worldKeyB = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, differentSeedWorldB)
            );
            ServerLevel created = server.getLevel(worldKey);
            ServerLevel createdB = server.getLevel(worldKeyB);
            if (created == null) {
                helper.fail("Created world not found: " + differentSeedWorld);
                return;
            }
            if (createdB == null) {
                helper.fail("Created world not found: " + differentSeedWorldB);
                return;
            }

            if (created.getSeed() != differentSeed) {
                helper.fail("Created world seed mismatch: expected " + differentSeed + " but got " + created.getSeed());
                return;
            }

            if (created.getRespawnData().pos().equals(createdB.getRespawnData().pos())) {
                helper.fail("Spawn should differ for different seeds: first=" + created.getRespawnData().pos()
                        + ", second=" + createdB.getRespawnData().pos());
                return;
            }

            helper.succeed();
        });
    }

    private static String uniqueWorldName(String prefix) {
        return prefix + "_" + Long.toUnsignedString(System.nanoTime(), 36);
    }
}
