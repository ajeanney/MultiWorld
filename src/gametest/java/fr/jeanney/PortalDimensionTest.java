package fr.jeanney;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;

import static fr.jeanney.MultiWorld.NAMESPACE;
import static fr.jeanney.TestAssertions.*;

public class PortalDimensionTest {

    private static final String NETHER_TEST_WORLD = SHARED_TEST_WORLD;
    private static final String END_TEST_WORLD = SHARED_TEST_WORLD;

    @GameTest(maxTicks = 2000)
    public void overworldNetherPortalRoundTrip(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = (ServerLevel) player.level();
        ensureAdvancementsDir(server);

        // Build nether portal near player
        BlockPos base = player.blockPosition().offset(5, 0, 0);
        buildNetherPortal(overworld, base);
        BlockPos portalPos = base.offset(1, 1, 0);

        // Use getPortalDestination directly to get the transition (tests the mixin routing)
        TeleportTransition transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                .getPortalDestination(overworld, player, portalPos);
        if (transition == null) {
            helper.fail("Nether portal getPortalDestination returned null from overworld");
            return;
        }

        assertDimensionKey(helper, transition.newLevel().dimension(), "minecraft", "the_nether",
                "nether portal destination from overworld");

        // Actually teleport the player
        player.teleport(transition);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, "minecraft", "the_nether");

            // Find the portal that was created in the nether
            ServerLevel nether = (ServerLevel) player.level();
            BlockPos netherPortal = findNearbyPortalBlock(nether, player.blockPosition(), 10);
            if (netherPortal == null) {
                helper.fail("No nether portal block found near player in the_nether");
                return;
            }

            // Use getPortalDestination from nether to go back
            TeleportTransition returnTransition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                    .getPortalDestination(nether, player, netherPortal);
            if (returnTransition == null) {
                helper.fail("Nether portal getPortalDestination returned null from the_nether");
                return;
            }

            assertDimensionKey(helper, returnTransition.newLevel().dimension(), "minecraft", "overworld",
                    "nether portal return destination");

            player.teleport(returnTransition);

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, "minecraft", "overworld");

                // Should be near the original portal
                assertNearPosition(helper, player,
                        base.getX() + 1.5, base.getY() + 1, base.getZ() + 0.5,
                        10.0, "back in overworld near original portal");

                helper.succeed();
            });
        });
    }

    @GameTest(maxTicks = 1000)
    public void overworldEndPortalGoesToEnd(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        ServerLevel overworld = (ServerLevel) player.level();

        // Place end portal block near player
        BlockPos portalPos = player.blockPosition().offset(5, 0, 0);
        overworld.setBlock(portalPos, Blocks.END_PORTAL.defaultBlockState(), 2);

        // Use getPortalDestination directly
        TeleportTransition transition = ((EndPortalBlock) Blocks.END_PORTAL)
                .getPortalDestination(overworld, player, portalPos);
        if (transition == null) {
            helper.fail("End portal getPortalDestination returned null from overworld");
            return;
        }

        assertDimensionKey(helper, transition.newLevel().dimension(), "minecraft", "the_end",
                "end portal destination from overworld");

        player.teleport(transition);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, "minecraft", "the_end");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 2000)
    public void creativeNetherPortalGoesToCreativeNether(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ensureAdvancementsDir(server);
        createWorld(helper, player, NETHER_TEST_WORLD);

        // Go to custom world first
        teleportToWorld(helper, player, NETHER_TEST_WORLD);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, NETHER_TEST_WORLD);

            ServerLevel customLevel = (ServerLevel) player.level();

            // Build nether portal in custom world
            BlockPos base = player.blockPosition().offset(5, 0, 0);
            buildNetherPortal(customLevel, base);
            BlockPos portalPos = base.offset(1, 1, 0);

            // Use getPortalDestination - mixin should route to <world>_nether
            TeleportTransition transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                    .getPortalDestination(customLevel, player, portalPos);
            if (transition == null) {
                helper.fail("Nether portal getPortalDestination returned null from custom world");
                return;
            }

            assertDimensionKey(helper, transition.newLevel().dimension(), NAMESPACE, NETHER_TEST_WORLD + "_nether",
                    "nether portal destination from custom world");

            player.teleport(transition);

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, NAMESPACE, NETHER_TEST_WORLD + "_nether");

                // Find the portal in <world>_nether and go back
                ServerLevel customNether = (ServerLevel) player.level();
                BlockPos netherPortal = findNearbyPortalBlock(customNether, player.blockPosition(), 10);
                if (netherPortal == null) {
                    helper.fail("No nether portal block found near player in custom_nether");
                    return;
                }

                TeleportTransition returnTransition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                        .getPortalDestination(customNether, player, netherPortal);
                if (returnTransition == null) {
                    helper.fail("Nether portal getPortalDestination returned null from custom_nether");
                    return;
                }

                assertDimensionKey(helper, returnTransition.newLevel().dimension(), NAMESPACE, NETHER_TEST_WORLD,
                        "nether portal return destination from custom_nether");

                player.teleport(returnTransition);

                helper.runAfterDelay(10, () -> {
                    assertInDimension(helper, player, NAMESPACE, NETHER_TEST_WORLD);
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(maxTicks = 1200)
    public void creativeEndPortalGoesToCreativeEnd(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ensureAdvancementsDir(server);
        createWorld(helper, player, END_TEST_WORLD);

        // Go to custom world first
        teleportToWorld(helper, player, END_TEST_WORLD);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, END_TEST_WORLD);

            ServerLevel customLevel = (ServerLevel) player.level();

            // Place end portal block in custom world
            BlockPos portalPos = player.blockPosition().offset(5, 0, 0);
            customLevel.setBlock(portalPos, Blocks.END_PORTAL.defaultBlockState(), 2);

            // Use getPortalDestination - mixin should route to <world>_end
            TeleportTransition transition = ((EndPortalBlock) Blocks.END_PORTAL)
                    .getPortalDestination(customLevel, player, portalPos);
            if (transition == null) {
                helper.fail("End portal getPortalDestination returned null from custom world");
                return;
            }

            assertDimensionKey(helper, transition.newLevel().dimension(), NAMESPACE, END_TEST_WORLD + "_end",
                    "end portal destination from custom world");

            player.teleport(transition);

            helper.runAfterDelay(10, () -> {
                assertInDimension(helper, player, NAMESPACE, END_TEST_WORLD + "_end");
                helper.succeed();
            });
        });
    }

    // --- Helpers ---

    private static void buildNetherPortal(ServerLevel level, BlockPos base) {
        // Build 4-wide, 5-tall obsidian frame (portal in XY plane, walk through along Z)
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                if (x == 0 || x == 3 || y == 0 || y == 4) {
                    level.setBlock(base.offset(x, y, 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
                }
            }
        }
        // Place portal blocks inside the frame
        BlockState portalState = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                level.setBlock(base.offset(x, y, 0), portalState, 2);
            }
        }
    }

    private static BlockPos findNearbyPortalBlock(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static void assertDimensionKey(GameTestHelper helper, ResourceKey<Level> actual,
                                           String expectedNamespace, String expectedPath, String context) {
        String ns = actual.identifier().getNamespace();
        String path = actual.identifier().getPath();
        if (!expectedNamespace.equals(ns) || !expectedPath.equals(path)) {
            helper.fail(context + ": expected " + expectedNamespace + ":" + expectedPath
                    + ", but got " + ns + ":" + path);
        }
    }
}
