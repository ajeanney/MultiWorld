package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;
import static com.gmail.anthony17j.multiworld.CustomServerWorld.isWorldWithNether;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow public abstract World getWorld();

    @Inject(at = @At("HEAD"), method = "tickPortalTeleportation", cancellable = true)
    public void tickNetherPortal(CallbackInfo ci) {
        if (!isWorldWithNether(this.getWorld().getRegistryKey())) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "canTeleportBetween",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;",
                    ordinal = 0
            )
    )
    private RegistryKey<World> redirectFromGetRegistryKey(World world) {
        return getMockRegistryKey(world.getRegistryKey());
    }

    @Redirect(
            method = "canTeleportBetween",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;",
                    ordinal = 1
            )
    )
    private RegistryKey<World> redirectToGetRegistryKey(World world) {
        return getMockRegistryKey(world.getRegistryKey());
    }
}