package com.gmail.anthony17j.multiworld;

import com.gmail.anthony17j.multiworld.mixin.IMinecraftServerMixin;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;

import java.util.Objects;

import static com.gmail.anthony17j.multiworld.MultiWorld.NAMESPACE;
import static net.minecraft.core.registries.Registries.DIMENSION;

public class CustomServerWorld extends ServerLevel {

    public CustomServerWorld(MinecraftServer server, ResourceKey<Level> registryKey, LevelStem options, CustomServerWorldProperties levelProperties, long seed) {
        super(
                server,
                Util.backgroundExecutor(),
                ((IMinecraftServerMixin) server).getSession(),
                levelProperties,
                registryKey,
                options,
                false,
                seed,
                ImmutableList.of(
                        new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(), new WanderingTraderSpawner(levelProperties)
                ),
                true,
                null
        );
    }

    public interface Constructor {
        CustomServerWorld create(MinecraftServer server, ResourceKey<Level> registryKey, LevelStem options, CustomServerWorldProperties levelProperties, long seed);
    }

    @Override
    public long getSeed() {
        return ((CustomServerWorldProperties) this.levelData).seed;
    }

    public ResourceKey<Level> getMockRegistryKey() {
        ResourceKey<Level> registryKey = super.dimension();
        String path = registryKey.identifier().getPath();
        if (path.endsWith("_nether")) {
            return Level.NETHER;
        } else if (path.endsWith("_end")) {
            return Level.END;
        }
        return Level.OVERWORLD;
    }

    public static ResourceKey<Level> getRegistryKey(String world) {
        return ResourceKey.create(
                DIMENSION,
                Identifier.fromNamespaceAndPath(NAMESPACE, world)
        );
    }

    public static ResourceKey<Level> getMockRegistryKey(Level world) {
        if (world instanceof CustomServerWorld) {
            return ((CustomServerWorld) world).getMockRegistryKey();
        }
        return world.dimension();
    }

    public static ResourceKey<Level> getMockRegistryKey(ResourceKey<Level> world) {
        Identifier worldId = world.identifier();
        if (NAMESPACE.equals(worldId.getNamespace())) {
            String path = worldId.getPath();

            if (path.endsWith("_nether")) {
                return Level.NETHER;
            } else if (path.endsWith("_end")) {
                return Level.END;
            } else {
                return Level.OVERWORLD;
            }
        }
        return world;
    }

    public static boolean isCustomEndWorld(ResourceKey<Level> key) {
        return key.identifier().getPath().endsWith("_end") && Objects.equals(key.identifier().getNamespace(), NAMESPACE);
    }

    public static boolean isCustomNetherWorld(ResourceKey<Level> key) {
        return key.identifier().getPath().endsWith("_nether") && Objects.equals(key.identifier().getNamespace(), NAMESPACE);
    }

    public static boolean isWorldWithNether(ResourceKey<Level> key) {
        if (Objects.equals(key.identifier().getNamespace(), NAMESPACE)) {
            String path = getBaseWorldName(key.identifier().getPath());
            ResourceKey<Level> worldKey = ResourceKey.create(DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, path));
            ResourceKey<Level> netherKey = ResourceKey.create(DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, path + "_nether"));
            return worldKey != null && netherKey != null;
        } else {
            return true;
        }
    }

    public static boolean isWorldWithEnd(ResourceKey<Level> key) {
        if (Objects.equals(key.identifier().getNamespace(), NAMESPACE)) {
            String path = getBaseWorldName(key.identifier().getPath());
            ResourceKey<Level> worldKey = ResourceKey.create(DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, path));
            ResourceKey<Level> endKey = ResourceKey.create(DIMENSION, Identifier.fromNamespaceAndPath(NAMESPACE, path + "_end"));
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

    public static boolean isCreativeWorld(ResourceKey<Level> key) {
        return MultiWorld.isCreativeModeWorld(key);
    }
}
