package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(MinecraftServer.class)
public interface IMinecraftServerMixin {
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getWorlds();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getSession();

    @Invoker("setInitialSpawn")
    static void invokeSetupSpawn(ServerLevel world, ServerLevelData worldProperties, boolean bonusChest, boolean debugWorld, LevelLoadListener loadProgress) {
        throw new AssertionError();
    }
}
