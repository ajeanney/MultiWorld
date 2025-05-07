package com.gmail.anthony17j.multiworld;

import net.minecraft.world.GameMode;
import net.minecraft.world.level.LevelProperties;

public final class CustomServerWorldProperties extends LevelProperties {
    public final long seed;
    private final GameMode gameMode;

    public CustomServerWorldProperties(LevelProperties levelProperties, long seed, GameMode gameMode) {
        super(
                levelProperties.getLevelInfo(),
                levelProperties.getGeneratorOptions(),
                LevelProperties.SpecialProperty.NONE,
                levelProperties.getLifecycle()
        );
        this.seed = seed;
        this.gameMode = gameMode != null ? gameMode : levelProperties.getGameMode();
    }

    @Override
    public GameMode getGameMode() {
        return this.gameMode;
    }
}
