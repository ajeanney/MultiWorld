package fr.jeanney;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.Boat;

import static fr.jeanney.TestAssertions.*;

public class MountedEntityTest {

    private static final String WORLD_NAME = SHARED_TEST_WORLD;

    @GameTest(maxTicks = 600)
    public void mountedEntityDisappearsAndReappearsOnWorldSwitch(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();

        ensureAdvancementsDir(server);
        createWorld(helper, player, WORLD_NAME);

        // Spawn a boat at the player's position and make the player ride it
        Boat boat = (Boat) EntityType.OAK_BOAT.create(level, EntitySpawnReason.COMMAND);
        if (boat == null)
            helper.fail("Failed to create boat");
        boat.setPos(player.getX(), player.getY(), player.getZ());
        level.addFreshEntity(boat);
        player.startRiding(boat);

        if (player.getVehicle() == null)
            helper.fail("Player should be riding the boat");

        // Step 1: /mw tp -> overworld to custom world (boat should be discarded)
        teleportToWorld(helper, player, WORLD_NAME);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, "multiworld", WORLD_NAME);

            // In custom world: player should NOT be riding anything
            if (player.getVehicle() != null) {
                helper.fail("Player should NOT be riding anything in custom world");
            }

            // Step 2: /mw tp -> back to overworld (boat should reappear)
            teleportToWorld(helper, player, "overworld");

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, "minecraft", "overworld");

                // Back in overworld: player should be riding the boat again
                if (player.getVehicle() == null) {
                    helper.fail("Player should be riding the boat again back in overworld");
                }

                helper.succeed();
            });
        });
    }
}
