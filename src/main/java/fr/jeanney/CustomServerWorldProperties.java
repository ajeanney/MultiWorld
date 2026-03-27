package fr.jeanney;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import org.jspecify.annotations.NonNull;

public final class CustomServerWorldProperties extends DerivedLevelData {
    public final long seed;

    private LevelData.RespawnData respawnData;
    private long gameTime;
    private boolean initialized;
    private GameType gameType;
    private final boolean allowCommands;
    private final Difficulty difficulty;
    private final boolean difficultyLocked;

    public CustomServerWorldProperties(WorldData worldData, ServerLevelData levelProperties, long seed) {
        super(worldData, levelProperties);

        this.seed = seed;
        this.respawnData = null;
        this.gameTime = levelProperties.getGameTime();
        this.initialized = false;
        this.gameType = levelProperties.getGameType();
        this.allowCommands = levelProperties.isAllowCommands();
        this.difficulty = levelProperties.getDifficulty();
        this.difficultyLocked = levelProperties.isDifficultyLocked();
    }

    @Override
    public LevelData.@NonNull RespawnData getRespawnData() {
        return this.respawnData;
    }

    @Override
    public void setSpawn(LevelData.@NonNull RespawnData respawnData) {
        this.respawnData = respawnData;
    }

    @Override
    public long getGameTime() {
        return this.gameTime;
    }

    @Override
    public void setGameTime(long time) {
        this.gameTime = time;
    }

    @Override
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public @NonNull GameType getGameType() {
        return this.gameType;
    }

    @Override
    public void setGameType(@NonNull GameType gameType) {
        this.gameType = gameType;
    }

    @Override
    public boolean isAllowCommands() {
        return this.allowCommands;
    }

    @Override
    public @NonNull Difficulty getDifficulty() {
        return this.difficulty;
    }

    @Override
    public boolean isDifficultyLocked() {
        return this.difficultyLocked;
    }
}
