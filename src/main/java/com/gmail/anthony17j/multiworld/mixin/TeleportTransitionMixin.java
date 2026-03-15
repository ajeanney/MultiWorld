package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.*;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(TeleportTransition.class)
public abstract class TeleportTransitionMixin {

    @Redirect(
            method = "createDefault",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;findRespawnDimension()Lnet/minecraft/server/level/ServerLevel;"
            )
    )
    private static ServerLevel redirectGetSpawnWorldInCreateDefault(MinecraftServer server, ServerPlayer player) {
        ServerLevel currentWorld = player.level();

        if (Objects.equals(currentWorld.dimension().identifier().getNamespace(), NAMESPACE)) {
            String worldName = getBaseWorldName(currentWorld.dimension().identifier().getPath());

            ResourceKey<Level> targetKey = getRegistryKey(worldName);

            ServerLevel targetWorld = server.getLevel(targetKey);
            if (targetWorld != null) {
                MultiWorld.LOGGER.info("Redirect createDefault spawn world to: {}", targetKey.identifier());
                return targetWorld;
            }
        }

        return server.findRespawnDimension();
    }

    @Redirect(
            method = "missingRespawnBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;findRespawnDimension()Lnet/minecraft/server/level/ServerLevel;"
            )
    )
    private static ServerLevel redirectGetSpawnWorldInMissingRespawnBlock(MinecraftServer server, ServerPlayer player) {
        ServerLevel currentWorld = player.level();

        if (Objects.equals(currentWorld.dimension().identifier().getNamespace(), NAMESPACE)) {
            String worldName = getBaseWorldName(currentWorld.dimension().identifier().getPath());

            ResourceKey<Level> targetKey = getRegistryKey(worldName);

            ServerLevel targetWorld = server.getLevel(targetKey);
            if (targetWorld != null) {
                MultiWorld.LOGGER.info("Redirect missingRespawnBlock spawn world to: {}", targetKey.identifier());
                return targetWorld;
            }
        }

        return server.findRespawnDimension();
    }

    @Redirect(
            method = "createDefault",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/portal/TeleportTransition;findAdjustedSharedSpawnPos(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private static Vec3 redirectFindAdjustedSharedSpawnPos(ServerLevel world, Entity entity) {
        Level entityWorld = entity.level();
        if (Objects.equals(entityWorld.dimension().identifier().getNamespace(), NAMESPACE)) {
            String worldName = getBaseWorldName(entityWorld.dimension().identifier().getPath());
            ResourceKey<Level> targetKey = getRegistryKey(worldName);
            ServerLevel targetWorld = world.getServer().getLevel(targetKey);
            if (targetWorld != null) {
                MultiWorld.LOGGER.info("Redirect findAdjustedSharedSpawnPos: use entity world spawn for {}", entityWorld.dimension().identifier());
                return Vec3.atBottomCenterOf(targetWorld.getRespawnData().pos());
            }
        }
        return Vec3.atBottomCenterOf(world.getRespawnData().pos());
    }
}
