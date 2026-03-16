package com.gmail.anthony17j.multiworld.test;

import com.gmail.anthony17j.multiworld.Utils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.gmail.anthony17j.multiworld.MultiWorld.CREATIVE_WORLD_NAME;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;
import static com.gmail.anthony17j.multiworld.test.TestAssertions.*;

public class InventorySwitchTest {

    @GameTest(maxTicks = 200)
    public void creativeCommandSwitchesWorldAndClearsInventory(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);

        player.getInventory().add(new ItemStack(Items.DIAMOND, 1));
        assertHasItem(helper, player, Items.DIAMOND, "after adding it");

        Utils.saveInv(player, "overworld");
        Utils.loadInv(player, CREATIVE_WORLD_NAME);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, "creative");
            assertNoItem(helper, player, Items.DIAMOND, "after switching to creative");
            helper.succeed();
        });
    }
}
