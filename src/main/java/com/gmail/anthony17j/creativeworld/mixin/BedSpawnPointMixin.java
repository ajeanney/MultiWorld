package com.gmail.anthony17j.creativeworld.mixin;

import com.gmail.anthony17j.creativeworld.Main;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class BedSpawnPointMixin {
    @Inject(at = @At("HEAD"), method = "setSpawnPoint", cancellable = true)
    public void setSpawnPoint(RegistryKey<World> dimension, BlockPos pos, float angle, boolean spawnPointSet, boolean sendMessage, CallbackInfo ci) {
        if (dimension == Main.CREATIVE_KEY) {
            ci.cancel();
        }
    }
}