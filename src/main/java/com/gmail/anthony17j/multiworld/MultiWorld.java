package com.gmail.anthony17j.multiworld;

import com.gmail.anthony17j.multiworld.command.createWorldCommand;
import com.gmail.anthony17j.multiworld.command.creativeCommand;
import com.gmail.anthony17j.multiworld.mixin.MinecraftServerMixin;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.Blocks;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;
import net.minecraft.world.level.LevelProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class MultiWorld implements ModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger(MultiWorld.class);

    public static final String NAMESPACE = "multiworld";
    public static final RegistryKey<DimensionType> DEFAULT_DIM_TYPE = RegistryKey.of(RegistryKeys.DIMENSION_TYPE, Identifier.of(NAMESPACE, "default"));

    public static final String CREATIVE_WORLD_NAME = "creative";

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(creativeCommand::register);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> createWorldCommand.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register((MinecraftServer server) -> {

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

            createWorldWithDimensions(server, "test", -2831322041033427943L);
        });
    }

    private static void createWorldWithDimensions(MinecraftServer server, String worldString, long seed) {
//         RegistryEntryLookup<ChunkGeneratorSettings> chunkGeneratorSettingsLookup = server.getRegistryManager().getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS);
//         RegistryEntryLookup<MultiNoiseBiomeSourceParameterList> multiNoisePresetLookup = server.getRegistryManager().getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
//         RegistryEntryLookup<Biome> biomeLookup = server.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
//
//         createWorld(server, worldString, DimensionTypes.OVERWORLD, new NoiseChunkGenerator(MultiNoiseBiomeSource.create(multiNoisePresetLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)), chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.OVERWORLD)), seed, null, null);
//         createWorld(server, worldString + "_nether", DimensionTypes.THE_NETHER, new NoiseChunkGenerator(MultiNoiseBiomeSource.create(multiNoisePresetLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.NETHER)), chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.NETHER)), seed, null, null);
//         createWorld(server, worldString + "_end", DimensionTypes.THE_END, new NoiseChunkGenerator(TheEndBiomeSource.createVanilla(biomeLookup), chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.END)), seed, null, null);

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
            // LOGGER.info("World {} already exists", worldString);
            return;
        }

        RegistryEntry<DimensionType> dimensionTypeEntry = server.getRegistryManager().getOrThrow(RegistryKeys.DIMENSION_TYPE).getOptional(dimensionType).orElseThrow();


        DimensionOptions options = new DimensionOptions(
                dimensionTypeEntry,
                generator
        );

        DynamicRegistryManager registryManager = server.getCombinedDynamicRegistries().getCombinedRegistryManager();
        SimpleRegistry<DimensionOptions> dimensionsRegistry = (SimpleRegistry<DimensionOptions>) registryManager.getOrThrow(RegistryKeys.DIMENSION);

        boolean isFrozen = ((CustomSimpleRegistry<?>) dimensionsRegistry).creativeWorld$isFrozen();
        ((CustomSimpleRegistry<?>) dimensionsRegistry).creativeWorld$setFrozen(false);

        RegistryKey<DimensionOptions> dimensionKey = RegistryKey.of(RegistryKeys.DIMENSION, worldKey.getValue());
        if (!dimensionsRegistry.contains(dimensionKey)) {
            dimensionsRegistry.add(
                    dimensionKey,
                    options,
                    RegistryEntryInfo.DEFAULT
            );
        }

        ((CustomSimpleRegistry<?>) dimensionsRegistry).creativeWorld$setFrozen(isFrozen);

        LevelProperties levelProperties = (LevelProperties) server.getSaveProperties().getMainWorldProperties();
        CustomServerWorldProperties newLevelProperties = new CustomServerWorldProperties(levelProperties, seed, gameMode);

        CustomServerWorld.Constructor worldConstructor = CustomServerWorld::new;
        CustomServerWorld addedWorld = worldConstructor.create(server, worldKey, options, newLevelProperties, seed);

        ((MinecraftServerMixin) server).getWorlds().put(addedWorld.getRegistryKey(), addedWorld);

        if (spawnPos != null) {
            newLevelProperties.setSpawnPos(spawnPos, 0.0F);
        } else {
            MinecraftServerMixin.invokeSetupSpawn(addedWorld, newLevelProperties, false, false);
        }

        ServerWorldEvents.LOAD.invoker().onWorldLoad(server, addedWorld);

        addedWorld.tick(() -> true);
    }
}