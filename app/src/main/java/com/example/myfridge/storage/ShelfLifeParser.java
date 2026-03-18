package com.example.myfridge.storage;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShelfLifeParser {
    private static final Pattern P = Pattern.compile("(\\d+)\\s*(day|days|week|weeks|month|months|year|years)", Pattern.CASE_INSENSITIVE);

    private ShelfLifeParser() {}

    /**
     * Best-effort parse of strings like "7 days", "12 months", "2 weeks".
     * Returns duration in millis. Returns 0 if unknown/unparseable.
     */
    public static long parseToDurationMillis(String raw) {
        if (raw == null) return 0L;
        String s = raw.trim().toLowerCase(Locale.US);
        if (s.isEmpty()) return 0L;

        Matcher m = P.matcher(s);
        if (!m.find()) return 0L;

        long n;
        try {
            n = Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return 0L;
        }

        String unit = m.group(2);
        long days;
        if (unit.startsWith("day")) days = n;
        else if (unit.startsWith("week")) days = n * 7L;
        else if (unit.startsWith("month")) days = n * 30L;
        else if (unit.startsWith("year")) days = n * 365L;
        else return 0L;

        return days * 24L * 60L * 60L * 1000L;
    }
}

