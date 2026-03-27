package fr.jeanney;

import fr.jeanney.mixin.ServerLevelAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.scores.ScoreboardSaveData;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.StreamSupport;

import static fr.jeanney.CustomServerWorld.getBaseWorldName;

final class WorldStateService {
    private static final int GAME_RULE_AUTOSAVE_INTERVAL_TICKS = 100;

    private static int gameRuleAutosaveCooldown = GAME_RULE_AUTOSAVE_INTERVAL_TICKS;
    private static final Map<ResourceKey<Level>, String> LAST_SAVED_GAMERULE_SNAPSHOTS = new HashMap<>();
    private static final Map<ResourceKey<Level>, GameRules> CUSTOM_WORLD_GAMERULES = new HashMap<>();
    private static final Map<ResourceKey<Level>, WeatherData> CUSTOM_WORLD_WEATHER = new HashMap<>();
    private static final Map<ResourceKey<Level>, ServerClockManager> CUSTOM_WORLD_CLOCKS = new HashMap<>();
    private static final Map<ResourceKey<Level>, ServerScoreboard> CUSTOM_WORLD_SCOREBOARDS = new HashMap<>();
    private static final Map<String, WorldInitialState> PENDING_INITIAL_STATES = new HashMap<>();
    private static final Set<String> CREATIVE_MODE_BASE_WORLDS = new HashSet<>();

    private WorldStateService() {
    }

    static void resetOnServerStarted() {
        CREATIVE_MODE_BASE_WORLDS.clear();
        PENDING_INITIAL_STATES.clear();
    }

    static void resetOnServerStopping() {
        LAST_SAVED_GAMERULE_SNAPSHOTS.clear();
        CUSTOM_WORLD_GAMERULES.clear();
        CUSTOM_WORLD_WEATHER.clear();
        CUSTOM_WORLD_CLOCKS.clear();
        CUSTOM_WORLD_SCOREBOARDS.clear();
        PENDING_INITIAL_STATES.clear();
        CREATIVE_MODE_BASE_WORLDS.clear();
        gameRuleAutosaveCooldown = GAME_RULE_AUTOSAVE_INTERVAL_TICKS;
    }

    static ServerClockManager getClockManagerForLevel(ServerLevel level) {
        if (!MultiWorld.NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
            return level.getServer().clockManager();
        }

        ResourceKey<Level> key = level.dimension();
        return CUSTOM_WORLD_CLOCKS.computeIfAbsent(key, unused -> {
            ServerClockManager manager = level.getDataStorage().computeIfAbsent(ServerClockManager.TYPE);
            manager.init(level.getServer());
            return manager;
        });
    }

    static ServerScoreboard getScoreboardForLevel(ServerLevel level) {
        if (!MultiWorld.NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
            return level.getServer().getScoreboard();
        }

        ResourceKey<Level> key = level.dimension();
        return CUSTOM_WORLD_SCOREBOARDS.computeIfAbsent(key, unused -> {
            ServerScoreboard scoreboard = new ServerScoreboard(level.getServer());
            ScoreboardSaveData scoreboardSaveData = level.getDataStorage().computeIfAbsent(ScoreboardSaveData.TYPE);
            if (scoreboardSaveData.getData() != null) {
                scoreboard.load(scoreboardSaveData.getData());
            }
            return scoreboard;
        });
    }

    static GameRules getCustomWorldGameRules(ServerLevel level) {
        if (!MultiWorld.NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
            return null;
        }

        ResourceKey<Level> key = level.dimension();
        return CUSTOM_WORLD_GAMERULES.computeIfAbsent(key,
                unused -> level.getServer().getGameRules().copy(level.getServer().getWorldData().enabledFeatures()));
    }

    static WeatherData getCustomWorldWeatherData(ServerLevel level) {
        if (!MultiWorld.NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
            return null;
        }

        ResourceKey<Level> key = level.dimension();
        return CUSTOM_WORLD_WEATHER.computeIfAbsent(key, unused -> {
            WeatherData base = level.getServer().getWeatherData();
            return new WeatherData(
                    base.getClearWeatherTime(),
                    base.getRainTime(),
                    base.getThunderTime(),
                    base.isRaining(),
                    base.isThundering());
        });
    }

    static void setCustomWorldWeather(ResourceKey<Level> key, WeatherData weatherData) {
        CUSTOM_WORLD_WEATHER.put(key, weatherData);
    }

    static void clearCustomWorldState(ResourceKey<Level> key) {
        CUSTOM_WORLD_GAMERULES.remove(key);
        CUSTOM_WORLD_WEATHER.remove(key);
        CUSTOM_WORLD_CLOCKS.remove(key);
        CUSTOM_WORLD_SCOREBOARDS.remove(key);
        LAST_SAVED_GAMERULE_SNAPSHOTS.remove(key);
    }

    static void initializeCustomWorldState(ServerLevel level) {
        if (!MultiWorld.NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
            return;
        }

        ResourceKey<Level> key = level.dimension();
        CUSTOM_WORLD_GAMERULES.put(
                key,
                level.getServer().getGameRules().copy(level.getServer().getWorldData().enabledFeatures()));

        WeatherData base = level.getServer().getWeatherData();
        CUSTOM_WORLD_WEATHER.put(
                key,
                new WeatherData(
                        base.getClearWeatherTime(),
                        base.getRainTime(),
                        base.getThunderTime(),
                        base.isRaining(),
                        base.isThundering()));
    }

    static void tickCustomWorldClocks(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!MultiWorld.NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
                continue;
            }
            getClockManagerForLevel(level).tick();
        }
    }

    static void setCustomWorldClockTimes(ServerLevel level, long gameTime) {
        ServerClockManager clockManager = getClockManagerForLevel(level);
        var clockRegistry = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
        clockRegistry.listElements().forEach(clock -> clockManager.setTotalTicks(clock, gameTime));
    }

    static void setOverworldClockTime(ServerLevel level, long clockTicks) {
        ServerClockManager clockManager = getClockManagerForLevel(level);
        var clockLookup = level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK);
        Holder<WorldClock> overworldClock = clockLookup.getOrThrow(
                ResourceKey.create(Registries.WORLD_CLOCK, Identifier.fromNamespaceAndPath("minecraft", "overworld")));
        clockManager.setTotalTicks(overworldClock, clockTicks);
    }

    private static void setCustomWorldTemporalState(ServerLevel level, WorldInitialState state) {
        if (!MultiWorld.NAMESPACE.equals(level.dimension().identifier().getNamespace())) {
            return;
        }

        ((ServerLevelAccessor) level).getWorldProperties().setGameTime(state.gameTime());
        if (state.overworldClockTicks() >= 0L) {
            setOverworldClockTime(level, state.overworldClockTicks());
        } else if (state.preserveSavedWorldClocks()) {
            // Ensure saved world_clocks.dat is loaded, but keep its per-clock values.
            getClockManagerForLevel(level);
        } else {
            setCustomWorldClockTimes(level, state.gameTime());
        }

        setCustomWorldWeather(
                level.dimension(),
                new WeatherData(
                        state.clearWeatherTime(),
                        state.rainTime(),
                        state.thunderTime(),
                        state.raining(),
                        state.thundering()));
    }

    static void applyInitialStateToWorldFamily(MinecraftServer server, String worldString, WorldInitialState state) {
        String[] dimensionNames = { worldString, worldString + "_nether", worldString + "_end" };
        for (String name : dimensionNames) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(MultiWorld.NAMESPACE, name));
            ServerLevel level = server.getLevel(key);
            if (level != null) {
                setCustomWorldTemporalState(level, state);
            }
        }
    }

    static WorldInitialState createFreshInitialState(MultiWorld.WorldMode worldMode) {
        WeatherData weatherData = new WeatherData();
        long initialGameTime = worldMode == MultiWorld.WorldMode.CREATIVE ? 1000L : 0L;
        return new WorldInitialState(
                initialGameTime,
                weatherData.getClearWeatherTime(),
                weatherData.getRainTime(),
                weatherData.getThunderTime(),
                weatherData.isRaining(),
                weatherData.isThundering(),
                false,
                -1L);
    }

    static WorldInitialState createImportedInitialState(CompoundTag dataTag, Optional<WeatherData> savedWeather,
            boolean preserveSavedWorldClocks, OptionalLong overworldClockTicks) {
        long gameTime = dataTag.getLong("Time")
                .or(() -> dataTag.getLong("DayTime"))
                .orElse(0L);

        WeatherData weatherData = savedWeather.orElse(
                new WeatherData(
                        dataTag.getInt("clearWeatherTime").orElse(0),
                        dataTag.getInt("rainTime").orElse(0),
                        dataTag.getInt("thunderTime").orElse(0),
                        dataTag.getBooleanOr("raining", false),
                        dataTag.getBooleanOr("thundering", false)));

        return new WorldInitialState(
                gameTime,
                weatherData.getClearWeatherTime(),
                weatherData.getRainTime(),
                weatherData.getThunderTime(),
                weatherData.isRaining(),
                weatherData.isThundering(),
                preserveSavedWorldClocks,
                overworldClockTicks.orElse(-1L));
    }

    static void rememberPendingInitialState(String worldName, WorldInitialState initialState) {
        PENDING_INITIAL_STATES.put(getBaseWorldName(worldName), initialState);
    }

    static Optional<WorldInitialState> consumePendingInitialState(String worldName) {
        String baseWorldName = getBaseWorldName(worldName);
        return Optional.ofNullable(PENDING_INITIAL_STATES.remove(baseWorldName));
    }

    static void registerWorldMode(String worldName, MultiWorld.WorldMode mode) {
        String baseWorldName = getBaseWorldName(worldName);
        if (mode == MultiWorld.WorldMode.CREATIVE) {
            CREATIVE_MODE_BASE_WORLDS.add(baseWorldName);
        } else {
            CREATIVE_MODE_BASE_WORLDS.remove(baseWorldName);
        }
    }

    static boolean isCreativeModeWorld(ResourceKey<Level> key) {
        if (!MultiWorld.NAMESPACE.equals(key.identifier().getNamespace())) {
            return false;
        }
        return CREATIVE_MODE_BASE_WORLDS.contains(getBaseWorldName(key.identifier().getPath()));
    }

    static Optional<String> getCreativeWorldName(MinecraftServer server) {
        return StreamSupport.stream(server.getAllLevels().spliterator(), false)
                .filter(level -> MultiWorld.NAMESPACE.equals(level.dimension().identifier().getNamespace()))
                .map(level -> getBaseWorldName(level.dimension().identifier().getPath()))
                .distinct()
                .filter(CREATIVE_MODE_BASE_WORLDS::contains)
                .min(Comparator.naturalOrder());
    }

    static boolean shouldAutosaveGameRulesThisTick() {
        if (--gameRuleAutosaveCooldown > 0) {
            return false;
        }
        gameRuleAutosaveCooldown = GAME_RULE_AUTOSAVE_INTERVAL_TICKS;
        return true;
    }

    static String getLastSavedGameRuleSnapshot(ResourceKey<Level> key) {
        return LAST_SAVED_GAMERULE_SNAPSHOTS.get(key);
    }

    static void setLastSavedGameRuleSnapshot(ResourceKey<Level> key, String snapshot) {
        LAST_SAVED_GAMERULE_SNAPSHOTS.put(key, snapshot);
    }

    static void autosaveCustomWorldScoreboards(MinecraftServer server) {
        for (ServerLevel world : server.getAllLevels()) {
            if (!MultiWorld.NAMESPACE.equals(world.dimension().identifier().getNamespace())) {
                continue;
            }

            ServerScoreboard scoreboard = CUSTOM_WORLD_SCOREBOARDS.get(world.dimension());
            if (scoreboard == null) {
                continue;
            }

            ScoreboardSaveData scoreboardSaveData = world.getDataStorage().computeIfAbsent(ScoreboardSaveData.TYPE);
            scoreboard.storeToSaveDataIfDirty(scoreboardSaveData);
        }
    }

    record WorldInitialState(long gameTime, int clearWeatherTime, int rainTime, int thunderTime,
            boolean raining, boolean thundering, boolean preserveSavedWorldClocks,
            long overworldClockTicks) {
    }
}
