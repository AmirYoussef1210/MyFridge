package com.example.myfridge.storage;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StorageRepository {
    private static final String PREFS = "storage_prefs";
    private static final String KEY_PRODUCTS = "products_v1";

    private final SharedPreferences prefs;

    public StorageRepository(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<Product> loadAll() {
        String raw = prefs.getString(KEY_PRODUCTS, "[]");
        List<Product> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(Product.fromJson(o));
            }
        } catch (JSONException e) {
            // If corrupted, return empty list (keeps app usable).
            return new ArrayList<>();
        }
        return out;
    }

    public void upsertIncrementByName(Product scanned) {
        List<Product> current = loadAll();

        Map<String, Product> byKey = new HashMap<>();
        for (Product p : current) {
            byKey.put(p.normalizedKey(), p);
        }

        String key = scanned.normalizedKey();
        Product existing = byKey.get(key);
        if (existing != null) {
            existing.amount += 1;
            existing.updatedAtMs = System.currentTimeMillis();

            // Keep earliest known expiry (safer for "about to expire" filter).
            if (existing.expiresAtMs <= 0L) {
                existing.expiresAtMs = scanned.expiresAtMs;
            } else if (scanned.expiresAtMs > 0L) {
                existing.expiresAtMs = Math.min(existing.expiresAtMs, scanned.expiresAtMs);
            }

            if ((existing.shelfLifeRaw == null || existing.shelfLifeRaw.trim().isEmpty()) &&
                    scanned.shelfLifeRaw != null && !scanned.shelfLifeRaw.trim().isEmpty()) {
                existing.shelfLifeRaw = scanned.shelfLifeRaw;
            }

            if ((existing.category == null || existing.category.trim().isEmpty()) &&
                    scanned.category != null && !scanned.category.trim().isEmpty()) {
                existing.category = scanned.category;
            }

            if ((existing.imageUri == null || existing.imageUri.trim().isEmpty()) &&
                    scanned.imageUri != null && !scanned.imageUri.trim().isEmpty()) {
                existing.imageUri = scanned.imageUri;
            }
            // We keep original productId; just increment amount.
        } else {
            current.add(scanned);
        }

        saveAll(current);
    }

    public void saveAll(List<Product> products) {
        JSONArray arr = new JSONArray();
        for (Product p : products) {
            try {
                arr.put(p.toJson());
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString(KEY_PRODUCTS, arr.toString()).apply();
    }
}

