package com.gmail.anthony17j.creativeworld.mixin;

import com.gmail.anthony17j.creativeworld.Main;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EndPortalBlock.class)
public abstract class EndPortalMixin {
    @Inject(at = @At("HEAD"), method = "onEntityCollision", cancellable = true)
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (entity.getEntityWorld().getRegistryKey() == Main.CREATIVE_KEY) {
            ci.cancel();
        }
    }
}
