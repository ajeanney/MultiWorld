package fr.jeanney.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static fr.jeanney.CustomServerWorld.getMockRegistryKey;
import static fr.jeanney.CustomServerWorld.isWorldWithNether;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(at = @At("HEAD"), method = "handlePortal", cancellable = true)
    public void handlePortal(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (!isWorldWithNether(self.level().dimension())) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "canTeleport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 0
            )
    )
    private ResourceKey<Level> redirectFromGetRegistryKey(Level world) {
        return getMockRegistryKey(world.dimension());
    }

    @Redirect(
            method = "canTeleport",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;",
                    ordinal = 1
            )
    )
    private ResourceKey<Level> redirectToGetRegistryKey(Level world) {
        return getMockRegistryKey(world.dimension());
    }
}
