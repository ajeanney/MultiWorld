package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

@Mixin(BaseFireBlock.class)
public abstract class BaseFireBlockMixin {
    /**
     * @author Uxzylon
     * @reason This method is used to check if the world is the overworld or nether.
     */
    @Overwrite
    private static boolean inPortalDimension(Level world) {
        if (world.dimension() == Level.OVERWORLD || world.dimension() == Level.NETHER) {
            return true;
        }

        ResourceKey<Level> worldKey = world.dimension();
        if (Objects.equals(worldKey.identifier().getNamespace(), NAMESPACE)) {
            String path = worldKey.identifier().getPath();
            return !path.contains("_end");
        }

        return false;
    }
}
