package com.gmail.anthony17j.multiworld;

import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public final class CustomServerWorldProperties extends DerivedLevelData {
    public final long seed;

    private LevelData.RespawnData respawnData;
    private long gameTime;
    private long dayTime;
    private int clearWeatherTime;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private boolean initialized;
    private GameType gameType;
    private final boolean allowCommands;
    private final Difficulty difficulty;
    private final boolean difficultyLocked;
    private final GameRules gameRules;
    private final TimerQueue<MinecraftServer> scheduledEvents;
    private int wanderingTraderSpawnDelay;
    private int wanderingTraderSpawnChance;
    private UUID wanderingTraderId;

    public CustomServerWorldProperties(WorldData worldData, ServerLevelData levelProperties, long seed) {
        super(worldData, levelProperties);

        this.seed = seed;

        this.respawnData = null;
        this.gameTime = levelProperties.getGameTime();
        this.dayTime = levelProperties.getDayTime();
        this.clearWeatherTime = levelProperties.getClearWeatherTime();
        this.raining = levelProperties.isRaining();
        this.rainTime = levelProperties.getRainTime();
        this.thundering = levelProperties.isThundering();
        this.thunderTime = levelProperties.getThunderTime();
        this.initialized = false;
        this.gameType = levelProperties.getGameType();
        this.allowCommands = levelProperties.isAllowCommands();
        this.difficulty = levelProperties.getDifficulty();
        this.difficultyLocked = levelProperties.isDifficultyLocked();
        this.gameRules = worldData.getGameRules().copy(worldData.enabledFeatures());
        this.scheduledEvents = new TimerQueue<>(
            TimerCallbacks.SERVER_CALLBACKS,
            levelProperties.getScheduledEvents().store().stream().map(tag -> new Dynamic<>(NbtOps.INSTANCE, tag))
        );
        this.wanderingTraderSpawnDelay = levelProperties.getWanderingTraderSpawnDelay();
        this.wanderingTraderSpawnChance = levelProperties.getWanderingTraderSpawnChance();
        this.wanderingTraderId = levelProperties.getWanderingTraderId();
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
    public long getDayTime() {
        return this.dayTime;
    }

    @Override
    public void setDayTime(long time) {
        this.dayTime = time;
    }

    @Override
    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int time) {
        this.clearWeatherTime = time;
    }

    @Override
    public boolean isRaining() {
        return this.raining;
    }

    @Override
    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    @Override
    public int getRainTime() {
        return this.rainTime;
    }

    @Override
    public void setRainTime(int time) {
        this.rainTime = time;
    }

    @Override
    public boolean isThundering() {
        return this.thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    @Override
    public int getThunderTime() {
        return this.thunderTime;
    }

    @Override
    public void setThunderTime(int time) {
        this.thunderTime = time;
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

    @Override
    public @NonNull GameRules getGameRules() {
        return this.gameRules;
    }

    @Override
    public @NonNull TimerQueue<MinecraftServer> getScheduledEvents() {
        return this.scheduledEvents;
    }

    @Override
    public int getWanderingTraderSpawnDelay() {
        return this.wanderingTraderSpawnDelay;
    }

    @Override
    public void setWanderingTraderSpawnDelay(int delay) {
        this.wanderingTraderSpawnDelay = delay;
    }

    @Override
    public int getWanderingTraderSpawnChance() {
        return this.wanderingTraderSpawnChance;
    }

    @Override
    public void setWanderingTraderSpawnChance(int chance) {
        this.wanderingTraderSpawnChance = chance;
    }

    @Override
    public @NonNull UUID getWanderingTraderId() {
        return this.wanderingTraderId;
    }

    @Override
    public void setWanderingTraderId(@NonNull UUID uuid) {
        this.wanderingTraderId = uuid;
    }
}
