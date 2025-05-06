package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;

@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {

    @ModifyVariable(
            method = "teleportTo",
            at = @At(value = "STORE"),
            ordinal = 0
    )
    private RegistryKey<World> modifyRegistryKey(RegistryKey<World> registryKey) {
        return getMockRegistryKey(registryKey);
    }

    @ModifyVariable(
            method = "teleportTo",
            at = @At(value = "STORE"),
            ordinal = 1
    )
    private RegistryKey<World> modifyRegistryKey2(RegistryKey<World> registryKey2) {
        return getMockRegistryKey(registryKey2);
    }
}