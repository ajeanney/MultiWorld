package fr.jeanney;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static fr.jeanney.MultiWorld.NAMESPACE;
import static fr.jeanney.CustomServerWorld.isCreativeWorld;
import static fr.jeanney.TestAssertions.*;

public class InventorySwitchTest {

    private static final String CREATIVE_TEST_WORLD = "a_creative_test_switch";

    @GameTest(maxTicks = 200)
    public void creativeCommandSwitchesWorldAndClearsInventory(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        String creativeWorld = CREATIVE_TEST_WORLD + "_" + Long.toUnsignedString(System.nanoTime(), 36);

        player.getInventory().add(new ItemStack(Items.DIAMOND, 1));
        assertHasItem(helper, player, Items.DIAMOND, "after adding it");

        createWorld(helper, player, creativeWorld, "flat", "creative");
        runPlayerCommand(helper, player, "creative");

        helper.runAfterDelay(10, () -> {
            if (!NAMESPACE.equals(player.level().dimension().identifier().getNamespace())
                    || !isCreativeWorld(player.level().dimension())) {
                helper.fail("Player should be in a creative-mode multiworld after /creative but was "
                        + player.level().dimension().identifier());
                return;
            }
            assertNoItem(helper, player, Items.DIAMOND, "after switching to creative");
            helper.succeed();
        });
    }
}
