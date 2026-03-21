package com.example.myfridge.rtdb;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.myfridge.storage.Product;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
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

public class RtdbRepository {

    private final DatabaseReference root;

    public RtdbRepository() {
        this.root = FirebaseDatabase.getInstance().getReference();
    }

    public interface ProductsCallback {
        void onSuccess(@NonNull List<Product> products);
        void onFailure(@NonNull DatabaseError error);
    }

    public interface UserPrefsCallback {
        void onSuccess(@NonNull String units, int daysBeforeExpireChoice);

        void onFailure(@NonNull DatabaseError error);
    }

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

    public void updateInventoryAmount(@NonNull FirebaseUser user, @NonNull Product p, int newAmount) {
        DatabaseReference ref = productRef(user, p).child("amount");
        if (newAmount <= 0) {
            // If amount goes to 0, remove the node.
            productRef(user, p).removeValue();
        } else {
            ref.setValue(newAmount);
        }
    }

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

