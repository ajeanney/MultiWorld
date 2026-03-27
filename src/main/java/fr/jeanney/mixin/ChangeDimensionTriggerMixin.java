package fr.jeanney.mixin;

import net.minecraft.advancements.criterion.ChangeDimensionTrigger;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

import static fr.jeanney.CustomServerWorld.isCustomEndWorld;
import static fr.jeanney.CustomServerWorld.isCustomNetherWorld;

@Mixin(ChangeDimensionTrigger.TriggerInstance.class)
public class ChangeDimensionTriggerMixin {

    @Final
    @Shadow
    private Optional<ResourceKey<Level>> from;
    @Final
    @Shadow
    private Optional<ResourceKey<Level>> to;

    /**
     * @author Uxzylon
     * @reason Allows the use of custom worlds in the advancement criteria.
     */
    @Overwrite
    public boolean matches(ResourceKey<Level> fromWorld, ResourceKey<Level> toWorld) {
        if (this.from.isPresent() && this.from.get() != fromWorld) {
            return false;
        }

        if (this.to.isPresent()) {
            ResourceKey<Level> expectedTo = this.to.get();

            if (expectedTo == Level.END) {
                return toWorld == Level.END || isCustomEndWorld(toWorld);
            } else if (expectedTo == Level.NETHER) {
                return toWorld == Level.NETHER || isCustomNetherWorld(toWorld);
            } else if (expectedTo == Level.OVERWORLD) {
                return toWorld == Level.OVERWORLD || isCustomNetherWorld(toWorld);
            } else {
                return expectedTo == toWorld;
            }
        }

        return true;
    }
}
