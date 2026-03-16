package com.gmail.anthony17j.multiworld.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;
import static com.gmail.anthony17j.multiworld.test.TestAssertions.*;

public class RoundTripTest {

    private static final String WORLD_NAME = SHARED_TEST_WORLD;

    @GameTest(maxTicks = 400)
    public void customWorldRoundTripPreservesInventoryAndPosition(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, WORLD_NAME);

        // Record overworld position
        double owX = player.getX();
        double owY = player.getY();
        double owZ = player.getZ();

        // Give diamond in overworld
        player.getInventory().add(new ItemStack(Items.DIAMOND, 1));
        assertHasItem(helper, player, Items.DIAMOND, "in overworld before world switch");

        // Step 1: /mw tp -> overworld to custom world
        teleportToWorld(helper, player, WORLD_NAME);

        helper.runAfterDelay(10, () -> {
            // Should be in custom world without the diamond
            assertInDimension(helper, player, NAMESPACE, WORLD_NAME);
            assertNoItem(helper, player, Items.DIAMOND, "in custom world");

            // Record custom world position
            double crX = player.getX();
            double crY = player.getY();
            double crZ = player.getZ();

            // Give emerald in custom world
            player.getInventory().add(new ItemStack(Items.EMERALD, 1));
            assertHasItem(helper, player, Items.EMERALD, "after adding emerald in custom world");

            // Step 2: /mw tp -> custom world back to overworld
            teleportToWorld(helper, player, "overworld");

            helper.runAfterDelay(10, () -> {
                // Should be back in overworld with diamond, without emerald, at overworld position
                assertInDimension(helper, player, "minecraft", "overworld");
                assertHasItem(helper, player, Items.DIAMOND, "back in overworld");
                assertNoItem(helper, player, Items.EMERALD, "back in overworld (emerald stayed in custom world)");
                assertNearPosition(helper, player, owX, owY, owZ, 1.0, "back in overworld");

                // Step 3: /mw tp -> overworld to custom world again
                teleportToWorld(helper, player, WORLD_NAME);

                helper.runAfterDelay(10, () -> {
                    // Should be back in custom world with emerald, without diamond, at custom-world position
                    assertInDimension(helper, player, NAMESPACE, WORLD_NAME);
                    assertHasItem(helper, player, Items.EMERALD, "back in custom world");
                    assertNoItem(helper, player, Items.DIAMOND, "back in custom world (diamond stayed in overworld)");
                    assertNearPosition(helper, player, crX, crY, crZ, 1.0, "back in custom world");

                    helper.succeed();
                });
            });
        });
    }
}
