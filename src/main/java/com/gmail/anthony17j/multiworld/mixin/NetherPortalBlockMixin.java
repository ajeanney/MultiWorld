package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Redirect(
            method = "createTeleportTarget(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/TeleportTarget;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getWorld(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/server/world/ServerWorld;"
            )
    )
    private ServerWorld redirectGetWorld(MinecraftServer server, RegistryKey<World> registryKey, ServerWorld world, Entity entity) {
        if (Objects.equals(world.getRegistryKey().getValue().getNamespace(), NAMESPACE)) {
            String originName = world.getRegistryKey().getValue().getPath();
            String destinationName = originName.endsWith("_nether")
                    ? originName.substring(0, originName.length() - 7)
                    : originName + "_nether";

            RegistryKey<World> destinationKey = RegistryKey.of(
                    RegistryKeys.WORLD,
                    Identifier.of(NAMESPACE, destinationName)
            );
            ServerWorld destinationWorld = server.getWorld(destinationKey);

            if (destinationWorld != null) {
                MultiWorld.LOGGER.info("Portal redirect to: {}", destinationKey.getValue());
                return destinationWorld;
            }
        }
        return server.getWorld(registryKey);
    }

    @Redirect(
            method = "createTeleportTarget(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/TeleportTarget;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ServerWorld;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"
            )
    )
    private RegistryKey<World> redirectGetRegistryKey(ServerWorld serverWorld) {
        RegistryKey<World> originalKey = serverWorld.getRegistryKey();
        String path = originalKey.getValue().getPath();
        String namespace = originalKey.getValue().getNamespace();

        if (Objects.equals(namespace, MultiWorld.NAMESPACE) && path.endsWith("_nether")) {
            return World.NETHER;
        }

        return originalKey;
    }
}