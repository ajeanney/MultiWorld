package com.gmail.anthony17j.multiworld;

import net.minecraft.world.level.LevelProperties;

public final class CustomServerWorldProperties extends LevelProperties {
    public final long seed;

    public CustomServerWorldProperties(LevelProperties levelProperties, long seed) {
        super(
                levelProperties.getLevelInfo(),
                levelProperties.getGeneratorOptions(),
                LevelProperties.SpecialProperty.NONE,
                levelProperties.getLifecycle()
        );
        this.seed = seed;
    }
}
