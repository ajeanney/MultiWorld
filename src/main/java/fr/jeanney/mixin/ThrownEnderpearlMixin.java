package fr.jeanney.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static fr.jeanney.CustomServerWorld.getMockRegistryKey;

@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {

        @Redirect(method = "canTeleport", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;", ordinal = 0))
        private ResourceKey<Level> redirectFromGetRegistryKey(Level world) {
                return getMockRegistryKey(world.dimension());
        }

        @Redirect(method = "canTeleport", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;", ordinal = 1))
        private ResourceKey<Level> redirectToGetRegistryKey(Level world) {
                return getMockRegistryKey(world.dimension());
        }
}
