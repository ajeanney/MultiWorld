package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.spawner.SpecialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"
            )
    )
    private RegistryKey<?> redirectGetRegistryKey(ServerWorld instance) {
        return getMockRegistryKey(instance);
    }

    // These two are probably not needed, but just in case
    @ModifyArgs(
            method = "<init>*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;<init>(Lnet/minecraft/world/MutableWorldProperties;Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/registry/DynamicRegistryManager;Lnet/minecraft/registry/entry/RegistryEntry;ZZJI)V"
            )
    )
    private static void modifySeedForWorldConstructor(Args args) {
        RegistryKey<World> worldKey = args.get(1);
        long seed = args.get(6);

        if (Objects.equals(worldKey.getValue().getNamespace(), NAMESPACE)) {
            args.set(6, BiomeAccess.hashSeed(seed));
        }
    }

    @Redirect(
            method = "<init>*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/gen/GeneratorOptions;getSeed()J"
            )
    )
    private long redirectGetSeed(net.minecraft.world.gen.GeneratorOptions instance,
                                 MinecraftServer server, Executor workerExecutor,
                                 LevelStorage.Session session, ServerWorldProperties properties,
                                 RegistryKey<World> worldKey, DimensionOptions dimensionOptions,
                                 WorldGenerationProgressListener worldGenerationProgressListener,
                                 boolean debugWorld, long seed, List<SpecialSpawner> spawners,
                                 boolean shouldTickTime, RandomSequencesState randomSequencesState) {
        if (Objects.equals(worldKey.getValue().getNamespace(), NAMESPACE)) {
            return seed;
        }
        return instance.getSeed();
    }
}