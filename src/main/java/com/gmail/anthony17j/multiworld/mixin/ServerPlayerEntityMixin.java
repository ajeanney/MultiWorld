package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.*;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Redirect(
            method = "getRespawnTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getWorld(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/server/world/ServerWorld;"
            )
    )
    private ServerWorld redirectGetWorld(MinecraftServer server, RegistryKey<World> registryKey) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        ServerWorld currentWorld = player.getEntityWorld();

        ServerWorld originalWorld = server.getWorld(registryKey);
        if (originalWorld.getRegistryKey() == World.OVERWORLD ) {
            if (Objects.equals(currentWorld.getRegistryKey().getValue().getNamespace(), NAMESPACE)) {
                String worldName = getBaseWorldName(currentWorld.getRegistryKey().getValue().getPath());
                RegistryKey<World> targetKey = getRegistryKey(worldName);
                ServerWorld targetWorld = server.getWorld(targetKey);
                if (targetWorld != null) {
                    MultiWorld.LOGGER.info("Redirect to multiworld : {}", targetKey.getValue());
                    return targetWorld;
                }
            }
            return server.getOverworld();
        }

        return originalWorld;
    }

    @Redirect(
            method = "worldChanged",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"
            )
    )
    private RegistryKey<World> redirectGetRegistryKeyInWorldChanged(ServerWorld world) {
        return getMockRegistryKey(world.getRegistryKey());
    }

    @Redirect(
            method = "worldChanged",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getEntityWorld()Lnet/minecraft/server/world/ServerWorld;"
            )
    )
    private ServerWorld redirectGetEntityWorldInWorldChanged(ServerPlayerEntity player) {
        return player.getEntityWorld();
    }
}
