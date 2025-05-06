package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.FeatureUpdater;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;

@Mixin(FeatureUpdater.class)
public abstract class FeatureUpdaterMixin {

    @ModifyVariable(
            method = "create",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static RegistryKey<World> modifyWorldKey(RegistryKey<World> world) {
        return getMockRegistryKey(world);
    }
}