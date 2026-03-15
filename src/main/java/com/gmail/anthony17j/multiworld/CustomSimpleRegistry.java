package com.gmail.anthony17j.multiworld;

import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.Identifier;

public interface CustomSimpleRegistry<T> {

    static <T> boolean remove(MappedRegistry<T> registry, Identifier key) {
        return ((CustomSimpleRegistry<T>) registry).multiWorld$remove(key);
    }

    boolean multiWorld$remove(T value);

    boolean multiWorld$remove(Identifier key);

    boolean multiWorld$isFrozen();

    void multiWorld$setFrozen(boolean frozen);
}
