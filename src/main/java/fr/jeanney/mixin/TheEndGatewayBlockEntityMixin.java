package fr.jeanney.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static fr.jeanney.CustomServerWorld.isCustomEndWorld;

@Mixin(TheEndGatewayBlockEntity.class)
public abstract class TheEndGatewayBlockEntityMixin {

    @Redirect(method = "getPortalPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;dimension()Lnet/minecraft/resources/ResourceKey;"))
    private ResourceKey<Level> redirectGetRegistryKey(ServerLevel world) {
        ResourceKey<Level> originalKey = world.dimension();

        if (isCustomEndWorld(originalKey)) {
            return Level.END;
        }

        return originalKey;
    }
}
