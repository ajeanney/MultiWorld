package com.gmail.anthony17j.multiworld;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.gmail.anthony17j.multiworld.command.CreativeCommand;
import com.gmail.anthony17j.multiworld.command.MultiWorldCommand;
import com.gmail.anthony17j.multiworld.mixin.MinecraftServerMixin;
import com.gmail.anthony17j.multiworld.mixin.ServerWorldAccessor;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.biome.source.TheEndBiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.level.LevelProperties;
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
    public static final RegistryKey<DimensionType> DEFAULT_DIM_TYPE = RegistryKey.of(RegistryKeys.DIMENSION_TYPE, Identifier.of(NAMESPACE, "default"));

    public static final String CREATIVE_WORLD_NAME = "creative";
    private static final String CONFIG_FILE_NAME = "multiworld.dat";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(CreativeCommand::register);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MultiWorldCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {
            // Charger les mondes personnalisés à partir des fichiers de configuration
            loadCustomWorlds(server);

            // Créer le monde créatif par défaut s'il n'existe pas déjà
            createWorld(
                    server,
                    CREATIVE_WORLD_NAME,
                    DEFAULT_DIM_TYPE,
                    new FlatChunkGenerator(
                            new FlatChunkGeneratorConfig(
                                    Optional.empty(),
                                    null,
                                    List.of()
                            ).with(
                                    List.of(
                                            new FlatChunkGeneratorLayer(1, Blocks.BEDROCK),
                                            new FlatChunkGeneratorLayer(61, Blocks.DIRT),
                                            new FlatChunkGeneratorLayer(1, Blocks.GRASS_BLOCK)
                                    ),
                                    Optional.empty(),
                                    server.getRegistryManager().getOrThrow(RegistryKeys.BIOME).getOrThrow(BiomeKeys.THE_VOID)
                            )
                    ),
                    BlockPos.ORIGIN,
                    GameMode.CREATIVE
            );
        });
    }

    private static void loadCustomWorlds(MinecraftServer server) {
        String serverWorldFolder = server.getSaveProperties().getLevelName();
        File dimensionsFolder = new File(serverWorldFolder + "/dimensions/" + NAMESPACE);
        
        if (!dimensionsFolder.exists() || !dimensionsFolder.isDirectory()) {
            LOGGER.info("No custom dimensions found.");
            return;
        }
        
        // Parcourir tous les dossiers de dimensions
        File[] worldFolders = dimensionsFolder.listFiles(File::isDirectory);
        
        if (worldFolders == null || worldFolders.length == 0) {
            LOGGER.info("No custom worlds found in dimensions folder.");
            return;
        }
        
        for (File worldFolder : worldFolders) {
            String worldName = worldFolder.getName();
            
            // Vérifier si le monde est déjà chargé correctement
            RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, worldName));
            ServerWorld existingWorld = server.getWorld(worldKey);
            
            if (existingWorld instanceof CustomServerWorld) {
                LOGGER.info("World '{}' already loaded correctly as CustomServerWorld", worldName);
                continue;
            }
            
            // Si le monde existe mais n'est pas une CustomServerWorld, on décharge d'abord
            if (existingWorld != null) {
                unload(server, existingWorld);
            }
            
            // Chercher le fichier de configuration
            File dataFolder = new File(worldFolder, "data");
            File configFile = new File(dataFolder, CONFIG_FILE_NAME);
            
            if (!configFile.exists()) {
                LOGGER.warn("Config file not found for world '{}', skipping", worldName);
                continue;
            }
            
            try {
                // Charger la configuration
                NbtCompound configNbt = NbtIo.readCompressed(configFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
                
                // Extraire les informations de configuration
                long seed = configNbt.getLong("seed").orElseThrow();

                RegistryKey<DimensionType> dimensionType = getDimensionTypeFromString(configNbt.getString("dimensionType").orElseThrow());
                String generatorType = configNbt.getString("generatorType").orElse("overworld");

                // Créer le générateur en fonction du type
                ChunkGenerator generator = createGeneratorFromType(server, generatorType, configNbt);

                // Récupérer la position de spawn si présente
                BlockPos spawnPos = null;
                if (configNbt.contains("spawnPos")) {
                    int[] spawnPosArray = configNbt.getIntArray("spawnPos").orElse(new int[]{0, 64, 0});
                    spawnPos = new BlockPos(spawnPosArray[0], spawnPosArray[1], spawnPosArray[2]);
                }

                // Récupérer le mode de jeu si présent
                GameMode gameMode = null;
                if (configNbt.contains("gameMode")) {
                    int gameModeIndex = configNbt.getInt("gameMode").orElse(0);
                    gameMode = GameMode.byIndex(gameModeIndex);
                }
                
                // Créer le monde
                createWorld(server, worldName, dimensionType, generator, seed, spawnPos, gameMode);
                
            } catch (Exception e) {
                LOGGER.error("Failed to load world '{}': {}", worldName, e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static RegistryKey<DimensionType> getDimensionTypeFromString(String dimensionTypeString) {
        if (dimensionTypeString == null || dimensionTypeString.isEmpty()) {
            return DimensionTypes.OVERWORLD;
        }
        
        String[] parts = dimensionTypeString.split(":");
        if (parts.length != 2) {
            return DimensionTypes.OVERWORLD;
        }
        
        return RegistryKey.of(RegistryKeys.DIMENSION_TYPE, Identifier.of(parts[0], parts[1]));
    }

    private static void saveWorldConfig(
            MinecraftServer server,
            String worldString,
            RegistryKey<DimensionType> dimensionType,
            ChunkGenerator generator,
            String generatorType,
            long seed,
            BlockPos spawnPos,
            GameMode gameMode
    ) {
        String serverWorldFolder = server.getSaveProperties().getLevelName();
        String dimensionPath = serverWorldFolder + "/dimensions/" + NAMESPACE + "/" + worldString + "/data";
        File dimensionDataDir = new File(dimensionPath);
        
        if (!dimensionDataDir.exists() && !dimensionDataDir.mkdirs()) {
            LOGGER.error("Could not create data directory for world '{}'", worldString);
            return;
        }
        
        File configFile = new File(dimensionDataDir, CONFIG_FILE_NAME);
        
        try {
            // Créer le NBT avec les informations de configuration
            NbtCompound configNbt = new NbtCompound();
            configNbt.putString("dimensionType", dimensionType.getValue().toString());
            configNbt.putLong("seed", seed);

            // Déterminer et sauvegarder le type de générateur
            configNbt.putString("generatorType", generatorType);

            // Sauvegarder les configurations spécifiques au générateur plat
            if (generatorType.equals("flat") && generator instanceof FlatChunkGenerator flatGenerator) {
                FlatChunkGeneratorConfig flatConfig = flatGenerator.getConfig();

                // Sauvegarder les couches
                NbtCompound flatConfigNbt = new NbtCompound();
                List<FlatChunkGeneratorLayer> layers = flatConfig.getLayers();
                NbtCompound[] layersNbt = new NbtCompound[layers.size()];

                for (int i = 0; i < layers.size(); i++) {
                    FlatChunkGeneratorLayer layer = layers.get(i);
                    NbtCompound layerNbt = new NbtCompound();
                    layerNbt.putInt("height", layer.getThickness());
                    layerNbt.putString("block", RegistryKey.of(RegistryKeys.BLOCK, layer.getBlockState().getBlock().getRegistryEntry().getKey().orElseThrow().getValue()).getValue().toString());
                    layersNbt[i] = layerNbt;
                }

                NbtCompound layersCompound = new NbtCompound();
                for (int i = 0; i < layersNbt.length; i++) {
                    layersCompound.put("layer" + i, layersNbt[i]);
                }
                layersCompound.putInt("count", layersNbt.length);
                flatConfigNbt.put("layers", layersCompound);

                // Sauvegarder le biome
                flatConfigNbt.putString("biome", flatConfig.getBiome().getKey().orElseThrow().getValue().toString());

                configNbt.put("flatConfig", flatConfigNbt);
            }

            if (spawnPos != null) {
                configNbt.putIntArray("spawnPos", new int[]{spawnPos.getX(), spawnPos.getY(), spawnPos.getZ()});
            }

            if (gameMode != null) {
                configNbt.putInt("gameMode", gameMode.getIndex());
            }
            
            // Sauvegarder le NBT dans un fichier
            NbtIo.writeCompressed(configNbt, configFile.toPath());
        } catch (IOException e) {
            LOGGER.error("Failed to save world configuration for '{}': {}", worldString, e.getMessage());
        }
    }

    private static ChunkGenerator createGeneratorFromType(MinecraftServer server, String generatorType, NbtCompound configNbt) {
        RegistryEntryLookup<ChunkGeneratorSettings> chunkGeneratorSettingsLookup =
            server.getRegistryManager().getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS);
        RegistryEntryLookup<MultiNoiseBiomeSourceParameterList> multiNoisePresetLookup =
            server.getRegistryManager().getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        RegistryEntryLookup<Biome> biomeLookup =
            server.getRegistryManager().getOrThrow(RegistryKeys.BIOME);

        return switch (generatorType) {
            case "nether" -> server.getWorld(World.NETHER).getChunkManager().getChunkGenerator();
            case "end" -> server.getWorld(World.END).getChunkManager().getChunkGenerator();
            case "flat" -> {
                // Reconstruire le générateur plat à partir des configurations sauvegardées
                if (configNbt.contains("flatConfig")) {
                    try {
                        NbtCompound flatConfigNbt = configNbt.getCompound("flatConfig").orElseThrow();
                        NbtCompound layersCompound = flatConfigNbt.getCompound("layers").orElseThrow();
                        int layerCount = layersCompound.getInt("count").orElse(0);

                        List<FlatChunkGeneratorLayer> layers = new ArrayList<>();
                        for (int i = 0; i < layerCount; i++) {
                            NbtCompound layerNbt = layersCompound.getCompound("layer" + i).orElseThrow();
                            int thickness = layerNbt.getInt("height").orElseThrow();
                            String blockId = layerNbt.getString("block").orElseThrow();
                            Identifier blockIdentifier = Identifier.tryParse(blockId);
                            if (blockIdentifier == null) {
                                throw new IllegalArgumentException("Invalid block identifier: " + blockId);
                            }

                            Block block = server.getRegistryManager().getOrThrow(RegistryKeys.BLOCK).get(blockIdentifier);
                            layers.add(new FlatChunkGeneratorLayer(thickness, block));
                        }

                        // Récupérer le biome
                        String biomeId = flatConfigNbt.getString("biome").orElse("minecraft:plains");
                        Identifier biomeIdentifier = Identifier.tryParse(biomeId);
                        RegistryEntry<Biome> biome = biomeLookup.getOrThrow(RegistryKey.of(RegistryKeys.BIOME, biomeIdentifier));

                        // Créer le générateur plat
                        FlatChunkGeneratorConfig flatConfig = new FlatChunkGeneratorConfig(
                                Optional.empty(),
                                biome,
                                null
                        ).with(layers, Optional.empty(), biome);

                        yield new FlatChunkGenerator(flatConfig);
                    } catch (Exception e) {
                        LOGGER.error("Error reconstructing flat generator: {}", e.getMessage());
                        e.printStackTrace();
                    }
                }
                // Fallback vers un générateur plat par défaut
                yield new FlatChunkGenerator(
                        new FlatChunkGeneratorConfig(
                                Optional.empty(),
                                null,
                                List.of()
                        ).with(
                                List.of(
                                        new FlatChunkGeneratorLayer(1, Blocks.BEDROCK),
                                        new FlatChunkGeneratorLayer(61, Blocks.DIRT),
                                        new FlatChunkGeneratorLayer(1, Blocks.GRASS_BLOCK)
                                ),
                                Optional.empty(),
                                biomeLookup.getOrThrow(BiomeKeys.PLAINS)
                        )
                );
                // Fallback vers un générateur plat par défaut
            }
            default -> server.getOverworld().getChunkManager().getChunkGenerator();
        };
    }

    // Rendre la méthode importWorld publique
    public static void importWorld(MinecraftServer server, String worldFolderPath, String worldName) {
        LOGGER.info("Importing world from {} to multiworld:{}", worldFolderPath, worldName);

        File sourceWorldFolder = new File(worldFolderPath);
        if (!sourceWorldFolder.exists() || !sourceWorldFolder.isDirectory()) {
            LOGGER.error("Source world folder {} does not exist or is not a directory", worldFolderPath);
            return;
        }

        try {
            // Récupérer les données du level.dat
            File levelDatFile = new File(sourceWorldFolder, "level.dat");
            if (!levelDatFile.exists()) {
                LOGGER.error("level.dat not found in {}", worldFolderPath);
                return;
            }

            // Lire le fichier level.dat pour obtenir la seed
            NbtCompound levelData = NbtIo.readCompressed(levelDatFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());
            NbtCompound dataTag = levelData.getCompound("Data").orElseThrow();
            NbtCompound worldGenSettings = dataTag.getCompound("WorldGenSettings").orElseThrow();
            long seed = worldGenSettings.getLong("seed").orElseThrow();
            LOGGER.info("Seed from world: {}", seed);

            // Préparer les chemins de destination
            String serverWorldFolder = server.getSaveProperties().getLevelName();

            // Définir les clés de registre pour les dimensions
            RegistryKey<World> overWorldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, worldName));
            RegistryKey<World> netherWorldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, worldName + "_nether"));
            RegistryKey<World> endWorldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, worldName + "_end"));

            // Préparer les dossiers et copier les fichiers AVANT de créer les mondes
            // Overworld
            copyWorldFiles(sourceWorldFolder, serverWorldFolder, overWorldKey);

            // Nether
            File netherFolder = new File(sourceWorldFolder, "DIM-1");
            if (netherFolder.exists()) {
                copyWorldFiles(netherFolder, serverWorldFolder, netherWorldKey);
            }

            // End
            File endFolder = new File(sourceWorldFolder, "DIM1");
            if (endFolder.exists()) {
                copyWorldFiles(endFolder, serverWorldFolder, endWorldKey);
            }

            // Créer les mondes (overworld, nether, end) avec la même seed
            // Les fichiers sont déjà copiés à ce stade
            createWorldWithDimensions(server, worldName, seed);

            // Obtenir les références aux nouveaux mondes
            ServerWorld overworld = server.getWorld(overWorldKey);
            ServerWorld nether = server.getWorld(netherWorldKey);
            ServerWorld end = server.getWorld(endWorldKey);

            if (overworld == null) {
                LOGGER.error("Failed to create overworld dimension");
                return;
            }

            // Créer le dossier multiworld/worldName pour stocker les données des joueurs
            File multiworldFolder = new File(serverWorldFolder, NAMESPACE);
            if (!multiworldFolder.exists() && !multiworldFolder.mkdirs()) {
                LOGGER.error("Could not create multiworld directory");
                return;
            }

            File worldNameFolder = new File(multiworldFolder, worldName);
            if (!worldNameFolder.exists() && !worldNameFolder.mkdirs()) {
                LOGGER.error("Could not create world directory");
                return;
            }

            // Traiter les données des joueurs
            File playerDataFolder = new File(sourceWorldFolder, "playerdata");
            if (playerDataFolder.exists() && playerDataFolder.isDirectory()) {
                File[] playerDataFiles = playerDataFolder.listFiles((dir, name) -> name.endsWith(".dat"));
                if (playerDataFiles != null) {
                    for (File playerDataFile : playerDataFiles) {
                        try {
                            // Lire les données du joueur
                            NbtCompound playerData = NbtIo.readCompressed(playerDataFile.toPath(), NbtSizeTracker.ofUnlimitedBytes());

                            String uuid = playerDataFile.getName().replace(".dat", "");

                            // Mettre à jour le tag respawn dimension
                            updateDimensionInTag(playerData, "respawn", worldName);

                            // Mettre à jour le tag Dimension directement dans les données du joueur
                            String dimensionTagString = playerData.getString("Dimension").orElse(World.OVERWORLD.getValue().toString());
                            playerData.putString("Dimension", convertDimensionName(dimensionTagString, worldName));

                            // Mettre à jour le tag LastDeathLocation dimension
                            updateDimensionInTag(playerData, "LastDeathLocation", worldName);

                            // Convertir en JSON
                            JsonObject playerJson = new JsonObject();
                            playerJson.put("player", playerData.toString());

                            // Ajouter les statistiques si elles existent
                            File statsFile = new File(sourceWorldFolder, "stats/" + uuid + ".json");
                            if (statsFile.exists()) {
                                try (FileReader statsReader = new FileReader(statsFile)) {
                                    JsonObject statsJson = (JsonObject) Jsoner.deserialize(statsReader);
                                    playerJson.put("stats", statsJson);
                                }
                            }

                            // Ajouter les avancements si ils existent
                            File advancementsFile = new File(sourceWorldFolder, "advancements/" + uuid + ".json");
                            if (advancementsFile.exists()) {
                                try (FileReader advancementsReader = new FileReader(advancementsFile)) {
                                    JsonObject advancementsJson = (JsonObject) Jsoner.deserialize(advancementsReader);
                                    playerJson.put("advancements", advancementsJson);
                                }
                            }

                            // Écrire le fichier JSON
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

        } catch (Exception e) {
            LOGGER.error("Error importing world: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private static void updateDimensionInTag(NbtCompound parentTag, String tagName, String worldName) {
        Optional<NbtCompound> tagOpt = parentTag.getCompound(tagName);
        if (tagOpt.isPresent()) {
            NbtCompound tag = tagOpt.get();
            String dimension = tag.getString("dimension").orElse(World.OVERWORLD.getValue().toString());
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

    private static void copyWorldFiles(File sourceFolder, String serverWorldFolder, RegistryKey<World> worldKey) {
        // Copier les fichiers de région, POI, entités et données
        String worldDirectory = serverWorldFolder + "/dimensions/" +
                worldKey.getValue().getNamespace() + "/" + worldKey.getValue().getPath();

        // Copier les fichiers de région
        copyFilesBeforeWorldCreation(sourceFolder, "region", worldDirectory);

        // Copier les fichiers POI
        copyFilesBeforeWorldCreation(sourceFolder, "poi", worldDirectory);

        // Copier les fichiers d'entités
        copyFilesBeforeWorldCreation(sourceFolder, "entities", worldDirectory);

        // Copier les fichiers de données
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

        // Créer le dossier de destination s'il n'existe pas
        File destinationFolder = new File(destinationPath + "/" + type);
        if (!destinationFolder.exists() && !destinationFolder.mkdirs()) {
            LOGGER.error("Could not create destination folder: {}", destinationFolder);
            return;
        }

        // Copier les fichiers
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
         RegistryEntryLookup<ChunkGeneratorSettings> chunkGeneratorSettingsLookup = server.getRegistryManager().getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS);
         RegistryEntryLookup<MultiNoiseBiomeSourceParameterList> multiNoisePresetLookup = server.getRegistryManager().getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
         RegistryEntryLookup<Biome> biomeLookup = server.getRegistryManager().getOrThrow(RegistryKeys.BIOME);

         ChunkGenerator overworldGenerator = new NoiseChunkGenerator(MultiNoiseBiomeSource.create(multiNoisePresetLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)), chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.OVERWORLD));
         ChunkGenerator netherGenerator = new NoiseChunkGenerator(MultiNoiseBiomeSource.create(multiNoisePresetLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)), chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.NETHER));
         ChunkGenerator endGenerator = new NoiseChunkGenerator(TheEndBiomeSource.createVanilla(biomeLookup), chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.END));
//
//         createWorld(server, worldString, DimensionTypes.OVERWORLD, new NoiseChunkGenerator(MultiNoiseBiomeSource.create(multiNoisePresetLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)), chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.OVERWORLD)), seed, null, null);
//         createWorld(server, worldString + "_nether", DimensionTypes.THE_NETHER, new NoiseChunkGenerator(MultiNoiseBiomeSource.create(multiNoisePresetLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)), chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.NETHER)), seed, null, null);
//         createWorld(server, worldString + "_end", DimensionTypes.THE_END, new NoiseChunkGenerator(TheEndBiomeSource.createVanilla(biomeLookup), chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.END)), seed, null, null);


        // Créer les trois dimensions (Overworld, Nether, End)
        createWorld(server, worldString, DimensionTypes.OVERWORLD, server.getOverworld().getChunkManager().getChunkGenerator(), seed, null, null);
        createWorld(server, worldString + "_nether", DimensionTypes.THE_NETHER, server.getWorld(World.NETHER).getChunkManager().getChunkGenerator(), seed, null, null);
        createWorld(server, worldString + "_end", DimensionTypes.THE_END, server.getWorld(World.END).getChunkManager().getChunkGenerator(), seed, null, null);
    }

    private static void createWorld(MinecraftServer server, String worldString, RegistryKey<DimensionType> dimensionType, ChunkGenerator generator, BlockPos spawnPos, GameMode gameMode) {
        createWorld(server, worldString, dimensionType, generator, server.getOverworld().getSeed(), spawnPos, gameMode);
    }

    private static void createWorld(
            MinecraftServer server,
            String worldString,
            RegistryKey<DimensionType> dimensionType,
            ChunkGenerator generator,
            long seed,
            BlockPos spawnPos,
            GameMode gameMode
    ) {
        RegistryKey<World> worldKey = RegistryKey.of(
                RegistryKeys.WORLD,
                Identifier.of(NAMESPACE, worldString)
        );

        if (server.getWorld(worldKey) != null) {
            // The game loaded our dimension but not with our CustomServerWorld, we need to fix that to have the custom generation and stuff
            ServerWorld world = server.getWorld(worldKey);
            if (world instanceof CustomServerWorld) {
                // LOGGER.info("World {} already exists, skipping creation.", worldString);
                return;
            } else {
                // kick players
                for (ServerPlayerEntity player : world.getPlayers()) {
                    Utils.saveInv(player, getBaseWorldName(worldKey.getValue().getPath()));
                    Utils.loadInv(player, "overworld");
                }
                unload(server, world);
            }
        }

        RegistryEntry<DimensionType> dimensionTypeEntry = server.getRegistryManager().getOrThrow(RegistryKeys.DIMENSION_TYPE).getOptional(dimensionType).orElseThrow();


        DimensionOptions options = new DimensionOptions(
                dimensionTypeEntry,
                generator
        );

        DynamicRegistryManager registryManager = server.getCombinedDynamicRegistries().getCombinedRegistryManager();
        SimpleRegistry<DimensionOptions> dimensionsRegistry = (SimpleRegistry<DimensionOptions>) registryManager.getOrThrow(RegistryKeys.DIMENSION);

        boolean isFrozen = ((CustomSimpleRegistry<?>) dimensionsRegistry).multiWorld$isFrozen();
        ((CustomSimpleRegistry<?>) dimensionsRegistry).multiWorld$setFrozen(false);

        RegistryKey<DimensionOptions> dimensionKey = RegistryKey.of(RegistryKeys.DIMENSION, worldKey.getValue());
        if (!dimensionsRegistry.contains(dimensionKey)) {
            dimensionsRegistry.add(
                    dimensionKey,
                    options,
                    RegistryEntryInfo.DEFAULT
            );
        }

        ((CustomSimpleRegistry<?>) dimensionsRegistry).multiWorld$setFrozen(isFrozen);

        LevelProperties levelProperties = (LevelProperties) server.getSaveProperties().getMainWorldProperties();
        CustomServerWorldProperties newLevelProperties = new CustomServerWorldProperties(levelProperties, seed);

        CustomServerWorld.Constructor worldConstructor = CustomServerWorld::new;
        CustomServerWorld addedWorld = worldConstructor.create(server, worldKey, options, newLevelProperties, seed);

        if (gameMode != null) {
            ((ServerWorldAccessor) addedWorld).getWorldProperties().setGameMode(gameMode);
        }

        ((MinecraftServerMixin) server).getWorlds().put(addedWorld.getRegistryKey(), addedWorld);

        if (spawnPos != null) {
            newLevelProperties.setSpawnPos(spawnPos, 0.0F);
        } else {
            MinecraftServerMixin.invokeSetupSpawn(addedWorld, newLevelProperties, false, false);
        }

        ServerWorldEvents.LOAD.invoker().onWorldLoad(server, addedWorld);

        saveWorldConfig(
                server,
                worldString,
                dimensionType,
                generator,
                generator instanceof FlatChunkGenerator ? "flat" : worldString.endsWith("_nether") ? "nether" : worldString.endsWith("_end") ? "end" : "overworld",
                seed,
                spawnPos,
                gameMode
        );

        LOGGER.info("Successfully loaded world '{}'", worldString);
    }

    private static void unload(MinecraftServer server, ServerWorld world) {
        RegistryKey<World> dimensionKey = world.getRegistryKey();

        if (((MinecraftServerMixin) server).getWorlds().remove(dimensionKey, world)) {
            world.save(new ProgressListener() {
                @Override
                public void setTitle(Text title) {}

                @Override
                public void setTitleAndTask(Text title) {}

                @Override
                public void setTask(Text task) {}

                @Override
                public void progressStagePercentage(int percentage) {}

                @Override
                public void setDone() {}
            }, true, false);

            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, world);

            DynamicRegistryManager registryManager = server.getCombinedDynamicRegistries().getCombinedRegistryManager();
            SimpleRegistry<DimensionOptions> dimensionsRegistry = (SimpleRegistry<DimensionOptions>) registryManager.getOrThrow(RegistryKeys.DIMENSION);
            CustomSimpleRegistry.remove(dimensionsRegistry, dimensionKey.getValue());
        }
    }

    public static void deleteWorld(MinecraftServer server, String worldName) {
        LOGGER.info("Deleting world: {}", worldName);

        // Identifier les clés de registre pour toutes les dimensions
        RegistryKey<World> overWorldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, worldName));
        RegistryKey<World> netherWorldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, worldName + "_nether"));
        RegistryKey<World> endWorldKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(NAMESPACE, worldName + "_end"));

        // Récupérer les références aux mondes
        ServerWorld overworld = server.getWorld(overWorldKey);
        ServerWorld nether = server.getWorld(netherWorldKey);
        ServerWorld end = server.getWorld(endWorldKey);

        // Déclencher les événements de déchargement pour chaque monde existant
        if (overworld != null) {
            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, overworld);
        }

        if (nether != null) {
            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, nether);
        }

        if (end != null) {
            ServerWorldEvents.UNLOAD.invoker().onWorldUnload(server, end);
        }

        // Retirer les dimensions du registre
        DynamicRegistryManager registryManager = server.getCombinedDynamicRegistries().getCombinedRegistryManager();
        SimpleRegistry<DimensionOptions> dimensionsRegistry = (SimpleRegistry<DimensionOptions>) registryManager.getOrThrow(RegistryKeys.DIMENSION);

        // Retirer l'overworld du registre
        if (overworld != null) {
            CustomSimpleRegistry.remove(dimensionsRegistry, overWorldKey.getValue());
        }

        // Retirer le nether du registre s'il existe
        if (nether != null) {
            CustomSimpleRegistry.remove(dimensionsRegistry, netherWorldKey.getValue());
        }

        // Retirer l'end du registre s'il existe
        if (end != null) {
            CustomSimpleRegistry.remove(dimensionsRegistry, endWorldKey.getValue());
        }

        // Récupérer le dossier du serveur
        String serverWorldFolder = server.getSaveProperties().getLevelName();

        // Supprimer les fichiers des dimensions
        if (overworld != null) {
            deleteWorldFiles(serverWorldFolder, overWorldKey);
            ((MinecraftServerMixin) server).getWorlds().remove(overWorldKey);
        }

        // Supprimer les fichiers du nether s'il existe
        if (nether != null) {
            deleteWorldFiles(serverWorldFolder, netherWorldKey);
            ((MinecraftServerMixin) server).getWorlds().remove(netherWorldKey);
        }

        // Supprimer les fichiers de l'end s'il existe
        if (end != null) {
            deleteWorldFiles(serverWorldFolder, endWorldKey);
            ((MinecraftServerMixin) server).getWorlds().remove(endWorldKey);
        }

        // Supprimer les données des joueurs liées à ce monde
        File multiworldFolder = new File(serverWorldFolder, NAMESPACE);
        File worldNameFolder = new File(multiworldFolder, worldName);
        if (worldNameFolder.exists()) {
            deleteFolder(worldNameFolder);
        }

        LOGGER.info("World deletion completed: {}", worldName);
    }

    private static void deleteWorldFiles(String serverWorldFolder, RegistryKey<World> worldKey) {
        String worldPath = serverWorldFolder + "/dimensions/" +
                worldKey.getValue().getNamespace() + "/" + worldKey.getValue().getPath();
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
