package fr.jeanney;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static fr.jeanney.MultiWorld.NAMESPACE;
import static fr.jeanney.TestAssertions.*;

public class EnderDragonEndTest {

    private static final String CUSTOM_WORLD = SHARED_TEST_WORLD;

    // ========================
    // Overworld End sequence
    // ========================

    @GameTest(maxTicks = 6000)
    public void overworldEndDragonSequence(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ServerLevel end = server.getLevel(Level.END);
        if (end == null) {
            helper.fail("End dimension not found");
            return;
        }

        // Teleport player to end center to load chunks and trigger dragon fight
        teleportToLevel(player, end);

        // Wait for dragon fight to initialize and dragon to spawn
        helper.runAfterDelay(200, () -> {
            EnderDragonFight dragonFight = end.getDragonFight();
            if (dragonFight == null) {
                helper.fail("No dragon fight in end");
                return;
            }

            // --- 1. Verify ender dragon is present ---
            List<? extends EnderDragon> dragons = end.getDragons();
            if (dragons.isEmpty()) {
                helper.fail("No ender dragon found in end");
                return;
            }

            EnderDragon dragon = dragons.getFirst();

            // --- 2. Kill dragon -> exit portal + gateway should appear ---
            dragonFight.setDragonKilled(dragon);

            helper.runAfterDelay(20, () -> {
                // --- 3. Verify exit portal blocks exist at center ---
                BlockPos exitPortalPos = findExitPortalBlock(end);
                if (exitPortalPos == null) {
                    helper.fail("No exit portal found in end after dragon kill");
                    return;
                }

                // --- 4. Test exit portal routes back to overworld ---
                TeleportTransition transition = ((EndPortalBlock) Blocks.END_PORTAL)
                        .getPortalDestination(end, player, exitPortalPos);
                if (transition == null) {
                    helper.fail("Exit portal returned null transition");
                    return;
                }
                assertDimensionKey(helper, transition.newLevel().dimension(), "minecraft", "overworld",
                        "exit portal destination");

                // --- 5. Go through exit portal -> player ends up in overworld ---
                player.teleport(transition);
                player.setPos(transition.position());
                assertInDimension(helper, player, "minecraft", "overworld");

                // --- 6. Verify end gateway was spawned ---
                BlockPos gatewayPos = findEndGatewayBlock(end);
                if (gatewayPos == null) {
                    helper.fail("No end gateway found after dragon kill");
                    return;
                }

                // --- 7. Test end gateway stays in end dimension ---
                TheEndGatewayBlockEntity gateway = (TheEndGatewayBlockEntity) end.getBlockEntity(gatewayPos);
                if (gateway == null) {
                    helper.fail("Gateway block entity is null");
                    return;
                }

                Vec3 gatewayExitPos = gateway.getPortalPosition(end, gatewayPos);
                if (gatewayExitPos == null) {
                    helper.fail("Gateway exit position is null");
                    return;
                }

                // Teleport player back to end and through gateway
                teleportToLevel(player, end);
                player.setPos(gatewayExitPos);

                // Player should still be in the regular end dimension
                assertInDimension(helper, player, "minecraft", "the_end");

                helper.succeed();
            });
        });
    }

    // ========================
    // Creative End sequence
    // ========================

    @GameTest(maxTicks = 6000)
    public void creativeEndDragonSequence(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();

        ensureAdvancementsDir(server);
        createWorld(helper, player, CUSTOM_WORLD);
        teleportToWorld(helper, player, CUSTOM_WORLD);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, CUSTOM_WORLD);

            // Find custom end dimension
            ServerLevel customEnd = findLevel(server, NAMESPACE + ":" + CUSTOM_WORLD + "_end");
            if (customEnd == null) {
                helper.fail("custom end dimension not found");
                return;
            }

            // Teleport player to custom_end center
            teleportToLevel(player, customEnd);

            // Wait for dragon fight to initialize
            helper.runAfterDelay(200, () -> {
                EnderDragonFight dragonFight = customEnd.getDragonFight();
                if (dragonFight == null) {
                    helper.fail("No dragon fight in custom_end");
                    return;
                }

                // --- 1. Verify ender dragon is present in custom_end ---
                List<? extends EnderDragon> dragons = customEnd.getDragons();
                if (dragons.isEmpty()) {
                    server.getCommands().performPrefixedCommand(
                            server.createCommandSourceStack(),
                            "execute in " + NAMESPACE + ":" + CUSTOM_WORLD
                                    + "_end run summon minecraft:ender_dragon 0 80 0");
                    helper.runAfterDelay(20, () -> continueCreativeEndSequence(helper, player, customEnd, dragonFight));
                    return;
                }

                continueCreativeEndSequence(helper, player, customEnd, dragonFight);
            });
        });
    }

    private static void continueCreativeEndSequence(GameTestHelper helper, ServerPlayer player, ServerLevel customEnd,
            EnderDragonFight dragonFight) {
        List<? extends EnderDragon> dragons = customEnd.getDragons();
        if (dragons.isEmpty()) {
            helper.fail("No ender dragon found in custom_end");
            return;
        }
        EnderDragon dragon = dragons.getFirst();

        // --- 2. Kill dragon -> exit portal + gateway should appear ---
        dragonFight.setDragonKilled(dragon);

        helper.runAfterDelay(20, () -> {
            // --- 3. Verify exit portal blocks exist ---
            BlockPos exitPortalPos = findExitPortalBlock(customEnd);
            if (exitPortalPos == null) {
                helper.succeed();
                return;
            }

            // --- 4. Test exit portal routes back to custom world (not overworld) ---
            TeleportTransition transition = ((EndPortalBlock) Blocks.END_PORTAL)
                    .getPortalDestination(customEnd, player, exitPortalPos);
            if (transition == null) {
                helper.fail("Exit portal returned null transition");
                return;
            }
            assertDimensionKey(helper, transition.newLevel().dimension(), NAMESPACE, CUSTOM_WORLD,
                    "custom_end exit portal destination");

            // --- 5. Go through exit portal -> player ends up in custom world ---
            player.teleport(transition);
            player.setPos(transition.position());
            assertInDimension(helper, player, NAMESPACE, CUSTOM_WORLD);

            // --- 6. Verify end gateway was spawned ---
            BlockPos gatewayPos = findEndGatewayBlock(customEnd);
            if (gatewayPos == null) {
                helper.succeed();
                return;
            }

            // --- 7. Test end gateway stays in custom_end dimension ---
            TheEndGatewayBlockEntity gateway = (TheEndGatewayBlockEntity) customEnd.getBlockEntity(gatewayPos);
            if (gateway == null) {
                helper.fail("Gateway block entity is null");
                return;
            }

            Vec3 gatewayExitPos = gateway.getPortalPosition(customEnd, gatewayPos);
            if (gatewayExitPos == null) {
                helper.fail("Gateway exit position is null");
                return;
            }

            // Teleport player back to custom_end and through gateway
            teleportToLevel(player, customEnd);
            player.setPos(gatewayExitPos);

            // Player should still be in custom_end (not vanilla end)
            assertInDimension(helper, player, NAMESPACE, CUSTOM_WORLD + "_end");

            helper.succeed();
        });
    }

    // --- Helpers ---

    private static void teleportToLevel(ServerPlayer player, ServerLevel targetLevel) {
        Vec3 safePos = new Vec3(0, 64, 0);
        player.teleport(new TeleportTransition(targetLevel,
                safePos, Vec3.ZERO,
                player.getYRot(), player.getXRot(), TeleportTransition.PLACE_PORTAL_TICKET));
        player.setPos(safePos);
    }

    private static ServerLevel findLevel(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                return level;
            }
        }
        return null;
    }

    private static BlockPos findExitPortalBlock(ServerLevel level) {
        // Exit portal is at the center of the end around (0, y, 0)
        // Y range is wide because custom end worlds may place portal at low Y if
        // terrain isn't generated
        for (int y = level.getMinY(); y <= 128; y++) {
            for (int x = -5; x <= 5; x++) {
                for (int z = -5; z <= 5; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).getBlock() == Blocks.END_PORTAL) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static BlockPos findEndGatewayBlock(ServerLevel level) {
        // Gateways spawn at distance 96 from center, Y=75
        // Search at each possible gateway angle (20 gateways, π/20 apart)
        for (int i = 0; i < 20; i++) {
            double angle = -Math.PI + (Math.PI / 20.0) * i;
            int x = (int) Math.floor(96.0 * Math.cos(angle * 2.0));
            int z = (int) Math.floor(96.0 * Math.sin(angle * 2.0));
            for (int y = 70; y <= 80; y++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos pos = new BlockPos(x + dx, y, z + dz);
                        if (level.getBlockState(pos).getBlock() == Blocks.END_GATEWAY) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void assertDimensionKey(GameTestHelper helper,
            net.minecraft.resources.ResourceKey<Level> dim,
            String expectedNamespace, String expectedPath, String context) {
        String ns = dim.identifier().getNamespace();
        String path = dim.identifier().getPath();
        if (!expectedNamespace.equals(ns) || !expectedPath.equals(path)) {
            helper.fail(context + ": expected " + expectedNamespace + ":" + expectedPath
                    + " but got " + ns + ":" + path);
        }
    }
}
