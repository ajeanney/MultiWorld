package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getBaseWorldName;
import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Redirect(
            method = "getRespawnTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getOverworld()Lnet/minecraft/server/world/ServerWorld;"
            )
    )
    private ServerWorld redirectGetOverworld(MinecraftServer server) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        ServerWorld currentWorld = player.getWorld();

        if (Objects.equals(currentWorld.getRegistryKey().getValue().getNamespace(), NAMESPACE)) {
            String worldName = getBaseWorldName(currentWorld.getRegistryKey().getValue().getPath());

            RegistryKey<World> targetKey = RegistryKey.of(
                    RegistryKeys.WORLD,
                    Identifier.of(NAMESPACE, worldName)
            );

            ServerWorld targetWorld = server.getWorld(targetKey);
            if (targetWorld != null) {
                MultiWorld.LOGGER.info("Redirect to multiworld : {}", targetKey.getValue());
                return targetWorld;
            }
        }

        return server.getOverworld();
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
                    target = "Lnet/minecraft/server/network/ServerPlayerEntity;getWorld()Lnet/minecraft/server/world/ServerWorld;"
            )
    )
    private ServerWorld redirectGetWorldInWorldChanged(ServerPlayerEntity player) {
        return player.getWorld();
    }
}
