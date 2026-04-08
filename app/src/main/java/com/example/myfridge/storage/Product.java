package com.example.myfridge.storage;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Immutable-by-convention data model for a single fridge inventory product.
 * <p>
 * A product is identified by a stable {@link #productId} (UUID) and a
 * {@link #normalizedKey()} derived from its name and unit, which is used as the
 * Firebase RTDB node key so repeated scans of the same item increment
 * {@link #amount} rather than creating duplicates.
 * </p>
 * <p>
 * Supports JSON serialisation via {@link #toJson()} / {@link #fromJson(org.json.JSONObject)}.
 * </p>
 */
public class Product {
    /** Unique identifier for this product instance (UUID string). */
    public final String productId;
    /** Display name of the product (e.g. "Milk"). */
    public final String name;
    /** Number of units in inventory. */
    public int amount;
    /** Measurement unit: {@code "kg"}, {@code "ml"} or {@code "piece"}. */
    public final String unit;
    /** Product category (e.g. {@code "dairy"}, {@code "produce"}). */
    public String category;
    /** Content URI or file path to the product photo; empty string if none. */
    public String imageUri;
    /** Unix timestamp (ms) when the product was first added. */
    public long createdAtMs;
    /** Unix timestamp (ms) of the last modification. */
    public long updatedAtMs;
    /** Unix timestamp (ms) of the product's expiry date; {@code 0} = unknown. */
    public long expiresAtMs;
    /** Human-readable shelf-life string returned by AI (e.g. {@code "7 days"}). */
    public String shelfLifeRaw;

    /**
     * Full constructor; prefer {@link #createNew} for newly scanned products.
     *
     * @param productId    stable UUID string
     * @param name         display name
     * @param amount       quantity in inventory
     * @param unit         measurement unit
     * @param category     category label
     * @param imageUri     photo URI or empty string
     * @param createdAtMs  creation timestamp in milliseconds
     * @param updatedAtMs  last-updated timestamp in milliseconds
     * @param expiresAtMs  expiry timestamp in milliseconds; {@code 0} if unknown
     * @param shelfLifeRaw raw AI shelf-life string
     */
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

    /**
     * Factory method that creates a new product with a freshly generated UUID,
     * an initial amount of 1 and {@code createdAtMs} / {@code updatedAtMs} both
     * set to the current time.
     *
     * @param name         display name
     * @param unit         measurement unit
     * @param category     category label
     * @param imageUri     photo URI or empty string
     * @param expiresAtMs  expiry timestamp in ms; {@code 0} if unknown
     * @param shelfLifeRaw raw AI shelf-life string
     * @return a new {@code Product} instance
     */
    public static Product createNew(String name, String unit, String category, String imageUri, long expiresAtMs, String shelfLifeRaw) {
        long now = System.currentTimeMillis();
        return new Product(UUID.randomUUID().toString(), name, 1, unit, category, imageUri, now, now, expiresAtMs, shelfLifeRaw);
    }

    /**
     * Returns a stable, lower-cased deduplication key in the form
     * {@code "<name>|<unit>"}. Used as the Firebase RTDB node key so that
     * scanning the same product twice increments its amount rather than
     * creating a new entry.
     *
     * @return non-null deduplication key
     */
    public String normalizedKey() {
        return (name == null ? "" : name.trim().toLowerCase()) + "|" + (unit == null ? "" : unit.trim().toLowerCase());
    }

    /**
     * Serialises this product to a {@link org.json.JSONObject}.
     *
     * @return a JSON representation of all fields
     * @throws org.json.JSONException if serialisation fails
     */
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

    /**
     * Deserialises a {@link org.json.JSONObject} into a {@code Product}.
     * Optional fields fall back to sensible defaults (amount = 1, timestamps = 0, etc.).
     *
     * @param o the JSON object to deserialise; must contain {@code "productId"}
     * @return the deserialised {@code Product}
     * @throws org.json.JSONException if the required {@code "productId"} field is missing
     */
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

