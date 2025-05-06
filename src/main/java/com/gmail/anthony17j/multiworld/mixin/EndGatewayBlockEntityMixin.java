package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.block.entity.EndGatewayBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.isCustomEndWorld;

@Mixin(EndGatewayBlockEntity.class)
public abstract class EndGatewayBlockEntityMixin {

    @Redirect(
            method = "getOrCreateExitPortalPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"
            )
    )
    private RegistryKey<World> redirectGetRegistryKey(ServerWorld world) {
        RegistryKey<World> originalKey = world.getRegistryKey();

        if (isCustomEndWorld(originalKey)) {
            return World.END;
        }

        return originalKey;
    }
}