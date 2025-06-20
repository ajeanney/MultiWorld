package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.isCustomEndWorld;
import static com.gmail.anthony17j.multiworld.CustomServerWorld.isWorldWithEnd;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {

    @Inject(at = @At("HEAD"), method = "onEntityCollision", cancellable = true)
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, CallbackInfo ci) {
        if (!isWorldWithEnd(entity.getWorld().getRegistryKey())) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "onEntityCollision",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;"
            )
    )
    private RegistryKey<World> redirectGetRegistryKey(World world) {
        RegistryKey<World> originalKey = world.getRegistryKey();

        if (isCustomEndWorld(originalKey)) {
            return World.END;
        }

        return originalKey;
    }

    @ModifyVariable(
            method = "createTeleportTarget(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/TeleportTarget;",
            at = @At(value = "STORE"),
            ordinal = 0,
            name = "bl"
    )
    private boolean modifyEndFlag(boolean bl, ServerWorld world, Entity entity, BlockPos pos) {
        RegistryKey<World> currentWorldKey = world.getRegistryKey();

        if (Objects.equals(currentWorldKey.getValue().getNamespace(), NAMESPACE)) {
            String path = currentWorldKey.getValue().getPath();
            boolean isEndWorld = !path.endsWith("_end");

            MultiWorld.LOGGER.info("Modified END flag for {} : {}", path, isEndWorld);
            return isEndWorld;
        }

        return bl;
    }

    @Redirect(
            method = "createTeleportTarget(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/world/TeleportTarget;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getWorld(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/server/world/ServerWorld;"
            )
    )
    private ServerWorld redirectGetWorld(net.minecraft.server.MinecraftServer server, RegistryKey<World> registryKey, ServerWorld world, Entity entity, BlockPos pos) {
        ServerWorld targetWorld = server.getWorld(registryKey);
        ServerWorld initialWorld = entity.getServer().getWorld(entity.getWorld().getRegistryKey());

        if (Objects.equals(initialWorld.getRegistryKey().getValue().getNamespace(), NAMESPACE)) {
            String destinationName = getDestinationName(initialWorld);

            RegistryKey<World> destinationKey = RegistryKey.of(
                    RegistryKeys.WORLD,
                    Identifier.of(NAMESPACE, destinationName)
            );
            ServerWorld destinationWorld = initialWorld.getServer().getWorld(destinationKey);

            if (destinationWorld != null) {
                MultiWorld.LOGGER.info("Teleportation redirect for End from {} to {}",
                        initialWorld.getRegistryKey().getValue(), destinationKey.getValue());
                return destinationWorld;
            }
        }

        return targetWorld;
    }

    @Unique
    private static @NotNull String getDestinationName(ServerWorld initialWorld) {
        String originName = initialWorld.getRegistryKey().getValue().getPath();
        String destinationName;

        if (originName.endsWith("_end")) {
            destinationName = originName.substring(0, originName.length() - 4);
        }
        else {
            destinationName = originName + "_end";
        }
        return destinationName;
    }
}