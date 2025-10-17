package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.isCreativeWorld;

@Mixin(PlayerAdvancementTracker.class)
public abstract class AdvancementMixin {
    @Shadow private ServerPlayerEntity owner;

    @Inject(at = @At("HEAD"), method = "grantCriterion", cancellable = true)
    public void grantCriterion(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (isCreativeWorld(this.owner.getEntityWorld().getRegistryKey())) {
            cir.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "revokeCriterion", cancellable = true)
    public void revokeCriterion(AdvancementEntry advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (isCreativeWorld(this.owner.getEntityWorld().getRegistryKey())) {
            cir.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "sendUpdate", cancellable = true)
    public void sendUpdate(ServerPlayerEntity player, boolean showToast, CallbackInfo ci) {
        if (isCreativeWorld(player.getEntityWorld().getRegistryKey())) {
            ci.cancel();
        }
    }
}
