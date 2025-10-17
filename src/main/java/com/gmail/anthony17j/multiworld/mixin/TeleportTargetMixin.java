package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.*;
import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(TeleportTarget.class)
public abstract class TeleportTargetMixin {
    @Redirect(
            method = "noRespawnPointSet",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getSpawnWorld()Lnet/minecraft/server/world/ServerWorld;"
            )
    )
    private static ServerWorld redirectGetSpawnWorldInNoRespawnPointSet(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld currentWorld = player.getEntityWorld();

        if (Objects.equals(currentWorld.getRegistryKey().getValue().getNamespace(), NAMESPACE)) {
            String worldName = getBaseWorldName(currentWorld.getRegistryKey().getValue().getPath());

            RegistryKey<World> targetKey = getRegistryKey(worldName);

            ServerWorld targetWorld = server.getWorld(targetKey);
            if (targetWorld != null) {
                MultiWorld.LOGGER.info("Redirect noRespawnPointSet spawn world to: {}", targetKey.getValue());
                return targetWorld;
            }
        }

        return server.getSpawnWorld();
    }

    @Redirect(
            method = "missingSpawnBlock",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getSpawnWorld()Lnet/minecraft/server/world/ServerWorld;"
            )
    )
    private static ServerWorld redirectGetSpawnWorldInMissingSpawnBlock(MinecraftServer server, ServerPlayerEntity player) {
        ServerWorld currentWorld = player.getEntityWorld();

        if (Objects.equals(currentWorld.getRegistryKey().getValue().getNamespace(), NAMESPACE)) {
            String worldName = getBaseWorldName(currentWorld.getRegistryKey().getValue().getPath());

            RegistryKey<World> targetKey = getRegistryKey(worldName);

            ServerWorld targetWorld = server.getWorld(targetKey);
            if (targetWorld != null) {
                MultiWorld.LOGGER.info("Redirect missingSpawnBlock spawn world to: {}", targetKey.getValue());
                return targetWorld;
            }
        }

        return server.getSpawnWorld();
    }

    @Redirect(
            method = "getWorldSpawnPos",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;getWorldSpawnPos(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/BlockPos;"
            )
    )
    private static BlockPos redirectEntityGetWorldSpawnPos(Entity entity, ServerWorld world, BlockPos basePos) {
        BlockPos spawnSourcePos = basePos;

        World entityWorld = entity.getEntityWorld();
        if (Objects.equals(entityWorld.getRegistryKey().getValue().getNamespace(), NAMESPACE)) {
            String worldName = getBaseWorldName(entityWorld.getRegistryKey().getValue().getPath());
            RegistryKey<World> targetKey = getRegistryKey(worldName);
            entityWorld = world.getServer().getWorld(targetKey);
            MultiWorld.LOGGER.info("Redirect getWorldSpawnPos: use entity world level properties spawn for entity world {}", entityWorld.getRegistryKey().getValue());
            spawnSourcePos = entityWorld.getLevelProperties().getSpawnPoint().getPos();
        }

        Vec3d vec3d = spawnSourcePos.toCenterPos();
        int i = world.getWorldChunk(spawnSourcePos).sampleHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawnSourcePos.getX(), spawnSourcePos.getZ()) + 1;
        return BlockPos.ofFloored(vec3d.x, i, vec3d.z);
    }
}
