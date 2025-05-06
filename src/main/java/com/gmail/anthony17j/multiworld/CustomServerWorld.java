package com.gmail.anthony17j.multiworld;

import com.gmail.anthony17j.multiworld.mixin.MinecraftServerMixin;
import com.google.common.collect.ImmutableList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.village.ZombieSiegeManager;
import net.minecraft.world.WanderingTraderManager;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.LevelProperties;
import net.minecraft.world.spawner.CatSpawner;
import net.minecraft.world.spawner.PatrolSpawner;
import net.minecraft.world.spawner.PhantomSpawner;
import java.util.Objects;

import static com.gmail.anthony17j.multiworld.MultiWorld.namespace;

public class CustomServerWorld extends ServerWorld {

//    ServerChunkManager chunkManager;

    public CustomServerWorld(MinecraftServer server, RegistryKey<World> registryKey, DimensionOptions options, LevelProperties levelProperties, long seed) {
        super(
                server,
                Util.getMainWorkerExecutor(),
                ((MinecraftServerMixin) server).getSession(),
                levelProperties,
                registryKey,
                options,
                VoidWorldProgressListener.INSTANCE,
                false,
                BiomeAccess.hashSeed(seed),
                ImmutableList.of(
                        new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new ZombieSiegeManager(), new WanderingTraderManager(levelProperties)
                ),
                true,
                null
        );
//        this.chunkManager = new ServerChunkManager(
//                this,
//                ((MinecraftServerMixin) server).getSession(),
//                server.getDataFixer(),
//                server.getStructureTemplateManager(),
//                Util.getMainWorkerExecutor(),
//                options.chunkGenerator(),
//                server.getPlayerManager().getViewDistance(),
//                server.getPlayerManager().getSimulationDistance(),
//                server.syncChunkWrites(),
//                VoidWorldProgressListener.INSTANCE,
//                (ServerWorldAccessor) this.getEntityManager(),
//                () -> server.getOverworld().getPersistentStateManager()
//        );
    }

//    protected CustomServerWorld(MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey<World> worldKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List<SpecialSpawner> spawners, boolean shouldTickTime, @Nullable RandomSequencesState randomSequencesState, ServerChunkManager chunkManager, ServerChunkManager chunkManager1) {
//        super(server, workerExecutor, session, properties, worldKey, dimensionOptions, worldGenerationProgressListener, debugWorld, seed, spawners, shouldTickTime, randomSequencesState);
//        this.chunkManager = chunkManager1;
//    }

//    public long getSeed() {
//        return ((CustomServerWorldProperties)this.properties).seed;
//    }

    public RegistryKey<World> getMockRegistryKey() {
        RegistryKey<World> registryKey = super.getRegistryKey();
        String path = registryKey.getValue().getPath();
        if (path.endsWith("_nether")) {
            return World.NETHER;
        } else if (path.endsWith("_end")) {
            return World.END;
        }
        return World.OVERWORLD;
    }

    public interface Constructor {
        CustomServerWorld create(MinecraftServer server, RegistryKey<World> registryKey, DimensionOptions options, LevelProperties levelProperties, long seed);
    }

    public static RegistryKey<World> getMockRegistryKey(World world) {
        if (world instanceof CustomServerWorld) {
            return ((CustomServerWorld) world).getMockRegistryKey();
        }
        return world.getRegistryKey();
    }

    public static RegistryKey<World> getMockRegistryKey(RegistryKey<World> world) {
        Identifier worldId = world.getValue();
        if (namespace.equals(worldId.getNamespace())) {
            String path = worldId.getPath();

            if (path.endsWith("_nether")) {
                return World.NETHER;
            } else if (path.endsWith("_end")) {
                return World.END;
            } else {
                return World.OVERWORLD;
            }
        }
        return world;
    }

    public static boolean isCustomEndWorld(RegistryKey<World> key) {
        return key.getValue().getPath().endsWith("_end") && Objects.equals(key.getValue().getNamespace(), namespace);
    }

    public static boolean isCustomNetherWorld(RegistryKey<World> key) {
        return key.getValue().getPath().endsWith("_nether") && Objects.equals(key.getValue().getNamespace(), namespace);
    }

    public static boolean isCustomOverworldWorld(RegistryKey<World> key) {
        return Objects.equals(key.getValue().getNamespace(), namespace) && !key.getValue().getPath().endsWith("_nether") && !key.getValue().getPath().endsWith("_end");
    }

    public static RegistryKey<World> mapRegularWorldToCustomWorld(ServerPlayerEntity player, RegistryKey<World> key) {
        RegistryKey<World> playerWorldKey = player.getWorld().getRegistryKey();
        MultiWorld.LOGGER.info("Player world key: {}", playerWorldKey);
        MultiWorld.LOGGER.info("key : {}", key);
        if (!Objects.equals(playerWorldKey.getValue().getNamespace(), namespace)) {
            MultiWorld.LOGGER.info("Player is not in a custom world, returning original key");
            return key;
        }
        String path = playerWorldKey.getValue().getPath();
        if (key == World.OVERWORLD) {
            path = path.substring(0, path.length() - 7);
        } else if (path.endsWith("_end")) {
            path = path.substring(0, path.length() - 4);
        }
        if (key == World.NETHER) {
            path = path + "_nether";
        } else if (key == World.END) {
            path = path + "_end";
        }
        MultiWorld.LOGGER.info("Mapping {} to {}", key, path);
        return RegistryKey.of(
                RegistryKeys.WORLD,
                Identifier.of(namespace, path)
        );
    }

//    public static boolean isWorldEnd(RegistryKey<World> key) {
//        RegistryKey.of(
//                RegistryKeys.WORLD,
//                Identifier.of(namespace, creativeWorldName)
//        );
//        return key == World.END || key ==
//    }
}
