package com.gmail.anthony17j.creativeworld.util;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsonable;
import net.minecraft.nbt.NbtCompound;

import java.io.IOException;
import java.io.Writer;

public class Json implements Jsonable {
    private String name;
    private String[] inventory;
    private String[] armor;
    private String[] enderChest;
    private String offHand;
    private int selectedSlot;
    private int xp;
    private float xpProgress;
    private String foodLevel;
    private float health;
    private String effects;
    private double posX;
    private double posY;
    private double posZ;
    private float pitch;
    private float yaw;
    private String dimension;
    private float absorption;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private String gamemode;
    private boolean fly;
    private float fallDistance;

    public Json() {
    }

    public Json(String[] inventory, String[] armor, String[] enderChest, String offHand, int selectedSlot, int xp, float xpProgress,
                String foodLevel, float health, String effects, double posX, double posY, double posZ, float pitch, float yaw, String dimension,
                float absorption, double velocityX, double velocityY, double velocityZ, String gamemode, boolean fly, float fallDistance) {
        this.inventory = inventory;
        this.armor = armor;
        this.enderChest = enderChest;
        this.offHand = offHand;
        this.selectedSlot = selectedSlot;
        this.xp = xp;
        this.xpProgress = xpProgress;
        this.foodLevel = foodLevel;
        this.health = health;
        this.effects = effects;
        this.posX = posX;
        this.posY = posY;
        this.posZ = posZ;
        this.pitch = pitch;
        this.yaw = yaw;
        this.dimension = dimension;
        this.absorption = absorption;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.gamemode = gamemode;
        this.fly = fly;
        this.fallDistance = fallDistance;
    }

    @Override
    public String toJson() {
        JsonObject json = new JsonObject();
        json.put("inventory", this.inventory);
        json.put("armor", this.armor);
        json.put("enderChest", this.enderChest);
        json.put("offHand", this.offHand);
        json.put("selectedSlot", this.selectedSlot);
        json.put("xp", this.xp);
        json.put("xpProgress", this.xpProgress);
        json.put("foodLevel", this.foodLevel);
        json.put("health", this.health);
        json.put("effects", this.effects);
        json.put("posX", this.posX);
        json.put("posY", this.posY);
        json.put("posZ", this.posZ);
        json.put("pitch", this.pitch);
        json.put("yaw", this.yaw);
        json.put("dimension", this.dimension);
        json.put("absorption", this.absorption);
        json.put("velocityX", this.velocityX);
        json.put("velocityY", this.velocityY);
        json.put("velocityZ", this.velocityZ);
        json.put("gamemode", this.gamemode);
        json.put("fly", this.fly);
        json.put("fallDistance", this.fallDistance);
        return json.toJson();
    }

    @Override
    public void toJson(Writer writable) throws IOException {
        try {
            writable.write(this.toJson());
        } catch (Exception ignored) {
        }
    }
}