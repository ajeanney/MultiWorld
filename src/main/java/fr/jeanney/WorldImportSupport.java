package fr.jeanney;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.saveddata.WeatherData;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

final class WorldImportSupport {

    private WorldImportSupport() {
    }

    static Optional<BlockPos> extractImportedSpawnPos(CompoundTag dataTag) {
        Optional<CompoundTag> spawnCompoundOpt = dataTag.getCompound("spawn");
        if (spawnCompoundOpt.isPresent()) {
            CompoundTag spawnCompound = spawnCompoundOpt.get();

            Optional<int[]> spawnPosArray = spawnCompound.getIntArray("pos");
            if (spawnPosArray.isPresent() && spawnPosArray.get().length >= 3) {
                int[] pos = spawnPosArray.get();
                return Optional.of(new BlockPos(pos[0], pos[1], pos[2]));
            }

            Optional<Integer> spawnX = spawnCompound.getInt("x");
            Optional<Integer> spawnY = spawnCompound.getInt("y");
            Optional<Integer> spawnZ = spawnCompound.getInt("z");
            if (spawnX.isPresent() && spawnY.isPresent() && spawnZ.isPresent()) {
                return Optional.of(new BlockPos(spawnX.get(), spawnY.get(), spawnZ.get()));
            }
        }

        if (dataTag.contains("SpawnX") && dataTag.contains("SpawnY") && dataTag.contains("SpawnZ")) {
            return Optional.of(new BlockPos(
                    dataTag.getInt("SpawnX").orElse(0),
                    dataTag.getInt("SpawnY").orElse(63),
                    dataTag.getInt("SpawnZ").orElse(0)));
        }

        return Optional.empty();
    }

    static File resolveImportSourceWorldFolder(File sourceWorldFolder) {
        if (new File(sourceWorldFolder, "level.dat").exists()) {
            return sourceWorldFolder;
        }

        File parent = sourceWorldFolder.getParentFile();
        if (parent != null && new File(parent, "level.dat").exists()) {
            return parent;
        }

        File nestedWorld = new File(sourceWorldFolder, "world");
        if (nestedWorld.isDirectory() && new File(nestedWorld, "level.dat").exists()) {
            return nestedWorld;
        }

        File[] children = sourceWorldFolder.listFiles(File::isDirectory);
        if (children != null) {
            File singleCandidate = null;
            for (File child : children) {
                if (new File(child, "level.dat").exists()) {
                    if (singleCandidate != null) {
                        singleCandidate = null;
                        break;
                    }
                    singleCandidate = child;
                }
            }
            if (singleCandidate != null) {
                return singleCandidate;
            }
        }

        return sourceWorldFolder;
    }

    static File resolveOverworldImportFolder(File sourceWorldFolder) {
        File modernOverworld = new File(sourceWorldFolder, "dimensions/minecraft/overworld");
        if (modernOverworld.isDirectory()) {
            return modernOverworld;
        }
        return sourceWorldFolder;
    }

    static File resolveNetherImportFolder(File sourceWorldFolder) {
        File modernNether = new File(sourceWorldFolder, "dimensions/minecraft/the_nether");
        if (modernNether.isDirectory()) {
            return modernNether;
        }

        File legacyNether = new File(sourceWorldFolder, "DIM-1");
        return legacyNether.isDirectory() ? legacyNether : null;
    }

    static File resolveEndImportFolder(File sourceWorldFolder) {
        File modernEnd = new File(sourceWorldFolder, "dimensions/minecraft/the_end");
        if (modernEnd.isDirectory()) {
            return modernEnd;
        }

        File legacyEnd = new File(sourceWorldFolder, "DIM1");
        return legacyEnd.isDirectory() ? legacyEnd : null;
    }

    static GameRules extractImportedGameRules(MinecraftServer server, File sourceWorldFolder,
            CompoundTag rawDataTag) {
        Optional<GameRules> fromDat = extractGameRulesFromDatFile(server, sourceWorldFolder);
        if (fromDat.isPresent()) {
            return fromDat.get();
        }

        Optional<GameRules> fromLevelDat = rawDataTag.getCompound("GameRules")
                .flatMap(tag -> parseGameRulesTag(server, tag));
        if (fromLevelDat.isPresent()) {
            return fromLevelDat.get();
        }

        Optional<GameRules> fromLowerCaseLevelDat = rawDataTag.getCompound("gameRules")
                .flatMap(tag -> parseGameRulesTag(server, tag));
        if (fromLowerCaseLevelDat.isPresent()) {
            return fromLowerCaseLevelDat.get();
        }

        return new GameRules(server.getWorldData().enabledFeatures());
    }

    private static Optional<GameRules> extractGameRulesFromDatFile(MinecraftServer server, File sourceWorldFolder) {
        File gameRulesDat = new File(sourceWorldFolder, "data/minecraft/game_rules.dat");
        if (!gameRulesDat.exists()) {
            gameRulesDat = new File(sourceWorldFolder, "data/game_rules.dat");
            if (!gameRulesDat.exists()) {
                return Optional.empty();
            }
        }

        try {
            CompoundTag root = NbtIo.readCompressed(gameRulesDat.toPath(), NbtAccounter.unlimitedHeap());
            CompoundTag dataTag = root.getCompound("data").orElse(root);

            Optional<GameRules> direct = parseGameRulesTag(server, dataTag);
            if (direct.isPresent()) {
                return direct;
            }

            return dataTag.getCompound("rules").flatMap(tag -> parseGameRulesTag(server, tag));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static Optional<GameRules> parseGameRulesTag(MinecraftServer server, CompoundTag gameRulesTag) {
        return GameRules.codec(server.getWorldData().enabledFeatures())
                .parse(NbtOps.INSTANCE, gameRulesTag)
                .result();
    }

    static long extractSeedFromLevelDat(CompoundTag levelData, MinecraftServer server, File sourceWorldFolder) {
        OptionalLong savedDataSeed = extractSeedFromSavedWorldGenData(server, sourceWorldFolder);
        if (savedDataSeed.isPresent()) {
            return savedDataSeed.getAsLong();
        }

        CompoundTag dataTag = levelData.getCompound("Data").orElse(levelData);

        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
            Optional<WorldGenSettings> worldGenSettings = dataTag
                    .read("WorldGenSettings", WorldGenSettings.CODEC, ops);
            if (worldGenSettings.isPresent()) {
                return worldGenSettings.get().options().seed();
            }
        } catch (Exception ignored) {
            // Continue with manual fallbacks for legacy or malformed data.
        }

        Optional<Long> directSeed = dataTag.getCompound("WorldGenSettings")
                .flatMap(tag -> tag.getLong("seed"))
                .or(() -> dataTag.getLong("RandomSeed"))
                .or(() -> dataTag.getLong("Seed"));
        if (directSeed.isPresent()) {
            return directSeed.get();
        }

        Optional<Long> recursiveSeed = findSeedRecursively(dataTag, 0);
        if (recursiveSeed.isPresent()) {
            return recursiveSeed.get();
        }

        MultiWorld.LOGGER.warn("Could not resolve seed from world data for '{}', using server overworld seed fallback",
                sourceWorldFolder.getAbsolutePath());
        return server.overworld().getSeed();
    }

    private static OptionalLong extractSeedFromSavedWorldGenData(MinecraftServer server, File sourceWorldFolder) {
        OptionalLong directDatSeed = extractSeedFromWorldGenSettingsDat(sourceWorldFolder);
        if (directDatSeed.isPresent()) {
            return directDatSeed;
        }

        File parent = sourceWorldFolder.getParentFile();
        if (parent == null) {
            return OptionalLong.empty();
        }

        try {
            var storageSource = net.minecraft.world.level.storage.LevelStorageSource.createDefault(parent.toPath());
            try (var storageAccess = storageSource.createAccess(sourceWorldFolder.getName())) {
                var result = net.minecraft.world.level.storage.LevelStorageSource.readExistingSavedData(
                        storageAccess,
                        server.registryAccess(),
                        WorldGenSettings.TYPE);
                Optional<WorldGenSettings> worldGenSettings = result.result();
                if (worldGenSettings.isPresent()) {
                    return OptionalLong.of(worldGenSettings.get().options().seed());
                }
            }
        } catch (Exception ignored) {
            return OptionalLong.empty();
        }

        return OptionalLong.empty();
    }

    static Optional<WeatherData> extractWeatherFromSavedData(MinecraftServer server, File sourceWorldFolder) {
        Optional<WeatherData> directDatWeather = extractWeatherFromDatFile(sourceWorldFolder);
        if (directDatWeather.isPresent()) {
            return directDatWeather;
        }

        File parent = sourceWorldFolder.getParentFile();
        if (parent == null) {
            return Optional.empty();
        }

        try {
            var storageSource = net.minecraft.world.level.storage.LevelStorageSource.createDefault(parent.toPath());
            try (var storageAccess = storageSource.createAccess(sourceWorldFolder.getName())) {
                var result = net.minecraft.world.level.storage.LevelStorageSource.readExistingSavedData(
                        storageAccess,
                        server.registryAccess(),
                        WeatherData.TYPE);
                return result.result();
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static OptionalLong extractSeedFromWorldGenSettingsDat(File sourceWorldFolder) {
        File worldGenSettingsDat = new File(sourceWorldFolder, "data/minecraft/world_gen_settings.dat");
        if (!worldGenSettingsDat.exists()) {
            return OptionalLong.empty();
        }

        try {
            CompoundTag root = NbtIo.readCompressed(worldGenSettingsDat.toPath(), NbtAccounter.unlimitedHeap());
            CompoundTag dataTag = root.getCompound("data").orElse(root);

            Optional<Long> directSeed = dataTag.getLong("seed")
                    .or(() -> dataTag.getLong("RandomSeed"))
                    .or(() -> dataTag.getLong("Seed"));
            if (directSeed.isPresent()) {
                return OptionalLong.of(directSeed.get());
            }

            Optional<Long> recursiveSeed = findSeedRecursively(dataTag, 0);
            if (recursiveSeed.isPresent()) {
                return OptionalLong.of(recursiveSeed.get());
            }
        } catch (Exception ignored) {
            return OptionalLong.empty();
        }

        return OptionalLong.empty();
    }

    private static Optional<WeatherData> extractWeatherFromDatFile(File sourceWorldFolder) {
        File weatherDat = new File(sourceWorldFolder, "data/minecraft/weather.dat");
        if (!weatherDat.exists()) {
            return Optional.empty();
        }

        try {
            CompoundTag root = NbtIo.readCompressed(weatherDat.toPath(), NbtAccounter.unlimitedHeap());
            CompoundTag dataTag = root.getCompound("data").orElse(root);

            return Optional.of(new WeatherData(
                    dataTag.getInt("clear_weather_time").or(() -> dataTag.getInt("clearWeatherTime")).orElse(0),
                    dataTag.getInt("rain_time").or(() -> dataTag.getInt("rainTime")).orElse(0),
                    dataTag.getInt("thunder_time").or(() -> dataTag.getInt("thunderTime")).orElse(0),
                    dataTag.getBooleanOr("raining", false),
                    dataTag.getBooleanOr("thundering", false)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    static boolean hasWorldClocksDat(File sourceWorldFolder) {
        return new File(sourceWorldFolder, "data/minecraft/world_clocks.dat").exists();
    }

    static OptionalLong extractOverworldClockTicksFromDat(File sourceWorldFolder) {
        File clocksDat = new File(sourceWorldFolder, "data/minecraft/world_clocks.dat");
        if (!clocksDat.exists()) {
            return OptionalLong.empty();
        }

        try {
            CompoundTag root = NbtIo.readCompressed(clocksDat.toPath(), NbtAccounter.unlimitedHeap());
            CompoundTag dataTag = root.getCompound("data").orElse(root);

            OptionalLong direct = dataTag.getCompound("minecraft:overworld")
                    .flatMap(clockTag -> clockTag.getLong("total_ticks"))
                    .map(OptionalLong::of)
                    .orElse(OptionalLong.empty());
            if (direct.isPresent()) {
                return direct;
            }

            return findClockTicksRecursively(dataTag, "minecraft:overworld", 0);
        } catch (Exception ignored) {
            return OptionalLong.empty();
        }
    }

    private static OptionalLong findClockTicksRecursively(CompoundTag tag, String keyName, int depth) {
        if (depth > 16) {
            return OptionalLong.empty();
        }

        for (Map.Entry<String, Tag> entry : tag.entrySet()) {
            String key = entry.getKey();
            Tag value = entry.getValue();
            if (!(value instanceof CompoundTag nested)) {
                continue;
            }

            if (keyName.equals(key)) {
                Optional<Long> ticks = nested.getLong("total_ticks");
                if (ticks.isPresent()) {
                    return OptionalLong.of(ticks.get());
                }
            }

            OptionalLong nestedResult = findClockTicksRecursively(nested, keyName, depth + 1);
            if (nestedResult.isPresent()) {
                return nestedResult;
            }
        }

        return OptionalLong.empty();
    }

    private static Optional<Long> findSeedRecursively(CompoundTag tag, int depth) {
        if (depth > 16) {
            return Optional.empty();
        }

        for (Map.Entry<String, Tag> entry : tag.entrySet()) {
            String key = entry.getKey();
            Tag value = entry.getValue();

            if (value instanceof LongTag(long value1)
                    && ("seed".equalsIgnoreCase(key) || "randomseed".equalsIgnoreCase(key))) {
                return Optional.of(value1);
            }

            if (value instanceof CompoundTag compoundTag) {
                Optional<Long> nestedSeed = findSeedRecursively(compoundTag, depth + 1);
                if (nestedSeed.isPresent()) {
                    return nestedSeed;
                }
            }
        }

        return Optional.empty();
    }

    static void updateDimensionInTag(CompoundTag parentTag, String tagName, String worldName) {
        Optional<CompoundTag> tagOpt = parentTag.getCompound(tagName);
        if (tagOpt.isPresent()) {
            CompoundTag tag = tagOpt.get();
            String dimension = tag.getString("dimension").orElse(Level.OVERWORLD.identifier().toString());
            tag.putString("dimension", convertDimensionName(dimension, worldName));
            parentTag.put(tagName, tag);
        }
    }

    static String convertDimensionName(String originalDimension, String worldName) {
        String newDimension;
        if (originalDimension.endsWith("_nether")) {
            newDimension = worldName + "_nether";
        } else if (originalDimension.endsWith("_end")) {
            newDimension = worldName + "_end";
        } else {
            newDimension = worldName;
        }
        return MultiWorld.NAMESPACE + ":" + newDimension;
    }

    static void copyWorldFiles(File sourceFolder, String serverWorldFolder, ResourceKey<Level> worldKey) {
        String worldDirectory = buildWorldDirectoryPath(serverWorldFolder, worldKey);

        copyFilesBeforeWorldCreation(sourceFolder, "region", worldDirectory);
        copyFilesBeforeWorldCreation(sourceFolder, "poi", worldDirectory);
        copyFilesBeforeWorldCreation(sourceFolder, "entities", worldDirectory);
        copyFilesBeforeWorldCreation(sourceFolder, "data", worldDirectory);
    }

    static String buildWorldDirectoryPath(String serverWorldFolder, ResourceKey<Level> worldKey) {
        return serverWorldFolder + "/dimensions/"
                + worldKey.identifier().getNamespace() + "/" + worldKey.identifier().getPath();
    }

    static void copyFilesBeforeWorldCreation(File sourceFolder, String type, String destinationPath) {
        File sourceTypeFolder = new File(sourceFolder, type);
        if (!sourceTypeFolder.exists() || !sourceTypeFolder.isDirectory()) {
            return;
        }

        String fileExtension = type.equals("data") ? ".dat" : ".mca";
        File destinationFolder = new File(destinationPath + "/" + type);
        if (!destinationFolder.exists() && !destinationFolder.mkdirs()) {
            MultiWorld.LOGGER.error("Could not create destination folder: {}", destinationFolder);
            return;
        }

        int copiedFiles = 0;
        try (var walk = Files.walk(sourceTypeFolder.toPath())) {
            for (Path sourcePath : walk.filter(Files::isRegularFile).toList()) {
                if (!sourcePath.getFileName().toString().endsWith(fileExtension)) {
                    continue;
                }

                Path relativePath = sourceTypeFolder.toPath().relativize(sourcePath);
                Path targetPath = destinationFolder.toPath().resolve(relativePath);
                if (targetPath.getParent() != null) {
                    Files.createDirectories(targetPath.getParent());
                }

                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                copiedFiles++;
            }
        } catch (IOException e) {
            MultiWorld.LOGGER.error("Error copying {} files from {}: {}", type, sourceTypeFolder, e.getMessage());
            return;
        }

        if (copiedFiles > 0) {
            MultiWorld.LOGGER.info("Copied {} files from {} to {}", copiedFiles, type, destinationPath + "/" + type);
        }
    }
}
