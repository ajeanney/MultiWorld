package com.gmail.anthony17j.multiworld;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.UnmodifiableLevelProperties;

public final class CustomServerWorldProperties extends UnmodifiableLevelProperties {
    final long seed;
    BlockPos spawnPos;
    float spawnAngle;

    public CustomServerWorldProperties(SaveProperties saveProperties, long seed) {
        super(saveProperties, saveProperties.getMainWorldProperties());
        this.seed = seed;
        ServerWorldProperties mainWorldProperties = saveProperties.getMainWorldProperties();
        this.spawnPos = mainWorldProperties.getSpawnPos();
        this.spawnAngle = mainWorldProperties.getSpawnAngle();
    }

    @Override
    public void setSpawnPos(BlockPos pos, float angle) {
        this.spawnPos = pos.toImmutable();
        this.spawnAngle = angle;
    }

    @Override
    public float getSpawnAngle() {
        return spawnAngle;
    }

    @Override
    public BlockPos getSpawnPos() {
        return spawnPos;
    }
}
