package fr.jeanney.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

@Mixin(PlayerSpawnFinder.class)
public interface PlayerSpawnFinderAccessor {
    @Invoker("getOverworldRespawnPos")
    static BlockPos invokeGetOverworldRespawnPos(ServerLevel level, int x, int z) {
        throw new AssertionError();
    }

    @Invoker("findSpawn")
    static CompletableFuture<BlockPos> invokeFindSpawn(ServerLevel level, BlockPos spawnSuggestion) {
        throw new AssertionError();
    }
}