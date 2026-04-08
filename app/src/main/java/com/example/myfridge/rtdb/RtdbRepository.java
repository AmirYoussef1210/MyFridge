package com.example.myfridge.rtdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myfridge.storage.Product;
import com.example.myfridge.shopping.ShoppingItem;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Repository class for all Firebase Realtime Database operations in MyFridge.
 * <p>
 * Covers three data domains:
 * <ol>
 *   <li><b>Inventory</b> — stored under {@code /inventory/<uid>/<category>/<expiration>/<productKey>}</li>
 *   <li><b>Shopping list</b> — stored under {@code /ShoppingListItems/<uid_itemKey>}</li>
 *   <li><b>User preferences</b> — stored under {@code /users/<uid>}</li>
 * </ol>
 * All database reads are single-value event listeners; writes use a mix of
 * {@code setValue}, {@code updateChildren} and Firebase Transactions for
 * atomic counter increments.
 * </p>
 * <p>
 * Callbacks are always invoked on the Firebase listener thread; callers must
 * dispatch to the main thread with {@code runOnUiThread} when updating UI.
 * </p>
 */
public class RtdbRepository {

    private final DatabaseReference root;

    /**
     * Creates a new repository instance rooted at the default Firebase database reference.
     */
    public RtdbRepository() {
        this.root = FirebaseDatabase.getInstance().getReference();
    }

    /**
     * Callback for asynchronous inventory fetch operations.
     */
    public interface ProductsCallback {
        /**
         * Called with the full list of products on success.
         *
         * @param products non-null list (may be empty)
         */
        void onSuccess(@NonNull List<Product> products);

        /**
         * Called when the database operation is cancelled or fails.
         *
         * @param error the Firebase error
         */
        void onFailure(@NonNull DatabaseError error);
    }

    /**
     * Callback for asynchronous user-preference fetch operations.
     */
    public interface UserPrefsCallback {
        /**
         * Called with the user's saved preferences.
         *
         * @param units                 measurement unit system; empty string if not set
         * @param daysBeforeExpireChoice expiry-alert window in days (clamped 1–30, default 2)
         */
        void onSuccess(@NonNull String units, int daysBeforeExpireChoice);

        /**
         * Called when the database operation is cancelled or fails.
         *
         * @param error the Firebase error
         */
        void onFailure(@NonNull DatabaseError error);
    }

    /**
     * Callback for asynchronous shopping-list fetch operations.
     */
    public interface ShoppingItemsCallback {
        /**
         * Called with the user's current shopping list.
         *
         * @param items non-null list (may be empty)
         */
        void onSuccess(@NonNull List<ShoppingItem> items);

        /**
         * Called when the database operation is cancelled or fails.
         *
         * @param error the Firebase error
         */
        void onFailure(@NonNull DatabaseError error);
    }

    /**
     * Callback for asynchronous recurring-defaults fetch operations.
     */
    public interface RecurringDefaultsCallback {
        /**
         * Called with the list of items the user has marked as recurring defaults.
         *
         * @param items non-null list (may be empty)
         */
        void onSuccess(@NonNull List<ShoppingItem> items);

        /**
         * Called when the database operation is cancelled or fails.
         *
         * @param error the Firebase error
         */
        void onFailure(@NonNull DatabaseError error);
    }

    /**
     * Callback used by {@link #ensureShoppingListDefaults} to signal completion.
     */
    public interface EnsureShoppingDefaultsCallback {
        /** Called when defaults have been seeded or already exist. */
        void onSuccess();

        /**
         * Called when the database operation is cancelled or fails.
         *
         * @param error the Firebase error
         */
        void onFailure(@NonNull DatabaseError error);
    }

    private static class DefaultShoppingSeed {
        final String name;
        final String category; // "dairy" | "produce" | "other"
        final int howMany;

        DefaultShoppingSeed(String name, String category, int howMany) {
            this.name = name;
            this.category = category;
            this.howMany = howMany;
        }
    }

    // A small “normal fridge” starter list for new users.
    private static final DefaultShoppingSeed[] DEFAULT_SHOPPING_SEEDS = new DefaultShoppingSeed[]{
            new DefaultShoppingSeed("Milk", "dairy", 1),
            new DefaultShoppingSeed("Yogurt", "dairy", 1),
            new DefaultShoppingSeed("Butter", "dairy", 1),
            new DefaultShoppingSeed("Cheese", "dairy", 1),
            new DefaultShoppingSeed("Eggs", "dairy", 1),

            new DefaultShoppingSeed("Apples", "produce", 1),
            new DefaultShoppingSeed("Bananas", "produce", 1),
            new DefaultShoppingSeed("Lettuce", "produce", 1),
            new DefaultShoppingSeed("Tomatoes", "produce", 1),
            new DefaultShoppingSeed("Onions", "produce", 1),
            new DefaultShoppingSeed("Carrots", "produce", 1)
    };

    /**
     * Reads /users/&lt;uid&gt; for units and daysBeforeExpireChoice.
     * Defaults: units "", days 2 if missing or invalid.
     */
    public void fetchUserPreferences(@NonNull FirebaseUser user, @NonNull UserPrefsCallback callback) {
        root.child("users")
                .child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String units = snapshot.child("units").getValue(String.class);
                        if (units == null) units = "";
                        Long daysL = snapshot.child("daysBeforeExpireChoice").getValue(Long.class);
                        int days = daysL == null ? 2 : (int) Math.max(1L, Math.min(30L, daysL));
                        callback.onSuccess(units, days);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onFailure(error);
                    }
                });
    }

    /**
     * Creates /users/<uid> defaults only if missing.
     */
    public void ensureUserProfile(@NonNull FirebaseUser user) {
        String uid = user.getUid();
        DatabaseReference userRef = root.child("users").child(uid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) return;

                String display = user.getDisplayName();
                if (display == null || display.trim().isEmpty()) display = user.getEmail();
                if (display == null || display.trim().isEmpty()) display = "User";

                UserProfile defaults = new UserProfile(
                        display,
                        "",
                        0
                );
                userRef.setValue(defaults);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // No-op (offline / permission / etc.). App remains usable.
            }
        });
    }

    /**
     * Upserts product under:
     * /inventory/<uid>/<category>/<expiration>/<productID>
     *
     * productID is a stable key derived from name+unit so repeated scans increment amount.
     */
    public void upsertInventoryIncrement(@NonNull FirebaseUser user, @NonNull Product p) {
        DatabaseReference productRef = productRef(user, p);

        // Set metadata (idempotent) and atomically increment amount.
        Map<String, Object> meta = new HashMap<>();
        meta.put("name", p.name);
        meta.put("units", p.unit);
        meta.put("category", p.category);
        meta.put("image", p.imageUri == null ? "" : p.imageUri);
        meta.put("expiresAtMs", p.expiresAtMs);
        meta.put("updatedAtMs", System.currentTimeMillis());
        productRef.updateChildren(meta);

        productRef.child("amount").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Long cur = currentData.getValue(Long.class);
                long next = (cur == null ? 0L : cur) + 1L;
                currentData.setValue(next);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                // No-op
            }
        });

        // Ensure this product has a numeric productID using a per-user counter tree.
        ensureProductId(user, productRef);
    }

    /**
     * Sets the amount of the given product in RTDB. If {@code newAmount ≤ 0}
     * the entire product node is removed.
     *
     * @param user      the authenticated user
     * @param p         the product to update
     * @param newAmount the new quantity; must be ≥ 0 (0 removes the node)
     */
    public void updateInventoryAmount(@NonNull FirebaseUser user, @NonNull Product p, int newAmount) {
        DatabaseReference ref = productRef(user, p).child("amount");
        if (newAmount <= 0) {
            // If amount goes to 0, remove the node.
            productRef(user, p).removeValue();
        } else {
            ref.setValue(newAmount);
        }
    }

    /**
     * Permanently removes the product node from the RTDB inventory.
     *
     * @param user the authenticated user
     * @param p    the product to delete
     */
    public void deleteInventoryItem(@NonNull FirebaseUser user, @NonNull Product p) {
        productRef(user, p).removeValue();
    }

    public void changeInventoryExpirationDate(
            @NonNull FirebaseUser user,
            @NonNull Product p,
            long newExpiresAtMs
    ) {
        // If only the time changed but the day bucket stays the same, just update expiresAtMs.
        String oldExpKey = RtdbKeyUtil.expirationKey(p.expiresAtMs);
        String newExpKey = RtdbKeyUtil.expirationKey(newExpiresAtMs);
        if (oldExpKey.equals(newExpKey)) {
            DatabaseReference oldRef = productRef(user, p);
            Map<String, Object> updates = new HashMap<>();
            updates.put("expiresAtMs", newExpiresAtMs);
            updates.put("updatedAtMs", System.currentTimeMillis());
            oldRef.updateChildren(updates);
            return;
        }

        // Otherwise, move the node from the old expiration bucket to the new one.
        String uid = user.getUid();
        String categoryKey = RtdbKeyUtil.categoryKey(p.category);
        String productKey = RtdbKeyUtil.stableProductKey(p.name, p.unit);

        DatabaseReference oldRef = root.child("inventory")
                .child(uid)
                .child(categoryKey)
                .child(oldExpKey)
                .child(productKey);

        DatabaseReference newRef = root.child("inventory")
                .child(uid)
                .child(categoryKey)
                .child(newExpKey)
                .child(productKey);

        Map<String, Object> meta = new HashMap<>();
        meta.put("name", p.name);
        meta.put("units", p.unit);
        meta.put("category", p.category);
        meta.put("image", p.imageUri == null ? "" : p.imageUri);
        meta.put("amount", p.amount);
        meta.put("expiresAtMs", newExpiresAtMs);
        meta.put("updatedAtMs", System.currentTimeMillis());

        Long existingProductId = tryParseProductId(p.productId);
        if (existingProductId != null) {
            meta.put("productID", existingProductId);
        }

        // Write to new bucket then remove old bucket.
        newRef.updateChildren(meta);
        oldRef.removeValue();

        // If productID wasn't present/couldn't be parsed, ensure it exists in the new node.
        ensureProductId(user, newRef);
    }

    /**
     * Builds the Firebase {@link DatabaseReference} for a product node under
     * {@code /inventory/<uid>/<categoryKey>/<expirationKey>/<productKey>}.
     *
     * @param user the authenticated user
     * @param p    the product whose reference to build
     * @return the database reference for the product node
     */
    private DatabaseReference productRef(@NonNull FirebaseUser user, @NonNull Product p) {
        String uid = user.getUid();
        String categoryKey = RtdbKeyUtil.categoryKey(p.category);
        String expirationKey = RtdbKeyUtil.expirationKey(p.expiresAtMs);
        String productKey = RtdbKeyUtil.stableProductKey(p.name, p.unit);

        return root.child("inventory")
                .child(uid)
                .child(categoryKey)
                .child(expirationKey)
                .child(productKey);
    }

    /**
     * Assigns a numeric {@code productID} to the product node if one does not
     * already exist. Uses a per-user atomic counter at
     * {@code /counters/<uid>/nextProductId} to generate sequential IDs.
     *
     * @param user       the authenticated user
     * @param productRef the RTDB reference of the product node to assign an ID to
     */
    private void ensureProductId(@NonNull FirebaseUser user, @NonNull DatabaseReference productRef) {
        // If productID already exists, do nothing.
        productRef.child("productID").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    return;
                }

                DatabaseReference counterRef = root
                        .child("counters")
                        .child(user.getUid())
                        .child("nextProductId");

                counterRef.runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        Long cur = currentData.getValue(Long.class);
                        long next = (cur == null ? 1L : cur + 1L);
                        currentData.setValue(next);
                        return Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                        if (!committed || error != null || currentData == null) return;
                        Long id = currentData.getValue(Long.class);
                        if (id != null) {
                            productRef.child("productID").setValue(id);
                        }
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ignore; productID is optional.
            }
        });
    }

    /**
     * Attempts to parse the given string as a {@code Long} product ID.
     *
     * @param raw the raw product ID string (may be {@code null} or blank)
     * @return the parsed {@code Long} value, or {@code null} if unparseable
     */
    private static Long tryParseProductId(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Reads and flattens:
     * /inventory/<uid>/<category>/<expiration>/<productID>
     */
    public void fetchAllInventory(@NonNull FirebaseUser user, @NonNull ProductsCallback callback) {
        root.child("inventory")
                .child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<Product> out = new ArrayList<>();

                        for (DataSnapshot categorySnap : snapshot.getChildren()) {
                            String categoryKey = categorySnap.getKey();
                            for (DataSnapshot expSnap : categorySnap.getChildren()) {
                                String expirationKey = expSnap.getKey();
                                for (DataSnapshot prodSnap : expSnap.getChildren()) {
                                    Long productIdL = prodSnap.child("productID").getValue(Long.class);
                                    String productId = productIdL == null ? null : String.valueOf(productIdL);
                                    if (productId == null || productId.trim().isEmpty()) productId = prodSnap.getKey();

                                    String name = prodSnap.child("name").getValue(String.class);
                                    String units = prodSnap.child("units").getValue(String.class);
                                    String category = prodSnap.child("category").getValue(String.class);
                                    String image = prodSnap.child("image").getValue(String.class);

                                    Long amountL = prodSnap.child("amount").getValue(Long.class);
                                    int amount = amountL == null ? 0 : (int) Math.max(0L, Math.min(Integer.MAX_VALUE, amountL));

                                    Long expiresAtMs = prodSnap.child("expiresAtMs").getValue(Long.class);
                                    long expires = expiresAtMs == null ? parseExpirationKeyToMs(expirationKey) : expiresAtMs;

                                    Long updatedAtMs = prodSnap.child("updatedAtMs").getValue(Long.class);
                                    long updated = updatedAtMs == null ? 0L : updatedAtMs;

                                    if (name == null) name = "";
                                    if (units == null) units = "";
                                    if (category == null || category.trim().isEmpty()) category = categoryKey == null ? "" : categoryKey;
                                    if (image == null) image = "";

                                    // createdAt is not tracked in RTDB yet; we reuse updatedAt.
                                    Product p = new Product(
                                            productId == null ? "" : productId,
                                            name,
                                            amount,
                                            units,
                                            category,
                                            image,
                                            updated,
                                            updated,
                                            expires,
                                            "" // shelfLifeRaw not stored in RTDB
                                    );
                                    out.add(p);
                                }
                            }
                        }

                        callback.onSuccess(out);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onFailure(error);
                    }
                });
    }

    /**
     * Ensures /shopping/<uid>/... has defaults only for new users.
     */
    public void ensureShoppingListDefaults(
            @NonNull FirebaseUser user,
            @NonNull EnsureShoppingDefaultsCallback callback
    ) {
        DatabaseReference listRef = root.child("ShoppingListItems");
        listRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot itemSnap : snapshot.getChildren()) {
                        String userId = itemSnap.child("userID").getValue(String.class);
                        if (user.getUid().equals(userId)) {
                            callback.onSuccess();
                            return;
                        }
                    }
                }

                long now = System.currentTimeMillis();
                Map<String, Object> updates = new HashMap<>();

                for (DefaultShoppingSeed seed : DEFAULT_SHOPPING_SEEDS) {
                    String itemKey = RtdbKeyUtil.safeKey(seed.name);
                    String storageKey = user.getUid() + "_" + itemKey;

                    Map<String, Object> itemMap = new HashMap<>();
                    itemMap.put("itemId", itemKey);
                    itemMap.put("name", seed.name);
                    itemMap.put("category", seed.category);
                    itemMap.put("requiredAmount", (long) Math.max(1, seed.howMany));
                    itemMap.put("bought", false);
                    itemMap.put("isRecurring", false);
                    itemMap.put("userID", user.getUid());
                    itemMap.put("updatedAtMs", now);

                    updates.put(storageKey, itemMap);
                }

                listRef.updateChildren(updates, (error, ref) -> {
                    if (error != null) callback.onFailure(error);
                    else callback.onSuccess();
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onFailure(error);
            }
        });
    }

    public void fetchShoppingList(@NonNull FirebaseUser user, @NonNull ShoppingItemsCallback callback) {
        Query q = root.child("ShoppingListItems")
                .orderByChild("userID")
                .equalTo(user.getUid());
        q
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<ShoppingItem> out = new ArrayList<>();

                        for (DataSnapshot itemSnap : snapshot.getChildren()) {
                            String storageKey = itemSnap.getKey();
                            String itemKey = itemSnap.child("itemId").getValue(String.class);
                            if (itemKey == null || itemKey.trim().isEmpty()) itemKey = storageKey;

                            String name = itemSnap.child("name").getValue(String.class);
                            String category = itemSnap.child("category").getValue(String.class);
                            if (category == null || category.trim().isEmpty()) category = "other";

                            Long howManyL = itemSnap.child("requiredAmount").getValue(Long.class);
                            if (howManyL == null) howManyL = itemSnap.child("howMany").getValue(Long.class);
                            int howMany = howManyL == null ? 1 : (int) Math.max(0L, howManyL);

                            Boolean boughtB = itemSnap.child("bought").getValue(Boolean.class);
                            boolean bought = boughtB != null && boughtB;
                            Boolean recurringB = itemSnap.child("isRecurring").getValue(Boolean.class);
                            if (recurringB == null) recurringB = itemSnap.child("saved").getValue(Boolean.class);
                            boolean recurring = recurringB != null && recurringB;

                            Long updatedAtMsL = itemSnap.child("updatedAtMs").getValue(Long.class);
                            long updatedAtMs = updatedAtMsL == null ? 0L : updatedAtMsL;

                            if (itemKey == null || itemKey.trim().isEmpty()) continue;
                            if (name == null) name = "";

                            out.add(new ShoppingItem(itemKey, name, category, howMany, bought, recurring, updatedAtMs));
                        }

                        callback.onSuccess(out);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onFailure(error);
                    }
                });
    }

    /**
     * Adds (or increments) the item into /shopping/<uid>/<category>/<itemKey>.
     * bought is set to the provided value.
     */
    /**
     * Writes or updates a shopping list item under
     * {@code /ShoppingListItems/<uid_itemKey>}. The {@code requiredAmount} field
     * is incremented atomically via a Firebase Transaction.
     *
     * @param user the authenticated user
     * @param item the item to upsert
     */
    public void upsertShoppingItem(@NonNull FirebaseUser user, @NonNull ShoppingItem item) {
        DatabaseReference itemRef = shoppingItemRef(user, item);
        String itemKey = item.key == null ? RtdbKeyUtil.safeKey(item.name) : item.key;
        String uid = user.getUid();

        long now = System.currentTimeMillis();

        itemRef.child("itemId").setValue(itemKey);
        itemRef.child("name").setValue(item.name);
        itemRef.child("category").setValue(item.category);
        itemRef.child("bought").setValue(item.bought);
        itemRef.child("isRecurring").setValue(item.saved);
        itemRef.child("userID").setValue(uid);
        itemRef.child("updatedAtMs").setValue(now);

        itemRef.child("requiredAmount").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Long cur = currentData.getValue(Long.class);
                long next = (cur == null ? 0L : cur) + item.howMany;
                currentData.setValue(next);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                // No-op
            }
        });
    }

    /**
     * Updates the {@code bought} flag of the given shopping list item in RTDB.
     *
     * @param user  the authenticated user
     * @param item  the item to update
     * @param bought {@code true} to mark as bought, {@code false} to unmark
     */
    public void setShoppingItemBought(
            @NonNull FirebaseUser user,
            @NonNull ShoppingItem item,
            boolean bought
    ) {
        DatabaseReference itemRef = shoppingItemRef(user, item);

        Map<String, Object> updates = new HashMap<>();
        updates.put("bought", bought);
        updates.put("updatedAtMs", System.currentTimeMillis());
        itemRef.updateChildren(updates);
    }

    /**
     * Sets the {@code requiredAmount} of the given shopping list item. If
     * {@code newAmount ≤ 0} the item node is removed entirely.
     *
     * @param user      the authenticated user
     * @param item      the item to update
     * @param newAmount the desired quantity; ≤ 0 removes the item
     */
    public void updateShoppingItemAmount(
            @NonNull FirebaseUser user,
            @NonNull ShoppingItem item,
            int newAmount
    ) {
        DatabaseReference itemRef = shoppingItemRef(user, item);

        if (newAmount <= 0) {
            itemRef.removeValue();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("requiredAmount", (long) newAmount);
        updates.put("updatedAtMs", System.currentTimeMillis());
        itemRef.updateChildren(updates);
    }

    /**
     * Permanently removes the shopping list item node from RTDB.
     *
     * @param user the authenticated user
     * @param item the item to delete
     */
    public void deleteShoppingItem(@NonNull FirebaseUser user, @NonNull ShoppingItem item) {
        shoppingItemRef(user, item).removeValue();
    }

    /**
     * Updates the {@code isRecurring} flag of the given shopping list item,
     * which controls whether the item is preserved after being marked as bought.
     *
     * @param user  the authenticated user
     * @param item  the item to update
     * @param saved {@code true} to mark as a recurring item, {@code false} otherwise
     */
    public void setShoppingItemSaved(
            @NonNull FirebaseUser user,
            @NonNull ShoppingItem item,
            boolean saved
    ) {
        DatabaseReference itemRef = shoppingItemRef(user, item);

        Map<String, Object> updates = new HashMap<>();
        updates.put("isRecurring", saved);
        updates.put("updatedAtMs", System.currentTimeMillis());
        itemRef.updateChildren(updates);
    }

    /**
     * Removes every item from the user's shopping list that has been marked as
     * bought, regardless of its recurring flag. This is the "Clear all bought"
     * action.
     *
     * @param user the authenticated user
     */
    public void clearAllBoughtShoppingItems(@NonNull FirebaseUser user) {
        Query q = root.child("ShoppingListItems")
                .orderByChild("userID")
                .equalTo(user.getUid());
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot itemSnap : snapshot.getChildren()) {
                    Boolean boughtB = itemSnap.child("bought").getValue(Boolean.class);
                    boolean bought = boughtB != null && boughtB;
                    if (!bought) continue;
                    itemSnap.getRef().removeValue();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    /**
     * Removes only non-recurring shopping items that have been bought.
     * Items with {@code isRecurring = true} are preserved so they re-appear
     * on the next shopping session. Called from {@link com.example.myfridge.fragments.ShoppingFragment#onPause}.
     *
     * @param user the authenticated user
     */
    public void clearBoughtUnsavedShoppingItems(@NonNull FirebaseUser user) {
        Query q = root.child("ShoppingListItems")
                .orderByChild("userID")
                .equalTo(user.getUid());
        q.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot itemSnap : snapshot.getChildren()) {
                    Boolean boughtB = itemSnap.child("bought").getValue(Boolean.class);
                    Boolean recurringB = itemSnap.child("isRecurring").getValue(Boolean.class);
                    if (recurringB == null) recurringB = itemSnap.child("saved").getValue(Boolean.class);
                    boolean bought = boughtB != null && boughtB;
                    boolean recurring = recurringB != null && recurringB;
                    if (bought && !recurring) {
                        itemSnap.getRef().removeValue();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // No-op
            }
        });
    }

    /**
     * Writes or updates the given item in the user's recurring-defaults list at
     * {@code /shoppingDefaults/<uid>/<itemKey>}. Recurring defaults are offered
     * in the "Pick default items" dialog.
     *
     * @param user the authenticated user
     * @param item the item to persist as a recurring default
     */
    public void upsertRecurringDefault(@NonNull FirebaseUser user, @NonNull ShoppingItem item) {
        String itemKey = item.key == null ? RtdbKeyUtil.safeKey(item.name) : item.key;
        DatabaseReference defaultRef = root.child("shoppingDefaults")
                .child(user.getUid())
                .child(itemKey);
        Map<String, Object> updates = new HashMap<>();
        updates.put("itemId", itemKey);
        updates.put("name", item.name);
        updates.put("category", item.category);
        updates.put("requiredAmount", (long) Math.max(1, item.howMany));
        updates.put("isRecurring", true);
        updates.put("userID", user.getUid());
        updates.put("updatedAtMs", System.currentTimeMillis());
        defaultRef.updateChildren(updates);
    }

    /**
     * Removes the item from the user's recurring-defaults list in RTDB.
     *
     * @param user the authenticated user
     * @param item the item whose default entry should be removed
     */
    public void deleteRecurringDefault(@NonNull FirebaseUser user, @NonNull ShoppingItem item) {
        String itemKey = item.key == null ? RtdbKeyUtil.safeKey(item.name) : item.key;
        root.child("shoppingDefaults")
                .child(user.getUid())
                .child(itemKey)
                .removeValue();
    }

    public void fetchRecurringDefaults(@NonNull FirebaseUser user, @NonNull RecurringDefaultsCallback callback) {
        root.child("shoppingDefaults")
                .child(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<ShoppingItem> out = new ArrayList<>();
                        for (DataSnapshot itemSnap : snapshot.getChildren()) {
                            String itemKey = itemSnap.child("itemId").getValue(String.class);
                            if (itemKey == null || itemKey.trim().isEmpty()) itemKey = itemSnap.getKey();
                            if (itemKey == null || itemKey.trim().isEmpty()) continue;

                            String name = itemSnap.child("name").getValue(String.class);
                            if (name == null) name = "";
                            String category = itemSnap.child("category").getValue(String.class);
                            if (category == null || category.trim().isEmpty()) category = "other";
                            Long requiredAmountL = itemSnap.child("requiredAmount").getValue(Long.class);
                            int requiredAmount = requiredAmountL == null ? 1 : (int) Math.max(1L, requiredAmountL);
                            Long updatedAtMsL = itemSnap.child("updatedAtMs").getValue(Long.class);
                            long updatedAtMs = updatedAtMsL == null ? 0L : updatedAtMsL;
                            out.add(new ShoppingItem(itemKey, name, category, requiredAmount, false, true, updatedAtMs));
                        }
                        callback.onSuccess(out);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onFailure(error);
                    }
                });
    }

    /**
     * Builds the database reference for the given shopping item under
     * {@code /ShoppingListItems/<uid_itemKey>}.
     *
     * @param user the authenticated user
     * @param item the shopping item whose reference to build
     * @return the database reference for the shopping item node
     */
    private DatabaseReference shoppingItemRef(@NonNull FirebaseUser user, @NonNull ShoppingItem item) {
        String itemKey = item.key == null ? RtdbKeyUtil.safeKey(item.name) : item.key;
        String storageKey = user.getUid() + "_" + itemKey;
        return root.child("ShoppingListItems")
                .child(storageKey);
    }

    /**
     * Parses a {@code "yyyy-MM-dd"} expiration-bucket key back to a Unix
     * timestamp in milliseconds. Returns {@code 0} for {@code null}, blank,
     * {@code "unknown"}, or unparseable strings.
     *
     * @param expirationKey the RTDB expiration-bucket key; may be {@code null}
     * @return the timestamp in milliseconds, or {@code 0} if unknown/unparseable
     */
    private static long parseExpirationKeyToMs(@Nullable String expirationKey) {
        if (expirationKey == null || expirationKey.trim().isEmpty() || "unknown".equalsIgnoreCase(expirationKey)) {
            return 0L;
        }
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(expirationKey.trim());
            return d == null ? 0L : d.getTime();
        } catch (ParseException e) {
            return 0L;
        }
    }
}

