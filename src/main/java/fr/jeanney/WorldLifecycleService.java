package fr.jeanney;

import fr.jeanney.mixin.IMinecraftServerMixin;
import fr.jeanney.mixin.ServerLevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static fr.jeanney.CustomServerWorld.getBaseWorldName;

final class WorldLifecycleService {
    private WorldLifecycleService() {
    }

    static void createWorldWithDimensions(
            MinecraftServer server,
            String worldString,
            long seed,
            BlockPos spawnPos,
            String preset,
            MultiWorld.WorldMode worldMode,
            WorldStateService.WorldInitialState initialState) {
        ChunkGenerator overworldGenerator;
        ChunkGenerator netherGenerator;
        ChunkGenerator endGenerator;

        boolean flatPreset = "flat".equalsIgnoreCase(preset);
        if (flatPreset) {
            var biomeLookup = server.registryAccess().lookupOrThrow(Registries.BIOME);
            Holder<Biome> voidBiome = biomeLookup.getOrThrow(Biomes.THE_VOID);

            overworldGenerator = new FlatLevelSource(
                    new FlatLevelGeneratorSettings(Optional.empty(), voidBiome, List.of())
                            .withBiomeAndLayers(
                                    List.of(
                                            new FlatLayerInfo(1, Blocks.BEDROCK),
                                            new FlatLayerInfo(61, Blocks.DIRT),
                                            new FlatLayerInfo(1, Blocks.GRASS_BLOCK)),
                                    Optional.empty(),
                                    voidBiome));
            netherGenerator = new FlatLevelSource(
                    new FlatLevelGeneratorSettings(Optional.empty(), voidBiome, List.of())
                            .withBiomeAndLayers(List.of(), Optional.empty(), voidBiome));
            endGenerator = new FlatLevelSource(
                    new FlatLevelGeneratorSettings(Optional.empty(), voidBiome, List.of())
                            .withBiomeAndLayers(List.of(), Optional.empty(), voidBiome));
        } else {
            overworldGenerator = WorldConfigService.createVanillaOverworldGenerator(server);
            netherGenerator = WorldConfigService.createVanillaNetherGenerator(server);
            endGenerator = WorldConfigService.createVanillaEndGenerator(server);
        }

        GameType gameMode = worldMode == MultiWorld.WorldMode.CREATIVE ? GameType.CREATIVE : null;
        BlockPos effectiveSpawnPos = worldMode == MultiWorld.WorldMode.CREATIVE ? BlockPos.ZERO : spawnPos;

        ResourceKey<DimensionType> overworldDimensionType = BuiltinDimensionTypes.OVERWORLD;
        if (worldMode == MultiWorld.WorldMode.CREATIVE) {
            try {
                server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE)
                        .getOrThrow(MultiWorld.DEFAULT_DIM_TYPE);
                overworldDimensionType = MultiWorld.DEFAULT_DIM_TYPE;
            } catch (Exception e) {
                MultiWorld.LOGGER.warn("Creative dimension type '{}' not found, falling back to vanilla overworld type",
                        MultiWorld.DEFAULT_DIM_TYPE.identifier());
            }
        }

        createWorld(server, worldString, overworldDimensionType, overworldGenerator, seed, effectiveSpawnPos, gameMode,
                worldMode);
        createWorld(server, worldString + "_nether", BuiltinDimensionTypes.NETHER, netherGenerator, seed, BlockPos.ZERO,
                gameMode, worldMode);
        createWorld(server, worldString + "_end", BuiltinDimensionTypes.END, endGenerator, seed, BlockPos.ZERO,
                gameMode, worldMode);
        WorldStateService.applyInitialStateToWorldFamily(server, worldString, initialState);
        WorldStateService.registerWorldMode(worldString, worldMode);
    }

    static void createWorld(
            MinecraftServer server,
            String worldString,
            ResourceKey<DimensionType> dimensionType,
            ChunkGenerator generator,
            long seed,
            BlockPos spawnPos,
            GameType gameMode,
            MultiWorld.WorldMode worldMode) {
        ResourceKey<Level> worldKey = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(MultiWorld.NAMESPACE, worldString));

        if (server.getLevel(worldKey) != null) {
            ServerLevel world = server.getLevel(worldKey);
            if (world instanceof CustomServerWorld) {
                return;
            } else {
                for (ServerPlayer player : world.players()) {
                    PlayerProfileManager.saveInv(player, getBaseWorldName(worldKey.identifier().getPath()));
                    PlayerProfileManager.loadInv(player, "overworld");
                }
                unload(server, world);
            }
        }

        Holder.Reference<DimensionType> dimensionTypeEntry = server.registryAccess()
                .lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(dimensionType);

        LevelStem options = new LevelStem(dimensionTypeEntry, generator);

        RegistryAccess.Frozen registryManager = server.registries().compositeAccess();
        MappedRegistry<LevelStem> dimensionsRegistry = (MappedRegistry<LevelStem>) registryManager
                .lookupOrThrow(Registries.LEVEL_STEM);

        boolean isFrozen = ((CustomSimpleRegistry<?>) dimensionsRegistry).multiWorld$isFrozen();
        ((CustomSimpleRegistry<?>) dimensionsRegistry).multiWorld$setFrozen(false);

        ResourceKey<LevelStem> dimensionKey = ResourceKey.create(Registries.LEVEL_STEM, worldKey.identifier());
        if (!dimensionsRegistry.containsKey(dimensionKey)) {
            dimensionsRegistry.register(dimensionKey, options, RegistrationInfo.BUILT_IN);
        }

        ((CustomSimpleRegistry<?>) dimensionsRegistry).multiWorld$setFrozen(isFrozen);

        var levelProperties = server.getWorldData().overworldData();
        CustomServerWorldProperties newLevelProperties = new CustomServerWorldProperties(server.getWorldData(),
                levelProperties, seed);

        CustomServerWorld.Constructor worldConstructor = CustomServerWorld::new;
        CustomServerWorld addedWorld = worldConstructor.create(server, worldKey, options, newLevelProperties, seed);
        WorldStateService.initializeCustomWorldState(addedWorld);

        if (gameMode != null) {
            ((ServerLevelAccessor) addedWorld).getWorldProperties().setGameType(gameMode);
        }

        // Set up dragon fight for custom end worlds.
        if (dimensionType.equals(BuiltinDimensionTypes.END)) {
            net.minecraft.world.level.dimension.end.EnderDragonFight dragonFight = net.minecraft.world.level.dimension.end.EnderDragonFight
                    .createDefault();
            dragonFight.init(addedWorld, seed, ServerLevel.END_SPAWN_POINT);
            addedWorld.setDragonFight(dragonFight);
        }

        ((IMinecraftServerMixin) server).getWorlds().put(addedWorld.dimension(), addedWorld);

        BlockPos resolvedSpawn;
        if (spawnPos != null) {
            if (worldMode == MultiWorld.WorldMode.CREATIVE && BlockPos.ZERO.equals(spawnPos)) {
                int surfaceY = addedWorld.getHeight(Heightmap.Types.MOTION_BLOCKING, spawnPos.getX(), spawnPos.getZ());
                int safeY = surfaceY > addedWorld.getMinY() ? surfaceY : 63;
                resolvedSpawn = new BlockPos(spawnPos.getX(), safeY, spawnPos.getZ());
            } else {
                // Preserve imported/configured spawn exactly.
                resolvedSpawn = spawnPos;
            }
        } else {
            // Use the exact vanilla initial spawn pipeline.
            IMinecraftServerMixin.invokeSetupSpawn(
                    addedWorld,
                    newLevelProperties,
                    false,
                    false,
                    ((IMinecraftServerMixin) server).multiWorld$getLevelLoadListener());
            LevelData.RespawnData respawnData = newLevelProperties.getRespawnData();
            if (respawnData != null) {
                resolvedSpawn = respawnData.pos();
            } else {
                resolvedSpawn = BlockPos.ZERO.above(80);
            }
        }
        newLevelProperties.setSpawn(new LevelData.RespawnData(new GlobalPos(worldKey, resolvedSpawn), 0.0F, 0.0F));

        WorldConfigService.saveWorldConfig(
                server,
                worldString,
                dimensionType,
                generator,
                generator instanceof FlatLevelSource ? "flat"
                        : worldString.endsWith("_nether") ? "nether"
                                : worldString.endsWith("_end") ? "end" : "overworld",
                seed,
                spawnPos,
                gameMode,
                worldMode,
                addedWorld.getGameRules());

        MultiWorld.LOGGER.info("Successfully loaded world '{}'", worldString);
    }

    static void unload(MinecraftServer server, ServerLevel world) {
        ResourceKey<Level> dimensionKey = world.dimension();

        if (((IMinecraftServerMixin) server).getWorlds().remove(dimensionKey, world)) {
            world.save(new ProgressListener() {
                @Override
                public void progressStartNoAbort(@NonNull Component title) {
                }

                @Override
                public void progressStart(@NonNull Component title) {
                }

                @Override
                public void progressStage(@NonNull Component task) {
                }

                @Override
                public void progressStagePercentage(int percentage) {
                }

                @Override
                public void stop() {
                }
            }, true, false);

            RegistryAccess.Frozen registryManager = server.registries().compositeAccess();
            MappedRegistry<LevelStem> dimensionsRegistry = (MappedRegistry<LevelStem>) registryManager
                    .lookupOrThrow(Registries.LEVEL_STEM);
            CustomSimpleRegistry.remove(dimensionsRegistry, dimensionKey.identifier());
            WorldStateService.clearCustomWorldState(dimensionKey);
        }
    }

    static void deleteWorld(MinecraftServer server, String worldName) {
        MultiWorld.LOGGER.info("Deleting world: {}", worldName);
        WorldStateService.registerWorldMode(worldName, MultiWorld.WorldMode.NORMAL);

        ResourceKey<Level> overWorldKey = ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(MultiWorld.NAMESPACE, worldName));
        ResourceKey<Level> netherWorldKey = ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(MultiWorld.NAMESPACE, worldName + "_nether"));
        ResourceKey<Level> endWorldKey = ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(MultiWorld.NAMESPACE, worldName + "_end"));

        ServerLevel overworld = server.getLevel(overWorldKey);
        ServerLevel nether = server.getLevel(netherWorldKey);
        ServerLevel end = server.getLevel(endWorldKey);

        RegistryAccess.Frozen registryManager = server.registries().compositeAccess();
        MappedRegistry<LevelStem> dimensionsRegistry = (MappedRegistry<LevelStem>) registryManager
                .lookupOrThrow(Registries.LEVEL_STEM);

        if (overworld != null) {
            CustomSimpleRegistry.remove(dimensionsRegistry, overWorldKey.identifier());
        }
        if (nether != null) {
            CustomSimpleRegistry.remove(dimensionsRegistry, netherWorldKey.identifier());
        }
        if (end != null) {
            CustomSimpleRegistry.remove(dimensionsRegistry, endWorldKey.identifier());
        }

        String serverWorldFolder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString();

        if (overworld != null) {
            deleteWorldFiles(serverWorldFolder, overWorldKey);
            ((IMinecraftServerMixin) server).getWorlds().remove(overWorldKey);
            WorldStateService.clearCustomWorldState(overWorldKey);
        }
        if (nether != null) {
            deleteWorldFiles(serverWorldFolder, netherWorldKey);
            ((IMinecraftServerMixin) server).getWorlds().remove(netherWorldKey);
            WorldStateService.clearCustomWorldState(netherWorldKey);
        }
        if (end != null) {
            deleteWorldFiles(serverWorldFolder, endWorldKey);
            ((IMinecraftServerMixin) server).getWorlds().remove(endWorldKey);
            WorldStateService.clearCustomWorldState(endWorldKey);
        }

        File multiworldFolder = new File(serverWorldFolder, MultiWorld.NAMESPACE);
        File worldNameFolder = new File(multiworldFolder, worldName);
        if (worldNameFolder.exists()) {
            deleteFolder(worldNameFolder);
        }

        MultiWorld.LOGGER.info("World deletion completed: {}", worldName);
    }

    private static void deleteWorldFiles(String serverWorldFolder, ResourceKey<Level> worldKey) {
        String worldPath = serverWorldFolder + "/dimensions/" +
                worldKey.identifier().getNamespace() + "/" + worldKey.identifier().getPath();
        File worldFolder = new File(worldPath);

        if (worldFolder.exists()) {
            deleteFolder(worldFolder);
        }
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteFolder(file);
                } else {
                    file.delete();
                }
            }
        }
        folder.delete();
    }
}
