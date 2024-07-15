package com.gmail.anthony17j.creativeworld.mixin;

import com.gmail.anthony17j.creativeworld.Main;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class NetherPortalMixin {
    @Shadow public abstract World getEntityWorld();

    @Inject(at = @At("HEAD"), method = "tickPortalTeleportation", cancellable = true)
    public void tickNetherPortal(CallbackInfo ci) {
        if (this.getEntityWorld().getRegistryKey() == Main.CREATIVE_KEY) {
            ci.cancel();
        }
    }
}