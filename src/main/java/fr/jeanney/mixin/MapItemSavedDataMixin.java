package fr.jeanney.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import static fr.jeanney.CustomServerWorld.getMockRegistryKey;

@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {

    @Final
    @Shadow
    public ResourceKey<Level> dimension;

    @Redirect(
            method = "calculateRotation",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/level/saveddata/maps/MapItemSavedData;dimension:Lnet/minecraft/resources/ResourceKey;",
                    opcode = Opcodes.GETFIELD)
    )
    private ResourceKey<Level> redirectDimension(MapItemSavedData instance) {
        return getMockRegistryKey(this.dimension);
    }
}
