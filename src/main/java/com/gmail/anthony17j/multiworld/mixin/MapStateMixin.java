package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.item.map.MapState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;

@Mixin(MapState.class)
public abstract class MapStateMixin {

    @Final
    @Shadow
    public RegistryKey<World> dimension;

    @Redirect(
            method = "getPlayerMarkerRotation",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/item/map/MapState;dimension:Lnet/minecraft/registry/RegistryKey;"
            )
    )
    private RegistryKey<World> redirectDimension(MapState instance) {
        return getMockRegistryKey(this.dimension);
    }
}