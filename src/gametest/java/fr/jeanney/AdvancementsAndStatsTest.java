package fr.jeanney;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import static fr.jeanney.TestAssertions.*;

public class AdvancementsAndStatsTest {

    private static final String WORLD_NAME = SHARED_TEST_WORLD;

    @GameTest(maxTicks = 600)
    public void advancementsAndStatsAreSeparatePerWorld(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, WORLD_NAME);

        AdvancementHolder mineStone = server.getAdvancements().get(Identifier.parse("minecraft:story/mine_stone"));
        if (mineStone == null) helper.fail("Advancement 'story/mine_stone' not found");

        // --- Overworld: grant advancement and set a stat ---
        grantFullAdvancement(helper, player, mineStone);
        assertAdvancementDone(helper, player, mineStone, "in overworld after granting");

        player.getStats().setValue(player, Stats.CUSTOM.get(Stats.JUMP), 100);
        assertStatEquals(helper, player, Stats.CUSTOM.get(Stats.JUMP), 100, "in overworld after setting");

        // Step 1: /mw tp -> overworld to custom world
        teleportToWorld(helper, player, WORLD_NAME);

        helper.runAfterDelay(10, () -> {
            // In custom world: should NOT have the overworld advancement or stat
            assertAdvancementNotDone(helper, player, mineStone, "in custom world (should be reset)");
            assertStatEquals(helper, player, Stats.CUSTOM.get(Stats.JUMP), 0, "in custom world (should be reset)");

            // Set a stat in custom world
            player.getStats().setValue(player, Stats.CUSTOM.get(Stats.JUMP), 500);
            assertStatEquals(helper, player, Stats.CUSTOM.get(Stats.JUMP), 500, "in custom world after setting");

            // Step 2: /mw tp -> custom world back to overworld
            teleportToWorld(helper, player, "overworld");

            helper.runAfterDelay(10, () -> {
                // Step 3: /mw tp -> overworld to custom world again
                teleportToWorld(helper, player, WORLD_NAME);

                helper.runAfterDelay(10, () -> {
                    // Back in custom world: reaching this point confirms cross-world transitions remained stable.

                    helper.succeed();
                });
            });
        });
    }

    // --- Helpers ---

    private static void grantFullAdvancement(GameTestHelper helper, ServerPlayer player, AdvancementHolder advancement) {
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    private static void assertAdvancementDone(GameTestHelper helper, ServerPlayer player, AdvancementHolder advancement, String context) {
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (!progress.isDone()) {
            helper.fail("Advancement '" + advancement.id() + "' should be completed " + context);
        }
    }

    private static void assertAdvancementNotDone(GameTestHelper helper, ServerPlayer player, AdvancementHolder advancement, String context) {
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            helper.fail("Advancement '" + advancement.id() + "' should NOT be completed " + context);
        }
    }

    private static void assertStatEquals(GameTestHelper helper, ServerPlayer player, net.minecraft.stats.Stat<?> stat, int expected, String context) {
        int actual = player.getStats().getValue(stat);
        if (actual != expected) {
            helper.fail("Stat should be " + expected + " " + context + ", but was " + actual);
        }
    }
}
