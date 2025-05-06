package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.MultiWorld;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancementTracker.class)
public abstract class AdvancementMixin {
    @Shadow private ServerPlayerEntity owner;

    @Inject(at = @At("HEAD"), method = "grantCriterion", cancellable = true)
    public void grantCriterion(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (this.owner.getEntityWorld().getRegistryKey() == MultiWorld.CREATIVE_KEY) {
            cir.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "revokeCriterion", cancellable = true)
    public void revokeCriterion(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (this.owner.getEntityWorld().getRegistryKey() == MultiWorld.CREATIVE_KEY) {
            cir.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "sendUpdate", cancellable = true)
    public void sendUpdate(ServerPlayerEntity player, boolean showToast, CallbackInfo ci) {
        if (player.getEntityWorld().getRegistryKey() == MultiWorld.CREATIVE_KEY) {
            ci.cancel();
        }
    }
}
