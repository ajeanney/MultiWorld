package com.gmail.anthony17j.multiworld.mixin;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.MultiWorld.namespace;

@Mixin(AbstractFireBlock.class)
public abstract class AbstractFireBlockMixin {
    /**
     * @author Uxzylon
     * @reason This method is used to check if the world is the overworld or nether.
     */
    @Overwrite
    private static boolean isOverworldOrNether(World world) {
        if (world.getRegistryKey() == World.OVERWORLD || world.getRegistryKey() == World.NETHER) {
            return true;
        }

        Identifier worldId = world.getRegistryKey().getValue();
        if (Objects.equals(worldId.getNamespace(), namespace)) {
            String path = worldId.getPath();
            return !path.contains("_end");
        }

        return false;
    }
}
