package com.gmail.anthony17j.multiworld.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.isCreativeWorld;
import static com.gmail.anthony17j.multiworld.test.TestAssertions.*;

public class CreativeBlocksAdvancementsTest {

    private static final String CREATIVE_ADVANCEMENT_WORLD = "a_creative_advancements";

    @GameTest(maxTicks = 400)
    public void advancementsAreBlockedInCreativeWorld(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        String creativeWorld = CREATIVE_ADVANCEMENT_WORLD + "_" + Long.toUnsignedString(System.nanoTime(), 36);

        String worldFolder = server.getWorldPath(LevelResource.ROOT).toString();
        new File(worldFolder + "/advancements").mkdirs();

        createWorld(helper, player, creativeWorld, "flat", "creative");

        // Switch to creative world
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "creative");

        helper.runAfterDelay(10, () -> {
            if (!"multiworld".equals(player.level().dimension().identifier().getNamespace()) || !isCreativeWorld(player.level().dimension())) {
                helper.fail("Expected /creative to place player in a creative-mode multiworld, but was " + player.level().dimension().identifier());
                return;
            }

            // Try to grant an advancement manually via award() - should be blocked by PlayerAdvancementsMixin
            AdvancementHolder mineStone = server.getAdvancements().get(Identifier.parse("minecraft:story/mine_stone"));
            if (mineStone == null) helper.fail("Advancement 'story/mine_stone' not found");

            AdvancementProgress progressBefore = player.getAdvancements().getOrStartProgress(mineStone);
            for (String criterion : progressBefore.getRemainingCriteria()) {
                player.getAdvancements().award(mineStone, criterion);
            }

            // Verify it was blocked
            AdvancementProgress progressAfter = player.getAdvancements().getOrStartProgress(mineStone);
            if (progressAfter.isDone()) {
                helper.fail("Advancement should NOT be grantable in creative world, but it was completed");
            }

            // Give the player items that would normally unlock recipes - verify no advancement triggers
            player.getInventory().add(new ItemStack(Items.DIAMOND, 64));
            player.getInventory().add(new ItemStack(Items.OAK_LOG, 64));
            player.getInventory().add(new ItemStack(Items.COBBLESTONE, 64));

            // Wait a tick for any triggers to fire
            helper.runAfterDelay(5, () -> {
                // Verify "getting_wood" type advancements haven't triggered
                AdvancementHolder mineStoneCheck = server.getAdvancements().get(Identifier.parse("minecraft:story/mine_stone"));
                AdvancementProgress stoneProgress = player.getAdvancements().getOrStartProgress(mineStoneCheck);
                if (stoneProgress.isDone()) {
                    helper.fail("Advancement 'mine_stone' should NOT trigger in creative world");
                }

                // Now go back to overworld and verify advancements work normally
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "creative");

                helper.runAfterDelay(10, () -> {
                    assertInDimension(helper, player, "minecraft", "overworld");

                    // In overworld, advancements should work
                    AdvancementHolder overworldAdv = server.getAdvancements().get(Identifier.parse("minecraft:story/mine_stone"));
                    AdvancementProgress owProgress = player.getAdvancements().getOrStartProgress(overworldAdv);
                    for (String criterion : owProgress.getRemainingCriteria()) {
                        player.getAdvancements().award(overworldAdv, criterion);
                    }

                    AdvancementProgress owProgressAfter = player.getAdvancements().getOrStartProgress(overworldAdv);
                    if (!owProgressAfter.isDone()) {
                        helper.fail("Advancement should be grantable in overworld, but it was not completed");
                    }

                    helper.succeed();
                });
            });
        });
    }
}
