package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.core.BlockPos;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.isCustomEndWorld;
import static com.gmail.anthony17j.multiworld.CustomServerWorld.isWorldWithEnd;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;
import static net.minecraft.core.registries.Registries.DIMENSION;

@Mixin(EndPortalBlock.class)
public abstract class EndPortalBlockMixin {

    @Inject(at = @At("HEAD"), method = "entityInside", cancellable = true)
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity, InsideBlockEffectApplier handler, boolean bl, CallbackInfo ci) {
        if (!isWorldWithEnd(entity.level().dimension())) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "entityInside",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"
            )
    )
    private ResourceKey<Level> redirectGetRegistryKey(Level world) {
        ResourceKey<Level> originalKey = world.dimension();

        if (isCustomEndWorld(originalKey)) {
            return Level.END;
        }

        return originalKey;
    }

    @ModifyVariable(
            method = "getPortalDestination(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/portal/TeleportTransition;",
            at = @At(value = "STORE"),
            ordinal = 0,
            name = "bl"
    )
    private boolean modifyEndFlag(boolean bl, ServerLevel world, Entity entity, BlockPos pos) {
        ResourceKey<Level> currentWorldKey = world.dimension();

        if (Objects.equals(currentWorldKey.identifier().getNamespace(), NAMESPACE)) {
            String path = currentWorldKey.identifier().getPath();
            boolean bl2 = path.endsWith("_end");

            MultiWorld.LOGGER.info("Modified END flag for {} : {}", path, bl2);
            return bl2;
        }

        return bl;
    }

    @Redirect(
            method = "getPortalDestination(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/portal/TeleportTransition;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"
            )
    )
    private ServerLevel redirectGetWorld(MinecraftServer server, ResourceKey<Level> registryKey, ServerLevel world, Entity entity, BlockPos pos) {
        ServerLevel targetWorld = server.getLevel(registryKey);
        ServerLevel initialWorld = entity.level().getServer().getLevel(entity.level().dimension());

        if (initialWorld != null && Objects.equals(initialWorld.dimension().identifier().getNamespace(), NAMESPACE)) {
            String destinationName = getDestinationName(initialWorld);

            ResourceKey<Level> destinationKey = ResourceKey.create(
                    DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, destinationName)
            );
            ServerLevel destinationWorld = initialWorld.getServer().getLevel(destinationKey);

            if (destinationWorld != null) {
                MultiWorld.LOGGER.info("Teleportation redirect for End from {} to {}",
                        initialWorld.dimension().identifier(), destinationKey.identifier());
                return destinationWorld;
            }
        }

        return targetWorld;
    }

    @Unique
    private static @NotNull String getDestinationName(ServerLevel initialWorld) {
        String originName = initialWorld.dimension().identifier().getPath();
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
