package fr.jeanney;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;

import static fr.jeanney.MultiWorld.NAMESPACE;
import static fr.jeanney.TestAssertions.*;

public class EntityPortalTest {

    private static final String WORLD_ITEM_NETHER = SHARED_TEST_WORLD;
    private static final String WORLD_FALLING_NETHER = SHARED_TEST_WORLD;
    private static final String WORLD_ITEM_END = SHARED_TEST_WORLD;
    private static final String WORLD_FALLING_END = SHARED_TEST_WORLD;

    // --- Overworld portals with entities ---

    @GameTest(maxTicks = 1000)
    public void itemEntityGoesToNetherFromOverworld(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();

        BlockPos base = new BlockPos(0, 64, 0);
        buildNetherPortal(overworld, base);
        BlockPos portalPos = base.offset(1, 1, 0);

        // Drop an item inside the portal
        ItemEntity item = new ItemEntity(overworld, portalPos.getX() + 0.5, portalPos.getY(), portalPos.getZ() + 0.5,
                new ItemStack(Items.DIAMOND, 1));
        overworld.addFreshEntity(item);

        TeleportTransition transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                .getPortalDestination(overworld, item, portalPos);
        if (transition == null) {
            helper.fail("Nether portal returned null for ItemEntity from overworld");
            return;
        }

        assertDimensionKey(helper, transition.newLevel().dimension(), "minecraft", "the_nether",
                "item entity nether portal from overworld");
        helper.succeed();
    }

    @GameTest(maxTicks = 1000)
    public void fallingBlockGoesToNetherFromOverworld(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();

        BlockPos base = new BlockPos(0, 64, 0);
        buildNetherPortal(overworld, base);
        BlockPos portalPos = base.offset(1, 1, 0);

        // Create a falling sand entity inside the portal
        FallingBlockEntity sand = FallingBlockEntity.fall(overworld, portalPos, Blocks.SAND.defaultBlockState());

        TeleportTransition transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                .getPortalDestination(overworld, sand, portalPos);
        if (transition == null) {
            helper.fail("Nether portal returned null for FallingBlockEntity from overworld");
            return;
        }

        assertDimensionKey(helper, transition.newLevel().dimension(), "minecraft", "the_nether",
                "falling block nether portal from overworld");
        helper.succeed();
    }

    @GameTest(maxTicks = 1000)
    public void itemEntityGoesToEndFromOverworld(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();

        BlockPos portalPos = new BlockPos(0, 64, 10);
        overworld.setBlock(portalPos, Blocks.END_PORTAL.defaultBlockState(), 2);

        ItemEntity item = new ItemEntity(overworld, portalPos.getX() + 0.5, portalPos.getY() + 0.3,
                portalPos.getZ() + 0.5,
                new ItemStack(Items.DIAMOND, 1));
        overworld.addFreshEntity(item);

        TeleportTransition transition = ((EndPortalBlock) Blocks.END_PORTAL)
                .getPortalDestination(overworld, item, portalPos);
        if (transition == null) {
            helper.fail("End portal returned null for ItemEntity from overworld");
            return;
        }

        assertDimensionKey(helper, transition.newLevel().dimension(), "minecraft", "the_end",
                "item entity end portal from overworld");
        helper.succeed();
    }

    @GameTest(maxTicks = 1000)
    public void fallingBlockGoesToEndFromOverworld(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();

        BlockPos portalPos = new BlockPos(0, 64, 20);
        overworld.setBlock(portalPos, Blocks.END_PORTAL.defaultBlockState(), 2);

        FallingBlockEntity sand = FallingBlockEntity.fall(overworld, portalPos.above(),
                Blocks.SAND.defaultBlockState());

        TeleportTransition transition = ((EndPortalBlock) Blocks.END_PORTAL)
                .getPortalDestination(overworld, sand, portalPos);
        if (transition == null) {
            helper.fail("End portal returned null for FallingBlockEntity from overworld");
            return;
        }

        assertDimensionKey(helper, transition.newLevel().dimension(), "minecraft", "the_end",
                "falling block end portal from overworld");
        helper.succeed();
    }

    // --- Custom world portals with entities ---

    @GameTest(maxTicks = 2000)
    public void itemEntityGoesToCreativeNetherFromCreative(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ensureAdvancementsDir(server);
        createWorld(helper, player, WORLD_ITEM_NETHER);

        teleportToWorld(helper, player, WORLD_ITEM_NETHER);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, WORLD_ITEM_NETHER);

            ServerLevel customLevel = (ServerLevel) player.level();
            BlockPos base = player.blockPosition().offset(5, 0, 0);
            buildNetherPortal(customLevel, base);
            BlockPos portalPos = base.offset(1, 1, 0);

            ItemEntity item = new ItemEntity(customLevel, portalPos.getX() + 0.5, portalPos.getY(),
                    portalPos.getZ() + 0.5,
                    new ItemStack(Items.DIAMOND, 1));
            customLevel.addFreshEntity(item);

            TeleportTransition transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                    .getPortalDestination(customLevel, item, portalPos);
            if (transition == null) {
                helper.fail("Nether portal returned null for ItemEntity from custom world");
                return;
            }

            assertDimensionKey(helper, transition.newLevel().dimension(), NAMESPACE, WORLD_ITEM_NETHER + "_nether",
                    "item entity nether portal from custom world");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 2000)
    public void fallingBlockGoesToCreativeNetherFromCreative(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ensureAdvancementsDir(server);
        createWorld(helper, player, WORLD_FALLING_NETHER);

        teleportToWorld(helper, player, WORLD_FALLING_NETHER);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, WORLD_FALLING_NETHER);

            ServerLevel customLevel = (ServerLevel) player.level();
            BlockPos base = player.blockPosition().offset(5, 0, 0);
            buildNetherPortal(customLevel, base);
            BlockPos portalPos = base.offset(1, 1, 0);

            FallingBlockEntity sand = FallingBlockEntity.fall(customLevel, portalPos, Blocks.SAND.defaultBlockState());

            TeleportTransition transition = ((NetherPortalBlock) Blocks.NETHER_PORTAL)
                    .getPortalDestination(customLevel, sand, portalPos);
            if (transition == null) {
                helper.fail("Nether portal returned null for FallingBlockEntity from custom world");
                return;
            }

            assertDimensionKey(helper, transition.newLevel().dimension(), NAMESPACE, WORLD_FALLING_NETHER + "_nether",
                    "falling block nether portal from custom world");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 2000)
    public void itemEntityGoesToCreativeEndFromCreative(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ensureAdvancementsDir(server);
        createWorld(helper, player, WORLD_ITEM_END);

        teleportToWorld(helper, player, WORLD_ITEM_END);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, WORLD_ITEM_END);

            ServerLevel customLevel = (ServerLevel) player.level();
            BlockPos portalPos = player.blockPosition().offset(5, 0, 0);
            customLevel.setBlock(portalPos, Blocks.END_PORTAL.defaultBlockState(), 2);

            ItemEntity item = new ItemEntity(customLevel, portalPos.getX() + 0.5, portalPos.getY() + 0.3,
                    portalPos.getZ() + 0.5,
                    new ItemStack(Items.DIAMOND, 1));
            customLevel.addFreshEntity(item);

            TeleportTransition transition = ((EndPortalBlock) Blocks.END_PORTAL)
                    .getPortalDestination(customLevel, item, portalPos);
            if (transition == null) {
                helper.fail("End portal returned null for ItemEntity from custom world");
                return;
            }

            assertDimensionKey(helper, transition.newLevel().dimension(), NAMESPACE, WORLD_ITEM_END + "_end",
                    "item entity end portal from custom world");
            helper.succeed();
        });
    }

    @GameTest(maxTicks = 2000)
    public void fallingBlockGoesToCreativeEndFromCreative(GameTestHelper helper) {
        ServerPlayer player = createMockServerPlayer(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ensureAdvancementsDir(server);
        createWorld(helper, player, WORLD_FALLING_END);

        teleportToWorld(helper, player, WORLD_FALLING_END);

        helper.runAfterDelay(10, () -> {
            assertInDimension(helper, player, NAMESPACE, WORLD_FALLING_END);

            ServerLevel customLevel = (ServerLevel) player.level();
            BlockPos portalPos = player.blockPosition().offset(5, 0, 10);
            customLevel.setBlock(portalPos, Blocks.END_PORTAL.defaultBlockState(), 2);

            FallingBlockEntity sand = FallingBlockEntity.fall(customLevel, portalPos.above(),
                    Blocks.SAND.defaultBlockState());

            TeleportTransition transition = ((EndPortalBlock) Blocks.END_PORTAL)
                    .getPortalDestination(customLevel, sand, portalPos);
            if (transition == null) {
                helper.fail("End portal returned null for FallingBlockEntity from custom world");
                return;
            }

            assertDimensionKey(helper, transition.newLevel().dimension(), NAMESPACE, WORLD_FALLING_END + "_end",
                    "falling block end portal from custom world");
            helper.succeed();
        });
    }

    // --- Helpers ---

    private static void buildNetherPortal(ServerLevel level, BlockPos base) {
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                if (x == 0 || x == 3 || y == 0 || y == 4) {
                    level.setBlock(base.offset(x, y, 0), Blocks.OBSIDIAN.defaultBlockState(), 3);
                }
            }
        }
        BlockState portalState = Blocks.NETHER_PORTAL.defaultBlockState()
                .setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
        for (int x = 1; x <= 2; x++) {
            for (int y = 1; y <= 3; y++) {
                level.setBlock(base.offset(x, y, 0), portalState, 2);
            }
        }
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
