package com.example.myfridge.shopping;

/**
 * Data model for a single row in the user's shopping list.
 * <p>
 * {@link #key} doubles as the Firebase RTDB node key and is derived from
 * {@link #name} via {@link com.example.myfridge.rtdb.RtdbKeyUtil#safeKey}, so
 * repeated additions of the same product update the existing row rather than
 * creating duplicates.
 * </p>
 * <p>
 * {@link #saved} / {@code isRecurring} controls whether the item is preserved
 * in RTDB after being marked as bought (and re-listed on the next shopping
 * session via the recurring-defaults list).
 * </p>
 */
public class ShoppingItem {
    /** Firebase-safe key derived from the product name; used as the RTDB node key. */
    public final String key;
    /** Display name of the shopping item. */
    public final String name;
    /** Category: {@code "dairy"}, {@code "produce"}, or {@code "other"}. */
    public final String category;
    /** Desired quantity to purchase. */
    public int howMany;
    /** Whether the item has been checked off in the shopping list. */
    public boolean bought;
    /** Whether the item should persist (be re-added) after it has been bought. */
    public boolean saved;
    /** Unix timestamp (ms) of the last modification. */
    public long updatedAtMs;

    /**
     * Creates a shopping list item.
     *
     * @param key          Firebase-safe node key (derived from name)
     * @param name         display name
     * @param category     category label
     * @param howMany      desired quantity
     * @param bought       whether it has been checked off
     * @param saved        whether it should persist after being bought
     * @param updatedAtMs  last-updated timestamp in ms
     */
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

