package com.gmail.anthony17j.creativeworld;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.StructuresConfig;
import net.minecraft.world.gen.chunk.VerticalBlockSample;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class VoidChunkGenerator extends ChunkGenerator {


    public static final Codec<VoidChunkGenerator> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source")
                                    .forGetter((generator) -> generator.biomeSource)
                    )
                    .apply(instance, instance.stable(VoidChunkGenerator::new))
    );

    public VoidChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource, new StructuresConfig(false));
    }

    @Override
    protected Codec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public ChunkGenerator withSeed(long seed) {
        return this;
    }

    @Override
    public void buildSurface(ChunkRegion region, Chunk chunk) {
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Executor executor, StructureAccessor accessor, Chunk chunk) {
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmapType, HeightLimitView heightLimitView) {
        return 0;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView heightLimitView) {
        return new VerticalBlockSample(0, new BlockState[0]);
    }
}
