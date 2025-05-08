package com.gmail.anthony17j.multiworld;

import net.minecraft.registry.SimpleRegistry;
import net.minecraft.util.Identifier;

public interface CustomSimpleRegistry<T> {

    static <T> boolean remove(SimpleRegistry<T> registry, Identifier key) {
        return ((CustomSimpleRegistry<T>) registry).multiWorld$remove(key);
    }

    boolean multiWorld$remove(T value);

    boolean multiWorld$remove(Identifier key);

    boolean multiWorld$isFrozen();

    void multiWorld$setFrozen(boolean frozen);
}
