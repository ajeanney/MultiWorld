package fr.jeanney;

import fr.jeanney.mixin.ServerLevelAccessor;
import fr.jeanney.mixin.StatsCounterAccessor;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import static fr.jeanney.MultiWorld.LOGGER;
import static fr.jeanney.MultiWorld.NAMESPACE;

public class Utils {
    private static String getWorldFolder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).toString();
    }

    public static CompoundTag migratePlayerDataToCurrentVersion(MinecraftServer server, CompoundTag playerNbt, String context) {
        int sourceVersion = NbtUtils.getDataVersion(playerNbt, 0);
        int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
        if (sourceVersion >= currentVersion) {
            return playerNbt;
        }

        try {
            CompoundTag fixed = DataFixTypes.PLAYER.updateToCurrentVersion(server.getFixerUpper(), playerNbt, sourceVersion);
            NbtUtils.addCurrentDataVersion(fixed);
            LOGGER.info("Migrated player data '{}' from DataVersion {} to {}", context, sourceVersion, currentVersion);
            return fixed;
        } catch (Exception ex) {
            LOGGER.error("Error migrating player data '{}' from DataVersion {}: {}", context, sourceVersion, ex.getMessage());
            return playerNbt;
        }
    }

    public static void saveInv(ServerPlayer player, String world) {
        try {
            String worldFolder = getWorldFolder(player.level().getServer());

            if (new File(worldFolder + "/" + NAMESPACE + "/" + world).mkdirs()) {
                LOGGER.info("Directory created: {}/{}/{}", worldFolder, NAMESPACE, world);
            }
            BufferedWriter writer = Files.newBufferedWriter(Paths.get(worldFolder + "/" + NAMESPACE + "/" + world + "/" + player.getStringUUID() + ".json"));

            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
            player.saveWithoutId(output);

            // Remove ender pearls the player had launched (they will be put back on load)
            player.getEnderPearls().forEach((enderPearl) -> enderPearl.remove(Entity.RemovalReason.DISCARDED));

            // Remove player vehicle (will be put back on load)
            if (player.getRootVehicle() != player) {
                player.getRootVehicle().remove(Entity.RemovalReason.DISCARDED);
            }

            CompoundTag tag = output.buildResult();

            // add respawn position to the tag
            CompoundTag respawnTag = new CompoundTag();
            ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
            if (respawn != null) {
                respawnTag.putString("dimension", respawn.respawnData().dimension().identifier().toString());
                respawnTag.putBoolean("forced", respawn.forced());
                respawnTag.putFloat("angle", respawn.respawnData().yaw());
                respawnTag.putFloat("pitch", respawn.respawnData().pitch());
                respawnTag.putIntArray("pos", new int[]{respawn.respawnData().pos().getX(), respawn.respawnData().pos().getY(), respawn.respawnData().pos().getZ()});
                tag.put("respawn", respawnTag);
            }

            // Save player stats
            player.getStats().save();
            JsonObject statsJson = new JsonObject();
            File statsFolder = new File(worldFolder + "/stats");
            File[] statsFiles = statsFolder.listFiles();
            if (statsFiles != null) {
                for (File statsFile : statsFiles) {
                    if (statsFile.getName().equals(player.getStringUUID() + ".json")) {
                        try (FileReader statsReader = new FileReader(statsFile)) {
                            JsonElement parsed = JsonParser.parseReader(statsReader);
                            if (parsed != null && parsed.isJsonObject()) {
                                statsJson = parsed.getAsJsonObject();
                            }
                        }
                    }
                }
            }

            // Save player advancements
            player.getAdvancements().save();
            JsonObject advancementsJson = new JsonObject();
            File advancementsFolder = new File(worldFolder + "/advancements");
            File[] advancementsFiles = advancementsFolder.listFiles();
            if (advancementsFiles != null) {
                for (File advancementsFile : advancementsFiles) {
                    if (advancementsFile.getName().equals(player.getStringUUID() + ".json")) {
                        try (FileReader advancementsReader = new FileReader(advancementsFile)) {
                            JsonElement parsed = JsonParser.parseReader(advancementsReader);
                            if (parsed != null && parsed.isJsonObject()) {
                                advancementsJson = parsed.getAsJsonObject();
                            }
                        }
                    }
                }
            }

            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("player", tag.toString());
            playerJson.add("stats", statsJson);
            playerJson.add("advancements", advancementsJson);
            writer.write(playerJson.toString());
            writer.close();

        } catch (Exception ex) {
            LOGGER.error("Error saving player data: {}", ex.getMessage());
        }
    }

    public static void loadInv(ServerPlayer player, String world) {
        String worldFolder = getWorldFolder(player.level().getServer());
        java.nio.file.Path advancementsPath = Paths.get(worldFolder, "advancements", player.getStringUUID() + ".json");

        try (FileReader fileReader = new FileReader(worldFolder + "/" + NAMESPACE + "/" + world + "/" + player.getStringUUID() + ".json")) {
            JsonElement parsedRoot = JsonParser.parseReader(fileReader);
            JsonObject file = parsedRoot != null && parsedRoot.isJsonObject() ? parsedRoot.getAsJsonObject() : new JsonObject();

            String playerString = file.has("player") ? file.get("player").getAsString() : "{}";
            CompoundTag playerNbt = TagParser.parseCompoundFully(playerString);
                playerNbt = migratePlayerDataToCurrentVersion(
                    player.level().getServer(),
                    playerNbt,
                    "loadInv/" + world + "/" + player.getStringUUID()
                );

            ValueInput nbtReadView = TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), playerNbt);
            player.load(nbtReadView);
            player.loadAndSpawnEnderPearls(nbtReadView);

            String[] strArr = playerNbt.getString("Dimension").orElse(Level.OVERWORLD.identifier().toString()).split(":");
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(strArr[0], strArr[1]));
            ServerLevel dimension = player.level().getServer().getLevel(key);

            // Load respawn position
            Optional<CompoundTag> respawnTag = playerNbt.getCompound("respawn");
            if (respawnTag.isPresent()) {
                CompoundTag respawnData = respawnTag.get();
                int[] respawnPos = respawnData.getIntArray("pos").orElseThrow();
                String respawnDimensionStr = respawnData.getString("dimension").orElse(Level.OVERWORLD.identifier().toString());
                String[] dimParts = respawnDimensionStr.split(":");
                ResourceKey<Level> respawnDimension = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(dimParts[0], dimParts[1]));
                LevelData.RespawnData respawnDataObj = new LevelData.RespawnData(
                        new GlobalPos(respawnDimension, new BlockPos(respawnPos[0], respawnPos[1], respawnPos[2])),
                        respawnData.getFloat("angle").orElse(0F),
                        respawnData.getFloat("pitch").orElse(0F)
                );
                player.setRespawnPosition(new ServerPlayer.RespawnConfig(respawnDataObj, respawnData.getBooleanOr("forced", false)), false);
            }

            // Load player stats
            if (file.has("stats") && file.get("stats").isJsonObject()) {
                JsonObject statsData = file.getAsJsonObject("stats");

                ServerStatsCounter statHandler = player.getStats();
                for (Stat<?> stat : ((StatsCounterAccessor) statHandler).getStatMap().keySet()) {
                    player.resetStat(stat);
                }

                statHandler.parse(player.level().getServer().getFixerUpper(), statsData);
                statHandler.markAllDirty();
            }

            // Load player advancements
            if (file.has("advancements") && file.get("advancements").isJsonObject()) {
                JsonObject advancementsData = file.getAsJsonObject("advancements");
                Files.createDirectories(advancementsPath.getParent());
                Files.write(
                    advancementsPath,
                    advancementsData.toString().getBytes()
                );
            }
            player.getAdvancements().reload(player.level().getServer().getAdvancements());

            if (dimension != null) {
                player.teleport(new TeleportTransition(dimension, new Vec3(player.getX(), player.getY(), player.getZ()), Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
            }

            player.loadAndSpawnParentVehicle(nbtReadView);
        } catch (Exception ex) {
            CompoundTag playerNbt = new CompoundTag();

            ValueInput nbtReadView = TagValueInput.create(ProblemReporter.DISCARDING, player.registryAccess(), playerNbt);
            player.load(nbtReadView);
            player.loadAndSpawnEnderPearls(nbtReadView);

            player.setRespawnPosition(null, false);

            // Reset stats
            ServerStatsCounter statHandler = player.getStats();
            for (Stat<?> stat : ((StatsCounterAccessor) statHandler).getStatMap().keySet()) {
                player.resetStat(stat);
            }
            statHandler.markAllDirty();

            // Reset advancements
            JsonObject clearedAdvancementsData = new JsonObject();
            JsonObject advancementsData = new JsonObject();
            try {
                Files.createDirectories(advancementsPath.getParent());
            } catch (Exception e) {
                LOGGER.error("Error creating advancements directory: {}", e.getMessage());
            }
            try (FileReader advancementsReader = new FileReader(advancementsPath.toFile())) {
                JsonElement parsed = JsonParser.parseReader(advancementsReader);
                if (parsed != null && parsed.isJsonObject()) {
                    advancementsData = parsed.getAsJsonObject();
                }
            } catch (Exception ignored) {
            }
            if (advancementsData.has("DataVersion")) {
                clearedAdvancementsData.add("DataVersion", advancementsData.get("DataVersion"));
            }
            try (BufferedWriter advancementsWriter = Files.newBufferedWriter(advancementsPath)) {
                advancementsWriter.write(clearedAdvancementsData.toString());
            } catch (Exception e) {
                LOGGER.error("Error writing cleared advancements: {}", e.getMessage());
            }
            player.getAdvancements().reload(player.level().getServer().getAdvancements());

                // Ensure no stale advancement progress remains when a world has no saved player profile yet.
                MinecraftServer clearServer = player.level().getServer();
                CommandSourceStack clearSource = clearServer.createCommandSourceStack()
                    .withLevel((ServerLevel) player.level())
                    .withEntity(player)
                    .withPermission(LevelBasedPermissionSet.forLevel(PermissionLevel.OWNERS));
                clearServer.getCommands().performPrefixedCommand(clearSource, "advancement revoke @s everything");

            // Teleport to world spawn
            String dimensionStr = world.equals("overworld") ? "minecraft:overworld" : NAMESPACE + ":" + world;
            String[] strArr = dimensionStr.split(":");
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(strArr[0], strArr[1]));

            MinecraftServer server = player.level().getServer();
            ServerLevel serverWorld = server.getLevel(key);

            if (serverWorld != null) {
                BlockPos spawnPos = serverWorld.getRespawnData().pos();
                Vec3 worldSpawn = Vec3.atBottomCenterOf(spawnPos);
                player.teleport(new TeleportTransition(serverWorld, worldSpawn, Vec3.ZERO, 0.0F, 0.0F, TeleportTransition.DO_NOTHING));

                // Do it twice to prevent a bug where the player must log out and back in to change gamemode
                GameType gameMode = ((ServerLevelAccessor) serverWorld).getWorldProperties().getGameType();
                player.setGameMode(gameMode == GameType.CREATIVE ? GameType.SURVIVAL : GameType.CREATIVE);
                player.setGameMode(gameMode);
            }
        }
    }

    public static String getMostRecentWorldSaved(ServerPlayer player, String sourceWorld) {
        String worldFolder = getWorldFolder(player.level().getServer());
        File folder = new File(worldFolder + "/" + NAMESPACE);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            String mostRecentWorld = "";
            long mostRecentTime = 0;
            for (File file : listOfFiles) {
                if (file.isDirectory()) {
                    File playerFile = new File(file.getPath() + "/" + player.getUUID() + ".json");
                    if (playerFile.exists()) {
                        long lastModified = playerFile.lastModified();
                        if (lastModified > mostRecentTime && !file.getName().equals(sourceWorld)) {
                            mostRecentTime = lastModified;
                            mostRecentWorld = file.getName();
                        }
                    }
                }
            }
            return mostRecentWorld;
        } else {
            LOGGER.error("No worlds found in namespace folder");
            return "overworld";
        }
    }
}
