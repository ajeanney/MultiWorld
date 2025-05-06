package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.advancement.criterion.ChangedDimensionCriterion;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.isCustomEndWorld;
import static com.gmail.anthony17j.multiworld.CustomServerWorld.isCustomNetherWorld;

@Mixin(ChangedDimensionCriterion.Conditions.class)
public class ChangedDimensionConditionsMixin {

    @Final
    @Shadow private Optional<RegistryKey<World>> from;
    @Final
    @Shadow private Optional<RegistryKey<World>> to;

    /**
     * @author Uxzylon
     * @reason Allows the use of custom worlds in the advancement criteria.
     */
    @Overwrite
    public boolean matches(RegistryKey<World> fromWorld, RegistryKey<World> toWorld) {
        if (this.from.isPresent() && this.from.get() != fromWorld) {
            return false;
        }

        if (this.to.isPresent()) {
            RegistryKey<World> expectedTo = this.to.get();

            if (expectedTo == World.END) {
                return toWorld == World.END || isCustomEndWorld(toWorld);
            } else if (expectedTo == World.NETHER) {
                return toWorld == World.NETHER || isCustomNetherWorld(toWorld);
            } else if (expectedTo == World.OVERWORLD) {
                return toWorld == World.OVERWORLD || isCustomNetherWorld(toWorld);
            } else {
                return expectedTo == toWorld;
            }
        }

        return true;
    }
}