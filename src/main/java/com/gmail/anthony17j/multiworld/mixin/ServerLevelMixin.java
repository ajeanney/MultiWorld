package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Shadow
    public abstract ServerChunkCache getChunkSource();

    @ModifyArgs(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;<init>(Lnet/minecraft/world/level/storage/WritableLevelData;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/core/Holder;ZZJI)V"
            )
    )
    private static void modifySeedForWorldConstructor(Args args) {
        ResourceKey<Level> worldKey = args.get(1);
        long seed = args.get(6);

        if (Objects.equals(worldKey.identifier().getNamespace(), NAMESPACE)) {
            args.set(6, BiomeManager.obfuscateSeed(seed));
        }
    }

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/WorldOptions;seed()J"
            )
    )
    private long redirectGetSeed(WorldOptions instance,
                                 MinecraftServer server, Executor workerExecutor,
                                 LevelStorageSource.LevelStorageAccess session, ServerLevelData properties,
                                 ResourceKey<Level> worldKey, LevelStem dimensionOptions,
                                 boolean debugWorld, long seed, List<CustomSpawner> spawners,
                                 boolean shouldTickTime, RandomSequences randomSequences) {
        if (Objects.equals(worldKey.identifier().getNamespace(), NAMESPACE)) {
            return seed;
        }
        return instance.seed();
    }
}
