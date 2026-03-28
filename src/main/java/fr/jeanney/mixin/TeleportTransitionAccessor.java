package fr.jeanney.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TeleportTransition.class)
public interface TeleportTransitionAccessor {
    @Invoker("findAdjustedSharedSpawnPos")
    static Vec3 invokeFindAdjustedSharedSpawnPos(ServerLevel world, Entity entity) {
        throw new AssertionError();
    }
}
