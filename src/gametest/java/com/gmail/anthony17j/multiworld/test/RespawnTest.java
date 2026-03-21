package com.gmail.anthony17j.multiworld.test;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.File;

import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;
import static com.gmail.anthony17j.multiworld.test.TestAssertions.*;

public class RespawnTest {

    private static final double BED_TOLERANCE = 5.0;
    private static final double SPAWN_ZONE_TOLERANCE = 40.0;
    private static final String RESPawn_WORLD = SHARED_TEST_WORLD;
    private static final String CREATIVE_WORLD_PREFIX = "creative_respawn_world";
    private static final long FIXED_SEED_SPAWN_TEST_SEED = -2831322041033427943L;
    private static final String FIXED_SEED_SPAWN_WORLD = "seed_spawn_world";

    // ========================
    // Overworld respawn tests
    // ========================

    @GameTest(maxTicks = 3000)
    public void overworldRespawnSequence(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        String worldFolder = server.getWorldPath(LevelResource.ROOT).toString();
        new File(worldFolder + "/advancements").mkdirs();

        ServerLevel overworld = server.overworld();

        // --- 1. Kill player with no spawn set -> respawn in overworld ---
        player = killAndRespawn(player, server);
        assertInDimension(helper, player, "minecraft", "overworld");

        // --- 2. Place bed, set spawn, kill -> respawn at bed ---
        BlockPos bedPos = player.blockPosition().offset(3, 0, 0);
        placeBedWithPlatform(overworld, bedPos, Direction.NORTH);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(Level.OVERWORLD, bedPos, 0f, 0f), false), false);

        player = killAndRespawn(player, server);
        assertInDimension(helper, player, "minecraft", "overworld");
        assertNearPosition(helper, player, bedPos.getX(), bedPos.getY(), bedPos.getZ(),
                BED_TOLERANCE, "respawn at bed");

        // --- 3. Destroy bed, kill -> respawn in overworld (world spawn) ---
        overworld.removeBlock(bedPos, false);
        overworld.removeBlock(bedPos.north(), false);

        player = killAndRespawn(player, server);
        assertInDimension(helper, player, "minecraft", "overworld");

        // --- 4. Go to nether, kill -> respawn in overworld ---
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) { helper.fail("Nether not found"); return; }
        teleportToDimension(helper, player, server, "minecraft:the_nether");
        assertInDimension(helper, player, "minecraft", "the_nether");

        player = killAndRespawn(player, server);
        assertInDimension(helper, player, "minecraft", "overworld");

        // --- 5. Go to nether, set respawn anchor, kill -> respawn at anchor in nether ---
        teleportToDimension(helper, player, server, "minecraft:the_nether");
        assertInDimension(helper, player, "minecraft", "the_nether");

        BlockPos anchorPos = player.blockPosition().offset(3, 0, 0);
        placeRespawnAnchorWithPlatform(nether, anchorPos);
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(Level.NETHER, anchorPos, 0f, 0f), false), false);

        player = killAndRespawn(player, server);
        assertInDimension(helper, player, "minecraft", "the_nether");
        assertNearPosition(helper, player, anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(),
                BED_TOLERANCE, "respawn at nether anchor");

        // --- 6. Remove anchor, go to end, kill -> respawn in overworld ---
        nether.removeBlock(anchorPos, false);
        player.setRespawnPosition(null, false);

        teleportToDimension(helper, player, server, "minecraft:the_end");
        assertInDimension(helper, player, "minecraft", "the_end");

        player = killAndRespawn(player, server);
        assertInDimension(helper, player, "minecraft", "overworld");

        helper.succeed();
    }

    // ========================
    // Custom world respawn tests
    // ========================

    @GameTest(maxTicks = 4000)
        public void customWorldRespawnSequence(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ensureAdvancementsDir(server);
        createWorld(helper, player, RESPawn_WORLD);

        // Go to custom world
        teleportToWorld(helper, player, RESPawn_WORLD);

        final ServerPlayer[] ref = {player};
        helper.runAfterDelay(10, () -> {
            ServerPlayer current = ref[0];
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD);

            ResourceKey<Level> customKey = current.level().dimension();
            ServerLevel customLevel = (ServerLevel) current.level();

            // --- 1. Kill in custom world -> respawn in custom world ---
            current = killAndRespawn(current, server);
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD);
            assertRespawnNearWorldSpawn(helper, current, customLevel, "custom world spawn after death");

            // --- 2. Place bed in custom world, set spawn, kill -> respawn at bed ---
            BlockPos bedPos = current.blockPosition().offset(3, 0, 0);
            placeBedWithPlatform(customLevel, bedPos, Direction.NORTH);
            current.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(customKey, bedPos, 0f, 0f), false), false);

            current = killAndRespawn(current, server);
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD);
            assertRespawnNearWorldSpawn(helper, current, customLevel, "custom world fallback spawn after broken bed");
            assertNearPosition(helper, current, bedPos.getX(), bedPos.getY(), bedPos.getZ(),
                BED_TOLERANCE, "respawn at custom-world bed");

            // --- 3. Destroy bed, kill -> respawn in custom world (world spawn) ---
            customLevel.removeBlock(bedPos, false);
            customLevel.removeBlock(bedPos.north(), false);

            current = killAndRespawn(current, server);
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD);

            // --- 4. Go to custom_nether, kill -> respawn in custom world ---
            teleportToDimension(helper, current, server, NAMESPACE + ":" + RESPawn_WORLD + "_nether");
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD + "_nether");

            current = killAndRespawn(current, server);
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD);

            // --- 5. Go to custom_nether, set respawn anchor, kill -> respawn at anchor ---
            teleportToDimension(helper, current, server, NAMESPACE + ":" + RESPawn_WORLD + "_nether");
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD + "_nether");

            BlockPos anchorPos = current.blockPosition().offset(3, 0, 0);
            ServerLevel customNetherLevel = (ServerLevel) current.level();
            placeRespawnAnchorWithPlatform(customNetherLevel, anchorPos);
            current.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(customNetherLevel.dimension(), anchorPos, 0f, 0f), false), false);

            current = killAndRespawn(current, server);
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD + "_nether");
            assertNearPosition(helper, current, anchorPos.getX(), anchorPos.getY(), anchorPos.getZ(),
                BED_TOLERANCE, "respawn at custom_nether anchor");

            // --- 6. Remove anchor, go to custom_end, kill -> respawn in custom world ---
            customNetherLevel.removeBlock(anchorPos, false);
            current.setRespawnPosition(null, false);

            teleportToDimension(helper, current, server, NAMESPACE + ":" + RESPawn_WORLD + "_end");
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD + "_end");

            current = killAndRespawn(current, server);
            assertInDimension(helper, current, NAMESPACE, RESPawn_WORLD);

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 1200)
    public void creativeWorldRespawnUsesCreativeSpawn(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ensureAdvancementsDir(server);
        String creativeWorld = CREATIVE_WORLD_PREFIX + "_" + Long.toUnsignedString(System.nanoTime(), 36);

        createWorld(helper, player, creativeWorld, "flat", "creative");

        teleportToWorld(helper, player, creativeWorld);
        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, creativeWorld);
            ServerLevel creative = (ServerLevel) player.level();

            BlockPos creativeSpawn = creative.getRespawnData().pos();
            if (creativeSpawn.getX() != 0 || creativeSpawn.getZ() != 0) {
                helper.fail("Creative world spawn must stay at X/Z 0,0 but was " + creativeSpawn);
                return;
            }
            if (creativeSpawn.getY() < 63) {
                helper.fail("Creative world spawn Y must be at least 63 but was " + creativeSpawn);
                return;
            }

            ServerPlayer respawned = killAndRespawn(player, server);
            assertInDimension(helper, respawned, NAMESPACE, creativeWorld);
            assertRespawnNearWorldSpawn(helper, respawned, creative, "creative world spawn after death");

            helper.succeed();
        });
    }

    @GameTest(maxTicks = 1600)
    public void fixedSeedWorldSpawnMatchesExpectedZone(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ensureAdvancementsDir(server);

        String fixedSeedWorldName = FIXED_SEED_SPAWN_WORLD + "_" + Long.toUnsignedString(System.nanoTime(), 36);

        createWorld(helper, player, fixedSeedWorldName, FIXED_SEED_SPAWN_TEST_SEED);
        teleportToWorld(helper, player, fixedSeedWorldName);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, fixedSeedWorldName);
            assertNearPosition(helper, player, 395, 70, 276, 35.0, "fixed-seed initial spawn zone");

            ServerPlayer respawned = killAndRespawn(player, server);
            assertInDimension(helper, respawned, NAMESPACE, fixedSeedWorldName);
            assertNearPosition(helper, respawned, 395, 70, 276, 35.0, "fixed-seed respawn zone");

            helper.succeed();
        });
    }

    // --- Helpers ---

    private static void teleportToDimension(GameTestHelper helper, ServerPlayer player, MinecraftServer server, String dimension) {
        ServerLevel targetLevel = null;
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) {
                targetLevel = level;
                break;
            }
        }
        if (targetLevel == null) {
            helper.fail("Dimension not found: " + dimension);
            return;
        }
        Vec3 safePos = new Vec3(0, 64, 0);
        player.teleport(new TeleportTransition(targetLevel,
                safePos, Vec3.ZERO,
                player.getYRot(), player.getXRot(), TeleportTransition.PLACE_PORTAL_TICKET));
        // EmbeddedChannel doesn't process position packets, so manually set position
        player.setPos(safePos);
    }

    private static ServerPlayer killAndRespawn(ServerPlayer player, MinecraftServer server) {
        player.kill((ServerLevel) player.level());
        ServerPlayer respawned = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        ServerPlayer listed = server.getPlayerList().getPlayer(respawned.getUUID());
        return listed != null ? listed : respawned;
    }

    private static void assertRespawnNearWorldSpawn(GameTestHelper helper, ServerPlayer player, ServerLevel level, String context) {
        BlockPos spawn = level.getRespawnData().pos();
        assertNearPosition(helper, player, spawn.getX(), spawn.getY(), spawn.getZ(), SPAWN_ZONE_TOLERANCE, context);
    }

    private static void placeBedWithPlatform(ServerLevel level, BlockPos footPos, Direction facing) {
        BlockPos headPos = footPos.relative(facing);
        // Place solid ground under and around the bed for valid spawn
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(footPos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(headPos.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 3);
                // Clear space above for spawn
                level.setBlock(footPos.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(footPos.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(headPos.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(headPos.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Place the bed
        level.setBlock(footPos, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(HorizontalDirectionalBlock.FACING, facing), 3);
        level.setBlock(headPos, Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(HorizontalDirectionalBlock.FACING, facing), 3);
    }

    private static void placeRespawnAnchorWithPlatform(ServerLevel level, BlockPos pos) {
        // Place solid ground under and around the anchor for valid spawn
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(pos.offset(dx, -1, dz), Blocks.NETHERRACK.defaultBlockState(), 3);
                // Clear space above for spawn
                level.setBlock(pos.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.offset(dx, 1, dz), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.offset(dx, 2, dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        // Place the anchor with max charges
        level.setBlock(pos, Blocks.RESPAWN_ANCHOR.defaultBlockState()
                .setValue(RespawnAnchorBlock.CHARGE, 4), 3);
    }
}
