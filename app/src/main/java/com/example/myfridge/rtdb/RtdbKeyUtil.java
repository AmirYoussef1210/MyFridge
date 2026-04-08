package com.example.myfridge.rtdb;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Utility class for generating Firebase Realtime Database-safe key strings.
 * <p>
 * RTDB keys must not contain the characters {@code . # $ [ ] /}.
 * All methods in this class replace those characters with underscores and
 * provide consistent, lower-cased key formats used throughout the repository
 * layer.
 * </p>
 * <p>This class is not instantiable.</p>
 */
public final class RtdbKeyUtil {

    /** Prevents instantiation. */
    private RtdbKeyUtil() {}

    /**
     * Returns a Firebase RTDB-safe version of {@code raw} by replacing the
     * forbidden characters ({@code . # $ [ ] /}) with underscores.
     *
     * @param raw the raw string to sanitise; may be {@code null}
     * @return a non-null, safe key string; {@code "unknown"} if {@code raw} is
     *         {@code null} or blank
     */
    public static String safeKey(String raw) {
        if (raw == null) return "unknown";
        String s = raw.trim();
        if (s.isEmpty()) return "unknown";
        return s
                .replace(".", "_")
                .replace("#", "_")
                .replace("$", "_")
                .replace("[", "_")
                .replace("]", "_")
                .replace("/", "_");
    }

    /**
     * Returns a lower-cased, RTDB-safe key for a product category.
     * Falls back to {@code "uncategorized"} when {@code category} is blank.
     *
     * @param category the category string; may be {@code null} or blank
     * @return a non-null, safe category key
     */
    public static String categoryKey(String category) {
        return safeKey(category == null || category.trim().isEmpty() ? "uncategorized" : category.trim().toLowerCase());
    }

    /**
     * Converts a Unix timestamp (ms) to a {@code "yyyy-MM-dd"} date string
     * suitable for use as an expiration-bucket key in RTDB.
     *
     * @param expiresAtMs the expiry timestamp in milliseconds; {@code ≤ 0}
     *                    returns {@code "unknown"}
     * @return a non-null, RTDB-safe date key or {@code "unknown"}
     */
    public static String expirationKey(long expiresAtMs) {
        if (expiresAtMs <= 0L) return "unknown";
        // Group by day to match your "expiration" level.
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(expiresAtMs));
    }

    /**
     * Derives a stable, deduplication key for a product from its name and unit.
     * The result is the RTDB-safe form of {@code "<name_lower>|<unit_lower>"}.
     * Using this as the RTDB node key means scanning the same product twice
     * increments its amount rather than creating a new entry.
     *
     * @param name the product name; may be {@code null}
     * @param unit the measurement unit; may be {@code null}
     * @return a non-null, stable RTDB-safe product key
     */
    public static String stableProductKey(String name, String unit) {
        String n = name == null ? "" : name.trim().toLowerCase();
        String u = unit == null ? "" : unit.trim().toLowerCase();
        String combined = n + "|" + u;
        return safeKey(combined);
    }
}

