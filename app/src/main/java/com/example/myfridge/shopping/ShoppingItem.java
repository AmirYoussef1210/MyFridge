package com.example.myfridge.shopping;

/**
 * Shopping list row saved per user.
 * Key is used as the RTDB node key (derived from the product name).
 */
public class ShoppingItem {
    public final String key;
    public final String name;
    public final String category; // "dairy" | "produce" | "other"

    public int howMany;
    public boolean bought;
    public boolean saved;
    public long updatedAtMs;

    public ShoppingItem(
            String key,
            String name,
            String category,
            int howMany,
            boolean bought,
            boolean saved,
            long updatedAtMs
    ) {
        this.key = key;
        this.name = name;
        this.category = category;
        this.howMany = howMany;
        this.bought = bought;
        this.saved = saved;
        this.updatedAtMs = updatedAtMs;
    }
}

