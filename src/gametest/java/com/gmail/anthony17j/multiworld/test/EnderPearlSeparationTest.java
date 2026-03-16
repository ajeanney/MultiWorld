package com.gmail.anthony17j.multiworld.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import static com.gmail.anthony17j.multiworld.test.TestAssertions.*;

public class EnderPearlSeparationTest {

    private static final String WORLD_NAME = SHARED_TEST_WORLD;

    @GameTest(maxTicks = 800)
    public void thrownEnderPearlIsSeparatePerWorld(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();

        ensureAdvancementsDir(server);
        createWorld(helper, player, WORLD_NAME);

        // Record starting position
        double startX = player.getX();
        double startY = player.getY();
        double startZ = player.getZ();

        // Throw an ender pearl high up so it takes a while to land
        ThrownEnderpearl pearl = new ThrownEnderpearl(level, player, new ItemStack(Items.ENDER_PEARL));
        pearl.setPos(player.getX(), player.getY() + 1, player.getZ());
        pearl.setDeltaMovement(new Vec3(0, 1.5, 0)); // Throw upward (moderate velocity so it lands within ~100 ticks)
        level.addFreshEntity(pearl);

        // Verify pearl exists
        if (player.getEnderPearls().isEmpty()) {
            helper.fail("Player should have an ender pearl in flight");
        }

        // Wait a couple ticks for the pearl to start moving, then switch to custom world
        helper.runAfterDelay(3, () -> {
            // Step 1: /mw tp while pearl is in the air
            teleportToWorld(helper, player, WORLD_NAME);

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, "multiworld", WORLD_NAME);

                // In custom world: the pearl should not exist (was discarded in saveInv)
                if (!player.getEnderPearls().isEmpty()) {
                    helper.fail("Player should NOT have ender pearls in custom world");
                }

                // Wait some ticks - nothing should teleport us
                helper.runAfterDelay(30, () -> {
                    assertInDimension(helper, player, "multiworld", WORLD_NAME);

                    // Step 2: /mw tp -> back to overworld (pearl should resume)
                    teleportToWorld(helper, player, "overworld");

                    helper.runAfterDelay(10, () -> {
                        assertInDimension(helper, player, "minecraft", "overworld");

                        // The pearl should be respawned (loadAndSpawnEnderPearls)
                        if (player.getEnderPearls().isEmpty()) {
                            helper.fail("Player should have ender pearl restored back in overworld");
                        }

                        helper.succeed();
                    });
                });
            });
        });
    }
}
