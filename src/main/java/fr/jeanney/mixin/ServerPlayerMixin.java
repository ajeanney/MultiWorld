package fr.jeanney.mixin;

import fr.jeanney.MultiWorld;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

import static fr.jeanney.CustomServerWorld.getBaseWorldName;
import static fr.jeanney.CustomServerWorld.getRegistryKey;
import static fr.jeanney.MultiWorld.NAMESPACE;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Redirect(method = "findRespawnPositionAndUseSpawnBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"))
    private ServerLevel redirectGetWorld(MinecraftServer server, ResourceKey<Level> registryKey) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        ServerLevel currentWorld = player.level();

        ServerLevel originalWorld = server.getLevel(registryKey);
        if (originalWorld != null && originalWorld.dimension() == Level.OVERWORLD) {
            if (Objects.equals(currentWorld.dimension().identifier().getNamespace(), NAMESPACE)) {
                String worldName = getBaseWorldName(currentWorld.dimension().identifier().getPath());
                ResourceKey<Level> targetKey = getRegistryKey(worldName);
                ServerLevel targetWorld = server.getLevel(targetKey);
                if (targetWorld != null) {
                    MultiWorld.LOGGER.info("Redirect to multiworld : {}", targetKey.identifier());
                    return targetWorld;
                }
            }
            return server.overworld();
        }

        return originalWorld;
    }

}
