package com.gmail.anthony17j.multiworld;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsoner;
import com.gmail.anthony17j.multiworld.mixin.StatHandlerAccessor;
import com.gmail.anthony17j.multiworld.util.Json;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;

import static com.gmail.anthony17j.multiworld.MultiWorld.LOGGER;
import static com.gmail.anthony17j.multiworld.MultiWorld.namespace;

public class Utils {
    public static void saveInv(ServerPlayerEntity player, String world) {
        // LOGGER.info("Saving player data for {} in world {}", player.getName().getString(), world);
        try {
            String worldFolder = player.getServer().getSaveProperties().getLevelName();

            // create a writer
            if (new File(worldFolder + "/" + namespace + "/" + world).mkdirs()) {
                LOGGER.info("Directory created: {}/{}/{}", worldFolder, namespace, world);
            }
            BufferedWriter writer = Files.newBufferedWriter(Paths.get(worldFolder + "/" + namespace + "/" + world + "/" + player.getUuidAsString() + ".json"));

            NbtCompound tag = new NbtCompound();
            player.writeNbt(tag);

            // Remove ender pearls the player had launched (they will be put back on load)
            player.getEnderPearls().forEach((enderPearl) -> enderPearl.remove(Entity.RemovalReason.DISCARDED));

            // Remove player vehicle (will be put back on load)
            if (player.getRootVehicle() != player) {
                player.getRootVehicle().remove(Entity.RemovalReason.DISCARDED);
            }

            // Save respawn position
            JsonObject respawnJson = new JsonObject();
            ServerPlayerEntity.Respawn respawn = player.getRespawn();

            if (respawn != null) {
                respawnJson.put("dimension", respawn.dimension().getValue().toString());
                respawnJson.put("posX", respawn.pos().getX());
                respawnJson.put("posY", respawn.pos().getY());
                respawnJson.put("posZ", respawn.pos().getZ());
                respawnJson.put("angle", respawn.angle());
                respawnJson.put("forced", respawn.forced());
            }

            // Save player stats
            player.getStatHandler().save();
            // go to the world folder, then "stats" folder, inside there is a json file with name uuid of the player, copy the content to a key "stats" in the json
            JsonObject statsJson = new JsonObject();
            File statsFolder = new File(worldFolder + "/stats");
            File[] statsFiles = statsFolder.listFiles();
            if (statsFiles != null) {
                for (File statsFile : statsFiles) {
                    if (statsFile.getName().equals(player.getUuidAsString() + ".json")) {
                        try (FileReader statsReader = new FileReader(statsFile)) {
                            statsJson = (JsonObject) Jsoner.deserialize(statsReader);
                        }
                    }
                }
            }

            // Save player advancements
            player.getAdvancementTracker().save();
            // same as above but for advancements folder
            JsonObject advancementsJson = new JsonObject();
            File advancementsFolder = new File(worldFolder + "/advancements");
            File[] advancementsFiles = advancementsFolder.listFiles();
            if (advancementsFiles != null) {
                for (File advancementsFile : advancementsFiles) {
                    if (advancementsFile.getName().equals(player.getUuidAsString() + ".json")) {
                        try (FileReader advancementsReader = new FileReader(advancementsFile)) {
                            advancementsJson = (JsonObject) Jsoner.deserialize(advancementsReader);
                        }
                    }
                }
            }

            Json uuid = new Json(tag.toString(), respawnJson, statsJson, advancementsJson);

            // convert Json list to JSON and write to file.json
            Jsoner.serialize(uuid, writer);

            // close the writer
            writer.close();

        } catch (Exception ex) {
            LOGGER.error("Error saving player data: {}", ex.getMessage());
        }
    }

    public static void loadInv(ServerPlayerEntity player, String world) {
        // LOGGER.info("Loading player data for {} in world {}", player.getName().getString(), world);

        String worldFolder = player.getServer().getSaveProperties().getLevelName();

        try (FileReader fileReader = new FileReader(worldFolder + "/" + namespace + "/" + world + "/" + player.getUuidAsString() + ".json")) {
            JsonObject file = (JsonObject) Jsoner.deserialize(fileReader);

            String playerString = (String) file.get("player");
            NbtCompound playerNbt = StringNbtReader.readCompound(playerString);

            player.readNbt(playerNbt);
            player.readGameModeNbt(playerNbt);
            player.readEnderPearls(playerNbt);
            player.getAbilities().readNbt(playerNbt);

            // Load respawn position
            if (file.containsKey("respawn")) {
                JsonObject respawnData = (JsonObject) file.get("respawn");

                if (!respawnData.isEmpty()) {
                    String dimensionStr = (String) respawnData.get("dimension");
                    String[] dimParts = dimensionStr.split(":");
                    RegistryKey<World> dimension = RegistryKey.of(
                            RegistryKeys.WORLD,
                            Identifier.of(dimParts[0], dimParts[1])
                    );

                    BlockPos respawnPos = new BlockPos(
                            ((Number) respawnData.get("posX")).intValue(),
                            ((Number) respawnData.get("posY")).intValue(),
                            ((Number) respawnData.get("posZ")).intValue()
                    );

                    float angle = ((Number) respawnData.get("angle")).floatValue();
                    boolean forced = (Boolean) respawnData.get("forced");

                    // Appliquer le point de réapparition
                    ServerPlayerEntity.Respawn respawn = new ServerPlayerEntity.Respawn(
                            dimension,
                            respawnPos,
                            angle,
                            forced
                    );
                    player.setSpawnPoint(respawn, false);
                }
            }

            // Load player stats
            if (file.containsKey("stats")) {
                JsonObject statsData = (JsonObject) file.get("stats");

                ServerStatHandler statHandler = player.getStatHandler();
                ObjectSet<Stat<?>> stats = ((StatHandlerAccessor) statHandler).getStatMap().keySet();
                for (Stat<?> stat : stats) {
                    player.resetStat(stat);
                }

                statHandler.parse(player.getServer().getDataFixer(), statsData.toJson());
                statHandler.updateStatSet();
            }

            // Load player advancements
            if (file.containsKey("advancements")) {
                JsonObject advancementsData = (JsonObject) file.get("advancements");
                Files.write(
                    Paths.get(worldFolder + "/advancements/" + player.getUuidAsString() + ".json"),
                    advancementsData.toJson().getBytes()
                );
                player.getAdvancementTracker().reload(player.getServer().getAdvancementLoader());
            }

            String[] strArr = playerNbt.getString("Dimension").orElse("minecraft:overworld").split(":");
            RegistryKey<DimensionOptions> DIMENSION_KEY = RegistryKey.of(RegistryKeys.DIMENSION, Identifier.of(strArr[0], strArr[1]));
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, DIMENSION_KEY.getValue());
            ServerWorld dimension = player.getEntityWorld().getServer().getWorld(key);

            player.teleport(
                dimension,
                player.getX(),
                player.getY(),
                player.getZ(),
                PositionFlag.getFlags(0),
                player.getYaw(),
                player.getPitch(),
                false
            );

            player.readRootVehicle(playerNbt);
        } catch (Exception ex) {
            NbtCompound playerNbt = new NbtCompound();

            player.readNbt(playerNbt);
            player.readGameModeNbt(playerNbt);
            player.readEnderPearls(playerNbt);
            player.readCustomDataFromNbt(playerNbt);
            player.getAbilities().readNbt(playerNbt);

            player.setSpawnPoint(null, false);

            // Reset stats
            ServerStatHandler statHandler = player.getStatHandler();
            ObjectSet<Stat<?>> stats = ((StatHandlerAccessor) statHandler).getStatMap().keySet();
            for (Stat<?> stat : stats) {
                player.resetStat(stat);
            }
            statHandler.updateStatSet();

            // Reset advancements
            JsonObject clearedAdvancementsData = new JsonObject();
            JsonObject advancementsData = new JsonObject();
            try (FileReader advancementsReader = new FileReader(worldFolder + "/advancements/" + player.getUuidAsString() + ".json")) {
                advancementsData = (JsonObject) Jsoner.deserialize(advancementsReader);
            } catch (Exception e) {
                LOGGER.error("Error loading player advancements: {}", e.getMessage());
            }
            clearedAdvancementsData.put("DataVersion", advancementsData.get("DataVersion"));
            try (BufferedWriter advancementsWriter = Files.newBufferedWriter(Paths.get(worldFolder + "/advancements/" + player.getUuidAsString() + ".json"))) {
                Jsoner.serialize(clearedAdvancementsData, advancementsWriter);
            } catch (Exception e) {
                LOGGER.error("Error writing cleared advancements: {}", e.getMessage());
            }
            player.getAdvancementTracker().reload(player.getServer().getAdvancementLoader());

            // Teleport
            String dimension = world.equals("overworld") ? "minecraft:overworld" : namespace + ":" + world;

            String[] strArr = dimension.split(":");
            RegistryKey<DimensionOptions> DIMENSION_KEY = RegistryKey.of(RegistryKeys.DIMENSION, Identifier.of(strArr[0], strArr[1]));
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, DIMENSION_KEY.getValue());

            // teleport to world spawn
            MinecraftServer server = player.getServer();
            ServerWorld serverWorld = server.getWorld(key);
//            int spawnRadius = server.getSpawnRadius(serverWorld);
//            BlockPos worldSpawn = serverWorld.getSpawnPos();
//            worldSpawn = worldSpawn.add(
//                    (int) (Math.random() * spawnRadius * 2) - spawnRadius,
//                    0,
//                    (int) (Math.random() * spawnRadius * 2) - spawnRadius
//            );
//            Vec3d centerPos = worldSpawn.toCenterPos();
//            int i = serverWorld.getWorldChunk(worldSpawn).sampleHeightmap(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, worldSpawn.getX(), worldSpawn.getZ()) + 1;
//            worldSpawn = BlockPos.ofFloored(centerPos.x, i, centerPos.z);
            Vec3d worldSpawn = player.getWorldSpawnPos(serverWorld, serverWorld.getSpawnPos()).toBottomCenterPos();
            TeleportTarget teleportTarget = new TeleportTarget(serverWorld, worldSpawn, Vec3d.ZERO, 0.0F, 0.0F, false, false, Set.of(), TeleportTarget.NO_OP);

            // player teleport
            player.teleport(teleportTarget.world(), teleportTarget.position().getX(), teleportTarget.position().getY(), teleportTarget.position().getZ(), PositionFlag.getFlags(0), teleportTarget.yaw(), teleportTarget.pitch(), true);

            // Do it twice to prevent a bug where the player must log out and back in to change gamemode
            GameMode gameMode = server.getDefaultGameMode();

            player.changeGameMode(GameMode.byIndex(player.getGameMode().getIndex() + 1 % 3));
            player.changeGameMode(gameMode);
        }
    }

    public static String getMostRecentWorldSaved(ServerPlayerEntity player) {
        // in the namespace folder, check every folder (world), then inside check the file with the user uuid. The world with the most recent file is the one we want
        File folder = new File(namespace);
        File[] listOfFiles = folder.listFiles();
        if (listOfFiles != null) {
            String mostRecentWorld = "";
            long mostRecentTime = 0;
            for (File file : listOfFiles) {
                if (file.isDirectory()) {
                    File playerFile = new File(file.getPath() + "/" + player.getUuid() + ".json");
                    if (playerFile.exists()) {
                        long lastModified = playerFile.lastModified();
                        if (lastModified > mostRecentTime) {
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
