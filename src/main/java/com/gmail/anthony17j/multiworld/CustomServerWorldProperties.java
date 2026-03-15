package com.gmail.anthony17j.multiworld;

import com.mojang.serialization.Lifecycle;
import net.minecraft.world.level.storage.PrimaryLevelData;

public final class CustomServerWorldProperties extends PrimaryLevelData {
    public final long seed;

    public CustomServerWorldProperties(PrimaryLevelData levelProperties, long seed) {
        super(
                levelProperties.getLevelSettings(),
                levelProperties.worldGenOptions(),
                PrimaryLevelData.SpecialWorldProperty.NONE,
                Lifecycle.stable()
        );
        this.seed = seed;
    }
}
