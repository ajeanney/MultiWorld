package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerPlayer.class)
public interface ServerPlayerAccessor {
    @Invoker("addAdditionalSaveData")
    void invokeAddAdditionalSaveData(ValueOutput output);

    @Invoker("readAdditionalSaveData")
    void invokeReadAdditionalSaveData(ValueInput input);
}
