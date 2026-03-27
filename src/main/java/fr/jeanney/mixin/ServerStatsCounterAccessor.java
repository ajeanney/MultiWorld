package fr.jeanney.mixin;

import net.minecraft.stats.ServerStatsCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

@Mixin(ServerStatsCounter.class)
public interface ServerStatsCounterAccessor {
    @Accessor("file")
    Path getFile();
}
