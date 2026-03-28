package fr.jeanney;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.gamerules.GameRules;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class WorldConfigService {
    static final String CONFIG_FILE_NAME = "multiworld.dat";

    @FunctionalInterface
    interface WorldUnloader {
        void unload(MinecraftServer server, ServerLevel world);
    }

    @FunctionalInterface
    interface WorldCreator {
        void create(
                MinecraftServer server,
                String worldName,
                ResourceKey<DimensionType> dimensionType,
                ChunkGenerator generator,
                long seed,
                BlockPos spawnPos,
                GameType gameMode,
                MultiWorld.WorldMode worldMode);
    }

    private WorldConfigService() {
    }

    static void loadCustomWorlds(MinecraftServer server, WorldUnloader worldUnloader, WorldCreator worldCreator) {
        String serverWorldFolder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString();
        File dimensionsFolder = new File(serverWorldFolder + "/dimensions/" + MultiWorld.NAMESPACE);

        if (!dimensionsFolder.exists() || !dimensionsFolder.isDirectory()) {
            MultiWorld.LOGGER.info("No custom dimensions found.");
            return;
        }

        File[] worldFolders = dimensionsFolder.listFiles(File::isDirectory);

        if (worldFolders == null || worldFolders.length == 0) {
            MultiWorld.LOGGER.info("No custom worlds found in dimensions folder.");
            return;
        }

        for (File worldFolder : worldFolders) {
            String worldName = worldFolder.getName();

            ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(MultiWorld.NAMESPACE, worldName));
            ServerLevel existingWorld = server.getLevel(worldKey);

            if (existingWorld instanceof CustomServerWorld) {
                MultiWorld.LOGGER.info("World '{}' already loaded correctly as CustomServerWorld", worldName);
                continue;
            }

            if (existingWorld != null) {
                worldUnloader.unload(server, existingWorld);
            }

            File dataFolder = new File(worldFolder, "data");
            File configFile = new File(dataFolder, CONFIG_FILE_NAME);

            if (!configFile.exists()) {
                MultiWorld.LOGGER.warn("Config file not found for world '{}', skipping", worldName);
                continue;
            }

            try {
                CompoundTag configNbt = NbtIo.readCompressed(configFile.toPath(), NbtAccounter.unlimitedHeap());

                long seed = configNbt.getLong("seed").orElseThrow();
                ResourceKey<DimensionType> dimensionType = getDimensionTypeFromString(
                        configNbt.getString("dimensionType").orElseThrow());
                String generatorType = configNbt.getString("generatorType").orElse("overworld");

                ChunkGenerator generator = createGeneratorFromType(server, generatorType, configNbt);

                BlockPos spawnPos = null;
                if (configNbt.contains("spawnPos")) {
                    int[] spawnPosArray = configNbt.getIntArray("spawnPos").orElse(new int[] { 0, 64, 0 });
                    spawnPos = new BlockPos(spawnPosArray[0], spawnPosArray[1], spawnPosArray[2]);
                }

                GameType gameMode = null;
                if (configNbt.contains("gameMode")) {
                    int gameModeIndex = configNbt.getInt("gameMode").orElse(0);
                    gameMode = GameType.byId(gameModeIndex);
                }

                MultiWorld.WorldMode worldMode = configNbt.contains("worldMode")
                        ? MultiWorld.WorldMode.fromString(configNbt.getString("worldMode").orElse("normal"))
                        : (gameMode == GameType.CREATIVE ? MultiWorld.WorldMode.CREATIVE : MultiWorld.WorldMode.NORMAL);

                GameRules loadedGameRules = configNbt.contains("gameRules")
                        ? decodeGameRules(server, configNbt.getCompound("gameRules").orElse(new CompoundTag()))
                        : new GameRules(server.getWorldData().enabledFeatures());

                worldCreator.create(server, worldName, dimensionType, generator, seed, spawnPos, gameMode, worldMode);
                WorldStateService.registerWorldMode(worldName, worldMode);

                ServerLevel loadedWorld = server.getLevel(worldKey);
                if (loadedWorld != null) {
                    loadedWorld.getGameRules().setAll(loadedGameRules, server);
                    WorldStateService.setLastSavedGameRuleSnapshot(worldKey,
                            encodeGameRules(server, loadedGameRules).toString());
                }

            } catch (Exception e) {
                MultiWorld.LOGGER.error("Failed to load world '{}'", worldName, e);
            }
        }
    }

    static void saveWorldConfig(
            MinecraftServer server,
            String worldString,
            ResourceKey<DimensionType> dimensionType,
            ChunkGenerator generator,
            String generatorType,
            long seed,
            BlockPos spawnPos,
            GameType gameMode,
            MultiWorld.WorldMode worldMode,
            GameRules gameRules) {
        String serverWorldFolder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString();
        String dimensionPath = serverWorldFolder + "/dimensions/" + MultiWorld.NAMESPACE + "/" + worldString + "/data";
        File dimensionDataDir = new File(dimensionPath);

        if (!dimensionDataDir.exists() && !dimensionDataDir.mkdirs()) {
            MultiWorld.LOGGER.error("Could not create data directory for world '{}'", worldString);
            return;
        }

        File configFile = new File(dimensionDataDir, CONFIG_FILE_NAME);

        try {
            CompoundTag configNbt = new CompoundTag();
            configNbt.putString("dimensionType", dimensionType.identifier().toString());
            configNbt.putLong("seed", seed);
            configNbt.putString("generatorType", generatorType);

            if (generatorType.equals("flat") && generator instanceof FlatLevelSource flatGenerator) {
                FlatLevelGeneratorSettings flatConfig = flatGenerator.settings();

                CompoundTag flatConfigNbt = new CompoundTag();
                List<FlatLayerInfo> layers = flatConfig.getLayersInfo();
                CompoundTag[] layersNbt = new CompoundTag[layers.size()];

                for (int i = 0; i < layers.size(); i++) {
                    FlatLayerInfo layer = layers.get(i);
                    CompoundTag layerNbt = new CompoundTag();
                    layerNbt.putInt("height", layer.getHeight());
                    layerNbt.putString("block",
                            BuiltInRegistries.BLOCK.getKey(layer.getBlockState().getBlock()).toString());
                    layersNbt[i] = layerNbt;
                }

                CompoundTag layersCompound = new CompoundTag();
                for (int i = 0; i < layersNbt.length; i++) {
                    layersCompound.put("layer" + i, layersNbt[i]);
                }
                layersCompound.putInt("count", layersNbt.length);
                flatConfigNbt.put("layers", layersCompound);
                flatConfigNbt.putString("biome",
                        flatConfig.getBiome().unwrapKey().map(k -> k.identifier().toString()).orElseThrow());
                configNbt.put("flatConfig", flatConfigNbt);
            }

            if (spawnPos != null) {
                configNbt.putIntArray("spawnPos", new int[] { spawnPos.getX(), spawnPos.getY(), spawnPos.getZ() });
            }

            if (gameMode != null) {
                configNbt.putInt("gameMode", gameMode.getId());
            }

            if (worldMode != null) {
                configNbt.putString("worldMode", worldMode.toConfigValue());
            }

            if (gameRules != null) {
                configNbt.put("gameRules", encodeGameRules(server, gameRules));
            }

            NbtIo.writeCompressed(configNbt, configFile.toPath());
        } catch (IOException e) {
            MultiWorld.LOGGER.error("Failed to save world configuration for '{}': {}", worldString, e.getMessage());
        }
    }

    static void autosaveCustomWorldGameRules(MinecraftServer server) {
        if (!WorldStateService.shouldAutosaveGameRulesThisTick()) {
            return;
        }
        persistCustomWorldGameRules(server, false);
    }

    static void persistCustomWorldGameRules(MinecraftServer server, boolean force) {
        for (ServerLevel world : server.getAllLevels()) {
            if (!MultiWorld.NAMESPACE.equals(world.dimension().identifier().getNamespace())) {
                continue;
            }

            ResourceKey<Level> worldKey = world.dimension();
            CompoundTag encodedRules = encodeGameRules(server, world.getGameRules());
            String currentSnapshot = encodedRules.toString();
            String previousSnapshot = WorldStateService.getLastSavedGameRuleSnapshot(worldKey);
            if (!force && currentSnapshot.equals(previousSnapshot)) {
                continue;
            }

            String worldName = world.dimension().identifier().getPath();
            String serverWorldFolder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toString();
            File configFile = new File(
                    serverWorldFolder + "/dimensions/" + MultiWorld.NAMESPACE + "/" + worldName + "/data/"
                            + CONFIG_FILE_NAME);
            if (!configFile.exists()) {
                continue;
            }

            try {
                CompoundTag configNbt = NbtIo.readCompressed(configFile.toPath(), NbtAccounter.unlimitedHeap());
                configNbt.put("gameRules", encodedRules);
                NbtIo.writeCompressed(configNbt, configFile.toPath());
                WorldStateService.setLastSavedGameRuleSnapshot(worldKey, currentSnapshot);
            } catch (Exception e) {
                MultiWorld.LOGGER.warn("Failed to persist gamerules for world '{}': {}", worldName, e.getMessage());
            }
        }
    }

    static CompoundTag encodeGameRules(MinecraftServer server, GameRules gameRules) {
        Object encoded = GameRules.codec(server.getWorldData().enabledFeatures())
                .encodeStart(NbtOps.INSTANCE, gameRules)
                .result()
                .orElse(null);
        if (encoded instanceof CompoundTag compoundTag) {
            return compoundTag;
        }
        return new CompoundTag();
    }

    private static GameRules decodeGameRules(MinecraftServer server, CompoundTag gameRulesTag) {
        return GameRules.codec(server.getWorldData().enabledFeatures())
                .parse(NbtOps.INSTANCE, gameRulesTag)
                .result()
                .orElseGet(() -> new GameRules(server.getWorldData().enabledFeatures()));
    }

    static ChunkGenerator createVanillaOverworldGenerator(MinecraftServer server) {
        var multiNoiseLookup = server.registryAccess()
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var noiseSettingsLookup = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
        return new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource
                        .createFromPreset(multiNoiseLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                noiseSettingsLookup.getOrThrow(NoiseGeneratorSettings.OVERWORLD));
    }

    static ChunkGenerator createVanillaNetherGenerator(MinecraftServer server) {
        var multiNoiseLookup = server.registryAccess()
                .lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var noiseSettingsLookup = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
        return new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource
                        .createFromPreset(multiNoiseLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)),
                noiseSettingsLookup.getOrThrow(NoiseGeneratorSettings.NETHER));
    }

    static ChunkGenerator createVanillaEndGenerator(MinecraftServer server) {
        var biomeLookup = server.registryAccess().lookupOrThrow(Registries.BIOME);
        var noiseSettingsLookup = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
        return new NoiseBasedChunkGenerator(
                TheEndBiomeSource.create(biomeLookup),
                noiseSettingsLookup.getOrThrow(NoiseGeneratorSettings.END));
    }

    private static ResourceKey<DimensionType> getDimensionTypeFromString(String dimensionTypeString) {
        if (dimensionTypeString == null || dimensionTypeString.isEmpty()) {
            return BuiltinDimensionTypes.OVERWORLD;
        }

        String[] parts = dimensionTypeString.split(":");
        if (parts.length != 2) {
            return BuiltinDimensionTypes.OVERWORLD;
        }

        return ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath(parts[0], parts[1]));
    }

    private static ChunkGenerator createGeneratorFromType(MinecraftServer server, String generatorType,
            CompoundTag configNbt) {
        var biomeLookup = server.registryAccess().lookupOrThrow(Registries.BIOME);

        return switch (generatorType) {
            case "nether" -> createVanillaNetherGenerator(server);
            case "end" -> createVanillaEndGenerator(server);
            case "flat" -> {
                if (configNbt.contains("flatConfig")) {
                    try {
                        CompoundTag flatConfigNbt = configNbt.getCompound("flatConfig").orElseThrow();
                        CompoundTag layersCompound = flatConfigNbt.getCompound("layers").orElseThrow();
                        int layerCount = layersCompound.getInt("count").orElse(0);

                        List<FlatLayerInfo> layers = new ArrayList<>();
                        for (int i = 0; i < layerCount; i++) {
                            CompoundTag layerNbt = layersCompound.getCompound("layer" + i).orElseThrow();
                            int thickness = layerNbt.getInt("height").orElseThrow();
                            String blockId = layerNbt.getString("block").orElseThrow();
                            Identifier blockIdentifier = Identifier.tryParse(blockId);
                            if (blockIdentifier == null) {
                                throw new IllegalArgumentException("Invalid block identifier: " + blockId);
                            }

                            Block block = server.registryAccess().lookupOrThrow(Registries.BLOCK)
                                    .getOrThrow(ResourceKey.create(Registries.BLOCK, blockIdentifier)).value();
                            layers.add(new FlatLayerInfo(thickness, block));
                        }

                        String biomeId = flatConfigNbt.getString("biome").orElse("minecraft:plains");
                        Identifier biomeIdentifier = Identifier.tryParse(biomeId);
                        Holder<Biome> biome = biomeLookup
                                .getOrThrow(ResourceKey.create(Registries.BIOME, biomeIdentifier));

                        yield new FlatLevelSource(
                                new FlatLevelGeneratorSettings(Optional.empty(), biome, List.of())
                                        .withBiomeAndLayers(layers, Optional.empty(), biome));
                    } catch (Exception e) {
                        MultiWorld.LOGGER.error("Error reconstructing flat generator", e);
                    }
                }
                Holder<Biome> plainsBiome = biomeLookup.getOrThrow(Biomes.PLAINS);
                yield new FlatLevelSource(
                        new FlatLevelGeneratorSettings(Optional.empty(), plainsBiome, List.of())
                                .withBiomeAndLayers(
                                        List.of(
                                                new FlatLayerInfo(1, Blocks.BEDROCK),
                                                new FlatLayerInfo(61, Blocks.DIRT),
                                                new FlatLayerInfo(1, Blocks.GRASS_BLOCK)),
                                        Optional.empty(),
                                        plainsBiome));
            }
            default -> createVanillaOverworldGenerator(server);
        };
    }
}
