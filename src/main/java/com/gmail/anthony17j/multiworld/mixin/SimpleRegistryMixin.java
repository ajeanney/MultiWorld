package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.CustomSimpleRegistry;
import net.minecraft.registry.MutableRegistry;
import net.minecraft.registry.SimpleRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SimpleRegistry.class)
public abstract class SimpleRegistryMixin<T> implements CustomSimpleRegistry<T>, MutableRegistry<T> {
    @Shadow private boolean frozen;

    public boolean creativeWorld$isFrozen() {
        return this.frozen;
    }

    public void creativeWorld$setFrozen(boolean frozen) {
        this.frozen = frozen;
    }
}
