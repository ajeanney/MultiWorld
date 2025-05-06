package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StatHandler.class)
public class StatMixin {
    @Inject(at = @At("HEAD"), method = "setStat", cancellable = true)
    public void setStat(PlayerEntity player, Stat<?> stat, int value, CallbackInfo ci) {
        if (player.getEntityWorld().getRegistryKey() == MultiWorld.CREATIVE_KEY) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "increaseStat", cancellable = true)
    public void increaseStat(PlayerEntity player, Stat<?> stat, int value, CallbackInfo ci) {
        if (player.getEntityWorld().getRegistryKey() == MultiWorld.CREATIVE_KEY) {
            ci.cancel();
        }
    }
}
