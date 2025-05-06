package com.gmail.anthony17j.multiworld.util;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsonable;

import java.io.IOException;
import java.io.Writer;

public class Json implements Jsonable {
    private final String player;
    private final JsonObject respawn;
    private final JsonObject stats;
    private final JsonObject advancements;

    public Json(String player, JsonObject respawn, JsonObject stats, JsonObject advancements) {
        this.player = player;
        this.respawn = respawn;
        this.stats = stats;
        this.advancements = advancements;
    }

    // Constructeur pour la compatibilité avec l'ancien code
    public Json(String player) {
        this.player = player;
        this.respawn = new JsonObject();
        this.stats = new JsonObject();
        this.advancements = new JsonObject();
    }

    @Override
    public String toJson() {
        JsonObject json = new JsonObject();
        json.put("player", this.player);
        json.put("respawn", this.respawn);
        json.put("stats", this.stats);
        json.put("advancements", this.advancements);
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