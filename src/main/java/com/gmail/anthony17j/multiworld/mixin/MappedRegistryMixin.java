package com.gmail.anthony17j.multiworld.mixin;

import com.gmail.anthony17j.multiworld.CustomSimpleRegistry;
import com.gmail.anthony17j.multiworld.MultiWorld;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Mixin(MappedRegistry.class)
public abstract class MappedRegistryMixin<T> implements CustomSimpleRegistry<T>, WritableRegistry<T> {
    @Shadow private boolean frozen;
    @Shadow @Final private Map<T, Holder.Reference<T>> byValue;
    @Shadow @Final private Reference2IntMap<T> toId;
    @Shadow @Final private Map<ResourceKey<T>, Holder.Reference<T>> byKey;
    @Shadow @Final private Map<Identifier, Holder.Reference<T>> byLocation;
    @Shadow @Final private ObjectList<Holder.Reference<T>> byId;
    @Shadow @Final private Map<ResourceKey<T>, RegistrationInfo> registrationInfos;
    @Shadow @Final private ResourceKey<? extends net.minecraft.core.Registry<T>> key;

    @Override
    public boolean multiWorld$remove(T entry) {
        var registryEntry = this.byValue.get(entry);
        int rawId = this.toId.removeInt(entry);
        if (rawId == -1) {
            return false;
        }

        try {
            this.byKey.remove(registryEntry.key());
            this.byLocation.remove(registryEntry.key().identifier());
            this.byValue.remove(entry);
            this.byId.set(rawId, null);
            this.registrationInfos.remove(registryEntry.key());

            return true;
        } catch (Throwable e) {
            MultiWorld.LOGGER.error("Failed to remove entry from registry: {}", this.key, e);
            return false;
        }
    }

    @Override
    public boolean multiWorld$remove(Identifier key) {
        var entry = this.byLocation.get(key);
        return entry != null && entry.isBound() && this.multiWorld$remove(entry.value());
    }

    @Override
    public boolean multiWorld$isFrozen() {
        return this.frozen;
    }

    @Override
    public void multiWorld$setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    @ModifyReturnValue(method = "listElements", at = @At("RETURN"))
    public Stream<Holder.Reference<T>> fixEntryStream(Stream<Holder.Reference<T>> original) {
        return original.filter(Objects::nonNull);
    }
}
