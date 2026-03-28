package fr.jeanney.mixin;

import fr.jeanney.MultiWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static fr.jeanney.MultiWorld.NAMESPACE;

@Mixin(ServerLevel.class)
public abstract class ServerLevelClockMixin {
    @Inject(method = "clockManager", at = @At("HEAD"), cancellable = true)
    private void usePerWorldClockManager(CallbackInfoReturnable<ServerClockManager> cir) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
            cir.setReturnValue(MultiWorld.getClockManagerForLevel(level));
        }
    }
}
