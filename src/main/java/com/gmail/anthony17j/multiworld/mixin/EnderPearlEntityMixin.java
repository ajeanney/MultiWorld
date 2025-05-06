package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;

@Mixin(EnderPearlEntity.class)
public abstract class EnderPearlEntityMixin {

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