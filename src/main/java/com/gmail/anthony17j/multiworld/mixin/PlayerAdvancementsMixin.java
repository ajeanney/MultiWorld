package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.isCreativeWorld;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {
    @Shadow private ServerPlayer player;

    @Inject(at = @At("HEAD"), method = "award", cancellable = true)
    public void award(AdvancementHolder advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (isCreativeWorld(this.player.level().dimension())) {
            cir.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "revoke", cancellable = true)
    public void revoke(AdvancementHolder advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (isCreativeWorld(this.player.level().dimension())) {
            cir.cancel();
        }
    }
}
