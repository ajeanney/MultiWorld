package com.gmail.anthony17j.multiworld;

import com.gmail.anthony17j.multiworld.mixin.MinecraftServerMixin;
import com.google.common.collect.ImmutableList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.village.ZombieSiegeManager;
import net.minecraft.world.WanderingTraderManager;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.spawner.CatSpawner;
import net.minecraft.world.spawner.PatrolSpawner;
import net.minecraft.world.spawner.PhantomSpawner;
import java.util.Objects;

import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;

public class CustomServerWorld extends ServerWorld {

    public CustomServerWorld(MinecraftServer server, RegistryKey<World> registryKey, DimensionOptions options, CustomServerWorldProperties levelProperties, long seed) {
        super(
                server,
                Util.getMainWorkerExecutor(),
                ((MinecraftServerMixin) server).getSession(),
                levelProperties,
                registryKey,
                options,
                VoidWorldProgressListener.INSTANCE,
                false,
                // BiomeAccess.hashSeed(seed),
                seed,
                ImmutableList.of(
                        new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new ZombieSiegeManager(), new WanderingTraderManager(levelProperties)
                ),
                true,
                null
        );
    }

    public interface Constructor {
        CustomServerWorld create(MinecraftServer server, RegistryKey<World> registryKey, DimensionOptions options, CustomServerWorldProperties levelProperties, long seed);
    }

    @Override
    public long getSeed() {
        return ((CustomServerWorldProperties)this.properties).seed;
    }

    public RegistryKey<World> getMockRegistryKey() {
        RegistryKey<World> registryKey = super.getRegistryKey();
        String path = registryKey.getValue().getPath();
        if (path.endsWith("_nether")) {
            return World.NETHER;
        } else if (path.endsWith("_end")) {
            return World.END;
        }
        return World.OVERWORLD;
    }

    public static RegistryKey<World> getRegistryKey(String world) {
        return RegistryKey.of(
                RegistryKeys.WORLD,
                Identifier.of(NAMESPACE, world)
        );
    }

    public static RegistryKey<World> getMockRegistryKey(World world) {
        if (world instanceof CustomServerWorld) {
            return ((CustomServerWorld) world).getMockRegistryKey();
        }
        return world.getRegistryKey();
    }

    public static RegistryKey<World> getMockRegistryKey(RegistryKey<World> world) {
        Identifier worldId = world.getValue();
        if (NAMESPACE.equals(worldId.getNamespace())) {
            String path = worldId.getPath();

            if (path.endsWith("_nether")) {
                return World.NETHER;
            } else if (path.endsWith("_end")) {
                return World.END;
            } else {
                return World.OVERWORLD;
            }
        }
        return world;
    }

    public static boolean isCustomEndWorld(RegistryKey<World> key) {
        return key.getValue().getPath().endsWith("_end") && Objects.equals(key.getValue().getNamespace(), NAMESPACE);
    }

    public static boolean isCustomNetherWorld(RegistryKey<World> key) {
        return key.getValue().getPath().endsWith("_nether") && Objects.equals(key.getValue().getNamespace(), NAMESPACE);
    }

    public static boolean isWorldWithNether(RegistryKey<World> key) {
        if (Objects.equals(key.getValue().getNamespace(), NAMESPACE)) {
            String path = getBaseWorldName(key.getValue().getPath());
            RegistryKey<World> worldKey = RegistryKey.of(
                    RegistryKeys.WORLD,
                    Identifier.of(NAMESPACE, path)
            );
            RegistryKey<World> netherKey = RegistryKey.of(
                    RegistryKeys.WORLD,
                    Identifier.of(NAMESPACE, path + "_nether")
            );
            return worldKey != null && netherKey != null;
        } else {
            return true;
        }
    }

    public static boolean isWorldWithEnd(RegistryKey<World> key) {
        if (Objects.equals(key.getValue().getNamespace(), NAMESPACE)) {
            String path = getBaseWorldName(key.getValue().getPath());
            RegistryKey<World> worldKey = RegistryKey.of(
                    RegistryKeys.WORLD,
                    Identifier.of(NAMESPACE, path)
            );
            RegistryKey<World> endKey = RegistryKey.of(
                    RegistryKeys.WORLD,
                    Identifier.of(NAMESPACE, path + "_end")
            );
            return worldKey != null && endKey != null;
        } else {
            return true;
        }
    }

    public static String getBaseWorldName(String path) {
        if (path.endsWith("_nether")) {
            return path.substring(0, path.length() - 7);
        } else if (path.endsWith("_end")) {
            return path.substring(0, path.length() - 4);
        }
        return path;
    }
}
