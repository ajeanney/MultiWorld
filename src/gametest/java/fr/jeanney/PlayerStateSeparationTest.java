package fr.jeanney;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Optional;

import static fr.jeanney.TestAssertions.*;

public class PlayerStateSeparationTest {

    private static final String WORLD_NAME = SHARED_TEST_WORLD;

    @GameTest(maxTicks = 600)
    public void playerStateIsSeparatePerWorld(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, WORLD_NAME);

        // Get a mob effect holder (SPEED)
        Optional<Holder.Reference<MobEffect>> speedEffect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse("minecraft:speed"));
        if (speedEffect.isEmpty()) helper.fail("Could not find SPEED mob effect");
        Holder<MobEffect> speed = speedEffect.get();

        // --- Set up overworld state ---
        player.setHealth(10.0f); // Half health
        player.getFoodData().setFoodLevel(10); // Half hunger
        player.setAirSupply(100); // Low air
        player.addEffect(new MobEffectInstance(speed, 10000, 2)); // Speed III for a long time
        player.giveExperienceLevels(30);
        player.setYRot(90.0f); // Looking east
        player.setXRot(-45.0f); // Looking up
        player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD, 1));

        // Verify overworld state
        assertFloatNear(helper, player.getHealth(), 10.0f, 0.1f, "overworld health");
        assertIntEquals(helper, player.getFoodData().getFoodLevel(), 10, "overworld food level");
        assertIntEquals(helper, player.getAirSupply(), 100, "overworld air supply");
        if (!player.hasEffect(speed)) helper.fail("Player should have SPEED in overworld");
        assertIntEquals(helper, player.experienceLevel, 30, "overworld XP level");

        float owYaw = player.getYRot();
        float owPitch = player.getXRot();

        // Step 1: /mw tp -> overworld to custom world
        teleportToWorld(helper, player, WORLD_NAME);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, "multiworld", WORLD_NAME);

            // Custom world: should have default/fresh state
            if (player.hasEffect(speed)) {
                helper.fail("Player should NOT have SPEED effect in custom world");
            }
            assertIntEquals(helper, player.experienceLevel, 0, "custom-world XP level (should be reset)");
            assertNoItem(helper, player, Items.DIAMOND_SWORD, "in custom world");

            // Set custom world state
            Optional<Holder.Reference<MobEffect>> poisonEffect = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse("minecraft:poison"));
            if (poisonEffect.isEmpty()) helper.fail("Could not find POISON mob effect");
            Holder<MobEffect> poison = poisonEffect.get();

            player.setHealth(5.0f);
            player.getFoodData().setFoodLevel(5);
            player.addEffect(new MobEffectInstance(poison, 10000, 1)); // Poison II
            player.giveExperienceLevels(50);
            player.setYRot(-90.0f); // Looking west
            player.setXRot(30.0f); // Looking slightly down
            player.getInventory().add(new ItemStack(Items.GOLDEN_APPLE, 1));

            float crYaw = player.getYRot();
            float crPitch = player.getXRot();

            // Step 2: /mw tp -> custom world back to overworld
            teleportToWorld(helper, player, "overworld");

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, "minecraft", "overworld");

                // Should have overworld state restored
                assertFloatNear(helper, player.getHealth(), 10.0f, 1.0f, "overworld health restored");
                assertIntEquals(helper, player.getFoodData().getFoodLevel(), 10, "overworld food level restored");
                if (!player.hasEffect(speed)) {
                    helper.fail("Player should have SPEED effect back in overworld");
                }
                assertIntEquals(helper, player.experienceLevel, 30, "overworld XP level restored");
                assertHasItem(helper, player, Items.DIAMOND_SWORD, "back in overworld");
                assertNoItem(helper, player, Items.GOLDEN_APPLE, "back in overworld (custom-world item)");
                assertFloatNear(helper, player.getYRot(), owYaw, 1.0f, "overworld yaw restored");
                assertFloatNear(helper, player.getXRot(), owPitch, 1.0f, "overworld pitch restored");

                // Step 3: /mw tp -> overworld to custom world again
                teleportToWorld(helper, player, WORLD_NAME);

                helper.runAfterDelay(10, () -> {
                    assertInDimension(helper, player, "multiworld", WORLD_NAME);

                    // Should have custom-world state restored
                    assertFloatNear(helper, player.getHealth(), 5.0f, 1.0f, "custom-world health restored");
                    assertIntEquals(helper, player.getFoodData().getFoodLevel(), 5, "custom-world food level restored");
                    assertIntEquals(helper, player.experienceLevel, 50, "custom-world XP level restored");
                    assertHasItem(helper, player, Items.GOLDEN_APPLE, "back in custom world");
                    assertNoItem(helper, player, Items.DIAMOND_SWORD, "back in custom world (overworld item)");
                    assertFloatNear(helper, player.getYRot(), crYaw, 1.0f, "custom-world yaw restored");
                    assertFloatNear(helper, player.getXRot(), crPitch, 1.0f, "custom-world pitch restored");

                    helper.succeed();
                });
            });
        });
    }

    // --- Assertion helpers ---

    private static void assertFloatNear(GameTestHelper helper, float actual, float expected, float tolerance, String context) {
        if (Math.abs(actual - expected) > tolerance) {
            helper.fail("Expected " + expected + " (±" + tolerance + ") " + context + ", but was " + actual);
        }
    }

    private static void assertIntEquals(GameTestHelper helper, int actual, int expected, String context) {
        if (actual != expected) {
            helper.fail("Expected " + expected + " " + context + ", but was " + actual);
        }
    }
}
