package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.LegacyStructureDataHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import static com.gmail.anthony17j.multiworld.CustomServerWorld.getMockRegistryKey;

@Mixin(LegacyStructureDataHandler.class)
public abstract class LegacyStructureDataHandlerMixin {

    @ModifyVariable(
            method = "getLegacyTagFixer",
            at = @At("HEAD"),
            argsOnly = true
    )
    private static ResourceKey<Level> modifyWorldKey(ResourceKey<Level> world) {
        return getMockRegistryKey(world);
    }
}
