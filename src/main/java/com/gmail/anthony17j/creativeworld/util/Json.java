package com.gmail.anthony17j.creativeworld.util;

import com.github.cliftonlabs.json_simple.JsonObject;
import com.github.cliftonlabs.json_simple.Jsonable;

import java.io.IOException;
import java.io.Writer;

public class Json implements Jsonable {
    private final String player;

    public Json(String player) {
        this.player = player;
    }

    @Override
    public String toJson() {
        JsonObject json = new JsonObject();
        json.put("player", this.player);
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