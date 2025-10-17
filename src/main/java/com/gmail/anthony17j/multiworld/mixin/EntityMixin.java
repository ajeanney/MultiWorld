package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.WorldProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;
import static com.gmail.anthony17j.multiworld.CustomServerWorld.isWorldWithNether;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow public abstract World getEntityWorld();

    @Inject(at = @At("HEAD"), method = "tickPortalTeleportation", cancellable = true)
    public void tickNetherPortal(CallbackInfo ci) {
        if (!isWorldWithNether(this.getEntityWorld().getRegistryKey())) {
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

    @Redirect(
            method = "getWorldSpawnPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getSpawnPoint()Lnet/minecraft/world/WorldProperties$SpawnPoint;"
            )
    )
    private WorldProperties.SpawnPoint redirectGetSpawnPoint(ServerWorld world) {
        if (Objects.equals(world.getRegistryKey().getValue().getNamespace(), NAMESPACE)) {
            MultiWorld.LOGGER.info("Redirect getWorldSpawnPos to entity world spawn point: {}", world.getRegistryKey().getValue());
            return world.getLevelProperties().getSpawnPoint();
        }

        return world.getSpawnPoint();
    }
}