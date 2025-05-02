package com.gmail.anthony17j.creativeworld.mixin;

import com.gmail.anthony17j.creativeworld.CreativeWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public class BedSpawnPointMixin {
    @Inject(at = @At("HEAD"), method = "setSpawnPoint", cancellable = true)
    public void setSpawnPoint(ServerPlayerEntity.Respawn respawn, boolean sendMessage, CallbackInfo ci) {
        if (respawn != null && respawn.dimension() == CreativeWorld.CREATIVE_KEY) {
            ci.cancel();
        }
    }
}