package com.example.myfridge.storage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class Product {
    public final String productId;
    public final String name;
    public int amount;
    public final String unit; // "kg" / "ml"
    public String category;
    public String imageUri; // content://... or file path
    public long createdAtMs;
    public long updatedAtMs;
    public long expiresAtMs; // 0 = unknown
    public String shelfLifeRaw; // e.g. "7 days"

    public Product(
            String productId,
            String name,
            int amount,
            String unit,
            String category,
            String imageUri,
            long createdAtMs,
            long updatedAtMs,
            long expiresAtMs,
            String shelfLifeRaw
    ) {
        this.productId = productId;
        this.name = name;
        this.amount = amount;
        this.unit = unit;
        this.category = category;
        this.imageUri = imageUri;
        this.createdAtMs = createdAtMs;
        this.updatedAtMs = updatedAtMs;
        this.expiresAtMs = expiresAtMs;
        this.shelfLifeRaw = shelfLifeRaw;
    }

    public static Product createNew(String name, String unit, String category, String imageUri, long expiresAtMs, String shelfLifeRaw) {
        long now = System.currentTimeMillis();
        return new Product(UUID.randomUUID().toString(), name, 1, unit, category, imageUri, now, now, expiresAtMs, shelfLifeRaw);
    }

    public String normalizedKey() {
        return (name == null ? "" : name.trim().toLowerCase()) + "|" + (unit == null ? "" : unit.trim().toLowerCase());
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("productId", productId);
        o.put("name", name);
        o.put("amount", amount);
        o.put("unit", unit);
        o.put("category", category);
        o.put("imageUri", imageUri);
        o.put("createdAtMs", createdAtMs);
        o.put("updatedAtMs", updatedAtMs);
        o.put("expiresAtMs", expiresAtMs);
        o.put("shelfLifeRaw", shelfLifeRaw);
        o.put("key", normalizedKey());
        return o;
    }

    public static Product fromJson(JSONObject o) throws JSONException {
        return new Product(
                o.getString("productId"),
                o.optString("name", ""),
                o.optInt("amount", 1),
                o.optString("unit", ""),
                o.optString("category", ""),
                o.optString("imageUri", ""),
                o.optLong("createdAtMs", 0L),
                o.optLong("updatedAtMs", 0L),
                o.optLong("expiresAtMs", 0L),
                o.optString("shelfLifeRaw", "")
        );
    }
}

