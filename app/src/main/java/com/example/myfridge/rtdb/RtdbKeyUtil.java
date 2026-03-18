package com.example.myfridge.rtdb;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class RtdbKeyUtil {
    private RtdbKeyUtil() {}

    /**
     * RTDB keys cannot contain: . # $ [ ] /
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

    public static String categoryKey(String category) {
        return safeKey(category == null || category.trim().isEmpty() ? "uncategorized" : category.trim().toLowerCase());
    }

    public static String expirationKey(long expiresAtMs) {
        if (expiresAtMs <= 0L) return "unknown";
        // Group by day to match your "expiration" level.
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(expiresAtMs));
    }

    public static String stableProductKey(String name, String unit) {
        String n = name == null ? "" : name.trim().toLowerCase();
        String u = unit == null ? "" : unit.trim().toLowerCase();
        String combined = n + "|" + u;
        return safeKey(combined);
    }
}

