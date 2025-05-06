package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;

@Mixin(MinecraftDedicatedServer.class)
public abstract class MinecraftDedicatedServerMixin {

    @Redirect(
            method = "isWorldAllowed",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"
            )
    )
    private RegistryKey<World> redirectGetRegistryKey(World world) {
        return getMockRegistryKey(world.getRegistryKey());
    }
}