package com.gmail.anthony17j.multiworld.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;
import static com.gmail.anthony17j.multiworld.test.TestAssertions.*;

public class WorldCreationTest {

    private static final String RANDOM_WORLD = "created_random_seed";
    private static final String MATCHED_WORLD = "created_matching_seed";

    @GameTest(maxTicks = 600)
    public void createWorldWithoutSeedUsesDifferentSeed(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, RANDOM_WORLD);
        teleportToWorld(helper, player, RANDOM_WORLD);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, RANDOM_WORLD);

            ResourceKey<Level> worldKey = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, RANDOM_WORLD)
            );
            ServerLevel created = server.getLevel(worldKey);
            if (created == null) {
                helper.fail("Created world not found: " + RANDOM_WORLD);
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

        long overworldSeed = server.overworld().getSeed();
        ensureAdvancementsDir(server);
        createWorld(helper, player, MATCHED_WORLD, overworldSeed);
        teleportToWorld(helper, player, MATCHED_WORLD);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, MATCHED_WORLD);

            ResourceKey<Level> worldKey = ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, MATCHED_WORLD)
            );
            ServerLevel created = server.getLevel(worldKey);
            if (created == null) {
                helper.fail("Created world not found: " + MATCHED_WORLD);
                return;
            }

            if (created.getSeed() != overworldSeed) {
                helper.fail("Created world seed mismatch: expected " + overworldSeed + " but got " + created.getSeed());
                return;
            }

            if (!created.getRespawnData().pos().equals(server.overworld().getRespawnData().pos())) {
                helper.fail("Spawn mismatch for same seed: overworld=" + server.overworld().getRespawnData().pos()
                        + ", created=" + created.getRespawnData().pos());
                return;
            }

            helper.succeed();
        });
    }
}
