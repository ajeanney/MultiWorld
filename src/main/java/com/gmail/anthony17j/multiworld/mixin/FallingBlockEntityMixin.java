package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;

@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {

    @ModifyVariable(
            method = "teleport",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private ResourceKey<Level> modifyRegistryKey(ResourceKey<Level> registryKey) {
        return getMockRegistryKey(registryKey);
    }

    @ModifyVariable(
            method = "teleport",
            at = @At(value = "STORE"),
            ordinal = 1
    )
    private ResourceKey<Level> modifyRegistryKey2(ResourceKey<Level> registryKey2) {
        return getMockRegistryKey(registryKey2);
    }
}
