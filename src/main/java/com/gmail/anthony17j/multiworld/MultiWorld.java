package com.gmail.anthony17j.multiworld;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.gmail.anthony17j.multiworld.command.CreativeCommand;
import com.gmail.anthony17j.multiworld.command.MultiWorldCommand;
import com.gmail.anthony17j.multiworld.mixin.IMinecraftServerMixin;
import com.gmail.anthony17j.multiworld.mixin.ServerLevelAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.flat.FlatLayerInfo;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getBaseWorldName;

public class MultiWorld implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(MultiWorld.class);

    public static final String NAMESPACE = "multiworld";
    public static final ResourceKey<DimensionType> DEFAULT_DIM_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath(NAMESPACE, "default"));

    public static final String CREATIVE_WORLD_NAME = "creative";
    private static final String CONFIG_FILE_NAME = "multiworld.dat";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(CreativeCommand::register);
        CommandRegistrationCallback.EVENT.register(MultiWorldCommand::register);

        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {
            loadCustomWorlds(server);

            Holder<Biome> voidBiome = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
            Holder<Biome> netherBiome = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.NETHER_WASTES);
            Holder<Biome> endBiome = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_END);

            createWorld(server, CREATIVE_WORLD_NAME, BuiltinDimensionTypes.OVERWORLD, new FlatLevelSource(
                    new FlatLevelGeneratorSettings(Optional.empty(), voidBiome, List.of())
                            .withBiomeAndLayers(
                                    List.of(
                                            new FlatLayerInfo(1, Blocks.BEDROCK),
                                            new FlatLayerInfo(61, Blocks.DIRT),
                                            new FlatLayerInfo(1, Blocks.GRASS_BLOCK)
                                    ),
                                    Optional.empty(),
                                    voidBiome
                            )
            ), BlockPos.ZERO, GameType.CREATIVE);
            createWorld(server, CREATIVE_WORLD_NAME + "_nether", BuiltinDimensionTypes.NETHER, new FlatLevelSource(
                    new FlatLevelGeneratorSettings(Optional.empty(), netherBiome, List.of())
                            .withBiomeAndLayers(List.of(), Optional.empty(), netherBiome)
            ), BlockPos.ZERO, GameType.CREATIVE);
            createWorld(server, CREATIVE_WORLD_NAME + "_end", BuiltinDimensionTypes.END, new FlatLevelSource(
                    new FlatLevelGeneratorSettings(Optional.empty(), endBiome, List.of())
                            .withBiomeAndLayers(List.of(), Optional.empty(), endBiome)
            ), BlockPos.ZERO, GameType.CREATIVE);
        });
    }

    private static void loadCustomWorlds(MinecraftServer server) {
        String serverWorldFolder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString();
        File dimensionsFolder = new File(serverWorldFolder + "/dimensions/" + NAMESPACE);

        if (!dimensionsFolder.exists() || !dimensionsFolder.isDirectory()) {
            LOGGER.info("No custom dimensions found.");
            return;
        }

        File[] worldFolders = dimensionsFolder.listFiles(File::isDirectory);

        if (worldFolders == null || worldFolders.length == 0) {
            LOGGER.info("No custom worlds found in dimensions folder.");
            return;
        }

        for (File worldFolder : worldFolders) {
            String worldName = worldFolder.getName();

            ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, worldName));
            ServerLevel existingWorld = server.getLevel(worldKey);

            if (existingWorld instanceof CustomServerWorld) {
                LOGGER.info("World '{}' already loaded correctly as CustomServerWorld", worldName);
                continue;
            }

            if (existingWorld != null) {
                unload(server, existingWorld);
            }

            File dataFolder = new File(worldFolder, "data");
            File configFile = new File(dataFolder, CONFIG_FILE_NAME);

            if (!configFile.exists()) {
                LOGGER.warn("Config file not found for world '{}', skipping", worldName);
                continue;
            }

            try {
                CompoundTag configNbt = NbtIo.readCompressed(configFile.toPath(), NbtAccounter.unlimitedHeap());

                long seed = configNbt.getLong("seed").orElseThrow();
                ResourceKey<DimensionType> dimensionType = getDimensionTypeFromString(configNbt.getString("dimensionType").orElseThrow());
                String generatorType = configNbt.getString("generatorType").orElse("overworld");

                ChunkGenerator generator = createGeneratorFromType(server, generatorType, configNbt);

                BlockPos spawnPos = null;
                if (configNbt.contains("spawnPos")) {
                    int[] spawnPosArray = configNbt.getIntArray("spawnPos").orElse(new int[]{0, 64, 0});
                    spawnPos = new BlockPos(spawnPosArray[0], spawnPosArray[1], spawnPosArray[2]);
                }

                GameType gameMode = null;
                if (configNbt.contains("gameMode")) {
                    int gameModeIndex = configNbt.getInt("gameMode").orElse(0);
                    gameMode = GameType.byId(gameModeIndex);
                }

                createWorld(server, worldName, dimensionType, generator, seed, spawnPos, gameMode);

            } catch (Exception e) {
                LOGGER.error("Failed to load world '{}'", worldName, e);
            }
        }
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

    private static void saveWorldConfig(
            MinecraftServer server,
            String worldString,
            ResourceKey<DimensionType> dimensionType,
            ChunkGenerator generator,
            String generatorType,
            long seed,
            BlockPos spawnPos,
            GameType gameMode
    ) {
        String serverWorldFolder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString();
        String dimensionPath = serverWorldFolder + "/dimensions/" + NAMESPACE + "/" + worldString + "/data";
        File dimensionDataDir = new File(dimensionPath);

        if (!dimensionDataDir.exists() && !dimensionDataDir.mkdirs()) {
            LOGGER.error("Could not create data directory for world '{}'", worldString);
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
                    layerNbt.putString("block", BuiltInRegistries.BLOCK.getKey(layer.getBlockState().getBlock()).toString());
                    layersNbt[i] = layerNbt;
                }

                CompoundTag layersCompound = new CompoundTag();
                for (int i = 0; i < layersNbt.length; i++) {
                    layersCompound.put("layer" + i, layersNbt[i]);
                }
                layersCompound.putInt("count", layersNbt.length);
                flatConfigNbt.put("layers", layersCompound);
                flatConfigNbt.putString("biome", flatConfig.getBiome().unwrapKey().map(k -> k.identifier().toString()).orElseThrow());
                configNbt.put("flatConfig", flatConfigNbt);
            }

            if (spawnPos != null) {
                configNbt.putIntArray("spawnPos", new int[]{spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()});
            }

            if (gameMode != null) {
                configNbt.putInt("gameMode", gameMode.getId());
            }

            NbtIo.writeCompressed(configNbt, configFile.toPath());
        } catch (IOException e) {
            LOGGER.error("Failed to save world configuration for '{}': {}", worldString, e.getMessage());
        }
    }

    private static ChunkGenerator createGeneratorFromType(MinecraftServer server, String generatorType, CompoundTag configNbt) {
        var biomeLookup = server.registryAccess().lookupOrThrow(Registries.BIOME);

        return switch (generatorType) {
            case "nether" -> server.getLevel(Level.NETHER).getChunkSource().getGenerator();
            case "end" -> server.getLevel(Level.END).getChunkSource().getGenerator();
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

                            Block block = server.registryAccess().lookupOrThrow(Registries.BLOCK).getOrThrow(ResourceKey.create(Registries.BLOCK, blockIdentifier)).value();
                            layers.add(new FlatLayerInfo(thickness, block));
                        }

                        String biomeId = flatConfigNbt.getString("biome").orElse("minecraft:plains");
                        Identifier biomeIdentifier = Identifier.tryParse(biomeId);
                        Holder<Biome> biome = biomeLookup.getOrThrow(ResourceKey.create(Registries.BIOME, biomeIdentifier));

                        yield new FlatLevelSource(
                                new FlatLevelGeneratorSettings(Optional.empty(), biome, List.of())
                                        .withBiomeAndLayers(layers, Optional.empty(), biome)
                        );
                    } catch (Exception e) {
                        LOGGER.error("Error reconstructing flat generator", e);
                    }
                }
                Holder<Biome> plainsBiome = biomeLookup.getOrThrow(Biomes.PLAINS);
                yield new FlatLevelSource(
                        new FlatLevelGeneratorSettings(Optional.empty(), plainsBiome, List.of())
                                .withBiomeAndLayers(
                                        List.of(
                                                new FlatLayerInfo(1, Blocks.BEDROCK),
                                                new FlatLayerInfo(61, Blocks.DIRT),
                                                new FlatLayerInfo(1, Blocks.GRASS_BLOCK)
                                        ),
                                        Optional.empty(),
                                        plainsBiome
                                )
                );
            }
            default -> server.overworld().getChunkSource().getGenerator();
        };
    }

    public static boolean importWorld(MinecraftServer server, String worldFolderPath, String worldName) {
        LOGGER.info("Importing world from {} to multiworld:{}", worldFolderPath, worldName);

        File sourceWorldFolder = new File(worldFolderPath);
        if (!sourceWorldFolder.exists() || !sourceWorldFolder.isDirectory()) {
            LOGGER.error("Source world folder {} does not exist or is not a directory", worldFolderPath);
            return false;
        }

        try {
            sourceWorldFolder = resolveImportSourceWorldFolder(sourceWorldFolder);
            File levelDatFile = new File(sourceWorldFolder, "level.dat");
            if (!levelDatFile.exists()) {
                LOGGER.error("level.dat not found in {}", sourceWorldFolder.getAbsolutePath());
                return false;
            }

                CompoundTag levelData = NbtIo.readCompressed(levelDatFile.toPath(), NbtAccounter.unlimitedHeap());
                levelData = DataFixTypes.LEVEL.updateToCurrentVersion(
                    server.getFixerUpper(),
                    levelData,
                    NbtUtils.getDataVersion(levelData, 0)
                );
                NbtUtils.addCurrentDataVersion(levelData);
            CompoundTag dataTag = levelData.getCompound("Data").orElseThrow();
            CompoundTag worldGenSettings = dataTag.getCompound("WorldGenSettings").orElseThrow();
            long seed = worldGenSettings.getLong("seed").orElseThrow();
            LOGGER.info("Seed from world: {}", seed);

            String serverWorldFolder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toString();

            ResourceKey<Level> overWorldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, worldName));
            ResourceKey<Level> netherWorldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, worldName + "_nether"));
            ResourceKey<Level> endWorldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, worldName + "_end"));

            copyWorldFiles(sourceWorldFolder, serverWorldFolder, overWorldKey);

            File netherFolder = new File(sourceWorldFolder, "DIM-1");
            if (netherFolder.exists()) {
                copyWorldFiles(netherFolder, serverWorldFolder, netherWorldKey);
            }

            File endFolder = new File(sourceWorldFolder, "DIM1");
            if (endFolder.exists()) {
                copyWorldFiles(endFolder, serverWorldFolder, endWorldKey);
            }

            createWorldWithDimensions(server, worldName, seed);

            ServerLevel overworld = server.getLevel(overWorldKey);

            if (overworld == null) {
                LOGGER.error("Failed to create overworld dimension");
                return false;
            }

            File multiworldFolder = new File(serverWorldFolder, NAMESPACE);
            if (!multiworldFolder.exists() && !multiworldFolder.mkdirs()) {
                LOGGER.error("Could not create multiworld directory");
                return false;
            }

            File worldNameFolder = new File(multiworldFolder, worldName);
            if (!worldNameFolder.exists() && !worldNameFolder.mkdirs()) {
                LOGGER.error("Could not create world directory");
                return false;
            }

            File playerDataFolder = new File(sourceWorldFolder, "playerdata");
            if (playerDataFolder.exists() && playerDataFolder.isDirectory()) {
                File[] playerDataFiles = playerDataFolder.listFiles((dir, name) -> name.endsWith(".dat"));
                if (playerDataFiles != null) {
                    for (File playerDataFile : playerDataFiles) {
                        try {
                            CompoundTag playerData = NbtIo.readCompressed(playerDataFile.toPath(), NbtAccounter.unlimitedHeap());

                            String uuid = playerDataFile.getName().replace(".dat", "");

                            playerData = Utils.migratePlayerDataToCurrentVersion(
                                    server,
                                    playerData,
                                    "import/" + worldName + "/" + uuid
                            );

                            updateDimensionInTag(playerData, "respawn", worldName);

                            String dimensionTagString = playerData.getString("Dimension").orElse(Level.OVERWORLD.identifier().toString());
                            playerData.putString("Dimension", convertDimensionName(dimensionTagString, worldName));

                            updateDimensionInTag(playerData, "LastDeathLocation", worldName);

                            JsonObject playerJson = new JsonObject();
                            playerJson.put("player", playerData.toString());

                            File statsFile = new File(sourceWorldFolder, "stats/" + uuid + ".json");
                            if (statsFile.exists()) {
                                try (FileReader statsReader = new FileReader(statsFile)) {
                                    JsonObject statsJson = (JsonObject) Jsoner.deserialize(statsReader);
                                    playerJson.put("stats", statsJson);
                                }
                            }

                            File advancementsFile = new File(sourceWorldFolder, "advancements/" + uuid + ".json");
                            if (advancementsFile.exists()) {
                                try (FileReader advancementsReader = new FileReader(advancementsFile)) {
                                    JsonObject advancementsJson = (JsonObject) Jsoner.deserialize(advancementsReader);
                                    playerJson.put("advancements", advancementsJson);
                                }
                            }

                            try (BufferedWriter writer = Files.newBufferedWriter(
                                    Paths.get(worldNameFolder.getPath(), uuid + ".json"))) {
                                Jsoner.serialize(playerJson, writer);
                            }

                        } catch (Exception e) {
                            LOGGER.error("Error processing player data: {}", e.getMessage());
                        }
                    }
                }
            }

            LOGGER.info("World import completed successfully!");
            return true;

        } catch (Exception e) {
            LOGGER.error("Error importing world", e);
            return false;
        }
    }

    private static File resolveImportSourceWorldFolder(File sourceWorldFolder) {
        if (new File(sourceWorldFolder, "level.dat").exists()) {
            return sourceWorldFolder;
        }

        File parent = sourceWorldFolder.getParentFile();
        if (parent != null && new File(parent, "level.dat").exists()) {
            return parent;
        }

        File nestedWorld = new File(sourceWorldFolder, "world");
        if (nestedWorld.isDirectory() && new File(nestedWorld, "level.dat").exists()) {
            return nestedWorld;
        }

        return sourceWorldFolder;
    }

    private static void updateDimensionInTag(CompoundTag parentTag, String tagName, String worldName) {
        Optional<CompoundTag> tagOpt = parentTag.getCompound(tagName);
        if (tagOpt.isPresent()) {
            CompoundTag tag = tagOpt.get();
            String dimension = tag.getString("dimension").orElse(Level.OVERWORLD.identifier().toString());
            tag.putString("dimension", convertDimensionName(dimension, worldName));
            parentTag.put(tagName, tag);
        }
    }

    private static String convertDimensionName(String originalDimension, String worldName) {
        String newDimension;
        if (originalDimension.endsWith("_nether")) {
            newDimension = worldName + "_nether";
        } else if (originalDimension.endsWith("_end")) {
            newDimension = worldName + "_end";
        } else {
            newDimension = worldName;
        }
        return NAMESPACE + ":" + newDimension;
    }

    private static void copyWorldFiles(File sourceFolder, String serverWorldFolder, ResourceKey<Level> worldKey) {
        String worldDirectory = serverWorldFolder + "/dimensions/" +
                worldKey.identifier().getNamespace() + "/" + worldKey.identifier().getPath();

        copyFilesBeforeWorldCreation(sourceFolder, "region", worldDirectory);
        copyFilesBeforeWorldCreation(sourceFolder, "poi", worldDirectory);
        copyFilesBeforeWorldCreation(sourceFolder, "entities", worldDirectory);
        copyFilesBeforeWorldCreation(sourceFolder, "data", worldDirectory);
    }

    private static void copyFilesBeforeWorldCreation(File sourceFolder, String type, String destinationPath) {
        File sourceTypeFolder = new File(sourceFolder, type);
        if (!sourceTypeFolder.exists() || !sourceTypeFolder.isDirectory()) {
            return;
        }

        String fileExtension = type.equals("data") ? ".dat" : ".mca";
        File[] files = sourceTypeFolder.listFiles((dir, name) -> name.endsWith(fileExtension));
        if (files == null || files.length == 0) {
            return;
        }

        File destinationFolder = new File(destinationPath + "/" + type);
        if (!destinationFolder.exists() && !destinationFolder.mkdirs()) {
            LOGGER.error("Could not create destination folder: {}", destinationFolder);
            return;
        }

        for (File file : files) {
            try {
                Files.copy(
                        file.toPath(),
                        new File(destinationFolder, file.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (IOException e) {
                LOGGER.error("Error copying file {}: {}", file.getName(), e.getMessage());
            }
        }

        LOGGER.info("Copied {} files from {} to {}", files.length, type, destinationPath + "/" + type);
    }

    public static void createWorldWithDimensions(MinecraftServer server, String worldString, long seed) {
        var noiseSettingsLookup = server.registryAccess().lookupOrThrow(Registries.NOISE_SETTINGS);
        var multiNoiseLookup = server.registryAccess().lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var biomeLookup = server.registryAccess().lookupOrThrow(Registries.BIOME);

        ChunkGenerator overworldGenerator = new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromPreset(multiNoiseLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)),
                noiseSettingsLookup.getOrThrow(NoiseGeneratorSettings.OVERWORLD));
        ChunkGenerator netherGenerator = new NoiseBasedChunkGenerator(
                MultiNoiseBiomeSource.createFromPreset(multiNoiseLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)),
                noiseSettingsLookup.getOrThrow(NoiseGeneratorSettings.NETHER));
        ChunkGenerator endGenerator = new NoiseBasedChunkGenerator(
                TheEndBiomeSource.create(biomeLookup),
                noiseSettingsLookup.getOrThrow(NoiseGeneratorSettings.END));

        createWorld(server, worldString, BuiltinDimensionTypes.OVERWORLD, overworldGenerator, seed, null, null);
        createWorld(server, worldString + "_nether", BuiltinDimensionTypes.NETHER, netherGenerator, seed, null, null);
        createWorld(server, worldString + "_end", BuiltinDimensionTypes.END, endGenerator, seed, null, null);
    }

    private static void createWorld(MinecraftServer server, String worldString, ResourceKey<DimensionType> dimensionType, ChunkGenerator generator, BlockPos spawnPos, GameType gameMode) {
        createWorld(server, worldString, dimensionType, generator, server.overworld().getSeed(), spawnPos, gameMode);
    }

    private static void createWorld(
            MinecraftServer server,
            String worldString,
            ResourceKey<DimensionType> dimensionType,
            ChunkGenerator generator,
            long seed,
            BlockPos spawnPos,
            GameType gameMode
    ) {
        ResourceKey<Level> worldKey = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(NAMESPACE, worldString)
        );

        if (server.getLevel(worldKey) != null) {
            ServerLevel world = server.getLevel(worldKey);
            if (world instanceof CustomServerWorld) {
                return;
            } else {
                for (ServerPlayer player : world.players()) {
                    Utils.saveInv(player, getBaseWorldName(worldKey.identifier().getPath()));
                    Utils.loadInv(player, "overworld");
                }
                unload(server, world);
            }
        }

        Holder.Reference<DimensionType> dimensionTypeEntry = server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(dimensionType);

        LevelStem options = new LevelStem(dimensionTypeEntry, generator);

        RegistryAccess.Frozen registryManager = server.registries().compositeAccess();
        MappedRegistry<LevelStem> dimensionsRegistry = (MappedRegistry<LevelStem>) registryManager.lookupOrThrow(Registries.LEVEL_STEM);

        boolean isFrozen = ((CustomSimpleRegistry<?>) dimensionsRegistry).multiWorld$isFrozen();
        ((CustomSimpleRegistry<?>) dimensionsRegistry).multiWorld$setFrozen(false);

        ResourceKey<LevelStem> dimensionKey = ResourceKey.create(Registries.LEVEL_STEM, worldKey.identifier());
        if (!dimensionsRegistry.containsKey(dimensionKey)) {
            dimensionsRegistry.register(dimensionKey, options, RegistrationInfo.BUILT_IN);
        }

        ((CustomSimpleRegistry<?>) dimensionsRegistry).multiWorld$setFrozen(isFrozen);

        var levelProperties = server.getWorldData().overworldData();
        CustomServerWorldProperties newLevelProperties = new CustomServerWorldProperties(server.getWorldData(), levelProperties, seed);

        CustomServerWorld.Constructor worldConstructor = CustomServerWorld::new;
        CustomServerWorld addedWorld = worldConstructor.create(server, worldKey, options, newLevelProperties, seed);

        if (gameMode != null) {
            ((ServerLevelAccessor) addedWorld).getWorldProperties().setGameType(gameMode);
        }

        // Set up dragon fight for custom end worlds
        if (dimensionType == BuiltinDimensionTypes.END) {
            ((ServerLevelAccessor) addedWorld).setDragonFightAccessor(new net.minecraft.world.level.dimension.end.EndDragonFight(
                    addedWorld, seed, net.minecraft.world.level.dimension.end.EndDragonFight.Data.DEFAULT));
        }

        ((IMinecraftServerMixin) server).getWorlds().put(addedWorld.dimension(), addedWorld);

        if (spawnPos != null) {
            newLevelProperties.setSpawn(new LevelData.RespawnData(new GlobalPos(worldKey, spawnPos), 0.0F, 0.0F));
        } else {
            IMinecraftServerMixin.invokeSetupSpawn(addedWorld, newLevelProperties, false, false, server.getLevelLoadListener());
        }

        ServerWorldEvents.LOAD.invoker().onWorldLoad(server, addedWorld);

        saveWorldConfig(
                server,
                worldString,
                dimensionType,
                generator,
                generator instanceof FlatLevelSource ? "flat" : worldString.endsWith("_nether") ? "nether" : worldString.endsWith("_end") ? "end" : "overworld",
                seed,
                spawnPos,
                gameMode
        );

        LOGGER.info("Successfully loaded world '{}'", worldString);
    }

    private static void unload(MinecraftServer server, ServerLevel world) {
        ResourceKey<Level> dimensionKey = world.dimension();

        if (((IMinecraftServerMixin) server).getWorlds().remove(dimensionKey, world)) {
            world.save(new ProgressListener() {
                @Override
                public void progressStartNoAbort(Component title) {}

                @Override
                public void progressStart(Component title) {}

                @Override
                public void progressStage(Component task) {}

                @Override
                public void progressStagePercentage(int percentage) {}

                @Override
                public void stop() {}
            }, true, false);

            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, world);

            RegistryAccess.Frozen registryManager = server.registries().compositeAccess();
            MappedRegistry<LevelStem> dimensionsRegistry = (MappedRegistry<LevelStem>) registryManager.lookupOrThrow(Registries.LEVEL_STEM);
            CustomSimpleRegistry.remove(dimensionsRegistry, dimensionKey.identifier());
        }
    }

    public static void deleteWorld(MinecraftServer server, String worldName) {
        LOGGER.info("Deleting world: {}", worldName);

        ResourceKey<Level> overWorldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, worldName));
        ResourceKey<Level> netherWorldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, worldName + "_nether"));
        ResourceKey<Level> endWorldKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, worldName + "_end"));

        ServerLevel overworld = server.getLevel(overWorldKey);
        ServerLevel nether = server.getLevel(netherWorldKey);
        ServerLevel end = server.getLevel(endWorldKey);

        if (overworld != null) {
            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, overworld);
        }
        if (nether != null) {
            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, nether);
        }
        if (end != null) {
            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, end);
        }

        RegistryAccess.Frozen registryManager = server.registries().compositeAccess();
        MappedRegistry<LevelStem> dimensionsRegistry = (MappedRegistry<LevelStem>) registryManager.lookupOrThrow(Registries.LEVEL_STEM);

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
        }
        if (nether != null) {
            deleteWorldFiles(serverWorldFolder, netherWorldKey);
            ((IMinecraftServerMixin) server).getWorlds().remove(netherWorldKey);
        }
        if (end != null) {
            deleteWorldFiles(serverWorldFolder, endWorldKey);
            ((IMinecraftServerMixin) server).getWorlds().remove(endWorldKey);
        }

        File multiworldFolder = new File(serverWorldFolder, NAMESPACE);
        File worldNameFolder = new File(multiworldFolder, worldName);
        if (worldNameFolder.exists()) {
            deleteFolder(worldNameFolder);
        }

        LOGGER.info("World deletion completed: {}", worldName);
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
