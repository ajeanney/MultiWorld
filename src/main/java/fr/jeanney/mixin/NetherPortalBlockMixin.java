package fr.jeanney.mixin;

import fr.jeanney.MultiWorld;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

import static fr.jeanney.MultiWorld.NAMESPACE;
import static net.minecraft.core.registries.Registries.DIMENSION;

@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {

    @Redirect(
            method = "getPortalDestination(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/portal/TeleportTransition;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"
            )
    )
    private ServerLevel redirectGetWorld(MinecraftServer server, ResourceKey<Level> registryKey, ServerLevel world, Entity entity) {
        if (Objects.equals(world.dimension().identifier().getNamespace(), NAMESPACE)) {
            String originName = world.dimension().identifier().getPath();
            String destinationName = originName.endsWith("_nether")
                    ? originName.substring(0, originName.length() - 7)
                    : originName + "_nether";

            ResourceKey<Level> destinationKey = ResourceKey.create(
                    DIMENSION,
                    Identifier.fromNamespaceAndPath(NAMESPACE, destinationName)
            );
            ServerLevel destinationWorld = server.getLevel(destinationKey);

            if (destinationWorld != null) {
                MultiWorld.LOGGER.info("Portal redirect to: {}", destinationKey.identifier());
                return destinationWorld;
            }
        }
        return server.getLevel(registryKey);
    }

    @Redirect(
            method = "getPortalDestination(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/portal/TeleportTransition;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"
            )
    )
    private ResourceKey<Level> redirectGetRegistryKey(ServerLevel serverWorld) {
        ResourceKey<Level> originalKey = serverWorld.dimension();
        String path = originalKey.identifier().getPath();
        String namespace = originalKey.identifier().getNamespace();

        if (Objects.equals(namespace, MultiWorld.NAMESPACE) && path.endsWith("_nether")) {
            return Level.NETHER;
        }

        return originalKey;
    }
}
