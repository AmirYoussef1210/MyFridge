package com.example.myfridge;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.text.InputType;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.content.DialogInterface;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myfridge.rtdb.RtdbRepository;
import com.example.myfridge.storage.Product;
import com.example.myfridge.storage.ProductAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Standalone inventory management screen (Activity variant).
 * <p>
 * Displays the user's fridge inventory as a {@link androidx.recyclerview.widget.RecyclerView}
 * driven by {@link com.example.myfridge.storage.ProductAdapter}. Supports:
 * <ul>
 *   <li>Free-text search across name, category and unit</li>
 *   <li>Category filtering via a {@link android.widget.Spinner}</li>
 *   <li>Sorting by recently-added, A→Z and Z→A</li>
 *   <li>"About to expire" checkbox filter using the user's configured expiry window</li>
 *   <li>Adding products via {@link addToStorage} (camera or manual)</li>
 *   <li>Editing amount, expiry date, or removing a product via a dialog</li>
 * </ul>
 * </p>
 */
public class StorageActivity extends AppCompatActivity {
    /** Intent extra key for the product JSON string returned by {@link addToStorage}. */
    public static final String EXTRA_PRODUCT_JSON = "extra_product_json";

    private enum SortMode {
        RECENTLY_ADDED,
        A_TO_Z,
        Z_TO_A
    }

    private RtdbRepository rtdb;
    private ProductAdapter adapter;
    private TextView txtCount;
    private TextInputEditText etSearch;
    private CheckBox cbAboutToExpire;
    private Spinner spCategory;
    private Spinner spSort;

    private NetworkChangeReceiver networkReceiver;
    private final List<Product> allProducts = new ArrayList<>();
    private String selectedCategory = "All";
    private SortMode sortMode = SortMode.RECENTLY_ADDED;
    /** From RTDB users/&lt;uid&gt;/daysBeforeExpireChoice (default 2 days). */
    private long aboutToExpireWindowMs = 2L * 24L * 60L * 60L * 1000L;

    private final ActivityResultLauncher<Intent> addToStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onAddResult);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage);

        rtdb = new RtdbRepository();
        adapter = new ProductAdapter();

        txtCount = findViewById(R.id.txt_storage_count);
        etSearch = findViewById(R.id.et_storage_search);
        cbAboutToExpire = findViewById(R.id.cb_about_to_expire);
        spCategory = findViewById(R.id.sp_category);
        spSort = findViewById(R.id.sp_sort);

        RecyclerView rv = findViewById(R.id.rv_storage);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        adapter.setOnProductClickListener(new ProductAdapter.OnProductClickListener() {
            @Override
            public void onClick(Product product) {
                showProductOptionsDialog(product);
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab_add_to_storage);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addToStorageLauncher.launch(new Intent(StorageActivity.this, addToStorage.class));
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                applyAllFilters();
            }
        });

        cbAboutToExpire.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applyAllFilters();
            }
        });

        setupSortSpinner();
    }

    /**
     * Registers the {@link NetworkChangeReceiver} for connectivity warnings and
     * refreshes the inventory list from RTDB so data is always current when the
     * screen returns to the foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();
        networkReceiver = new NetworkChangeReceiver(this);
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        refresh();
    }

    /**
     * Unregisters the {@link NetworkChangeReceiver} to prevent memory leaks
     * when the activity moves to the background.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
        }
    }

    /**
     * Handles the result returned by {@link addToStorage}. Parses the product
     * JSON from the intent extra, creates a {@link com.example.myfridge.storage.Product}
     * via {@link com.example.myfridge.storage.Product#createNew} and upserts it
     * to the RTDB inventory, then refreshes the list.
     *
     * @param result the activity result from the {@link addToStorage} launcher
     */
    private void onAddResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK) {
            return;
        }
        Intent data = result.getData();
        if (data == null) return;

        String productJson = data.getStringExtra(EXTRA_PRODUCT_JSON);
        if (productJson == null || productJson.trim().isEmpty()) return;

        try {
            JSONObject o = new JSONObject(productJson);
            String name = o.optString("name", "");
            String unit = o.optString("unit", "");
            String category = o.optString("category", "");
            String imageUri = o.optString("imageUri", "");
            long expiresAtMs = o.optLong("expiresAtMs", 0L);
            String shelfLifeRaw = o.optString("shelfLifeRaw", "");

            if (name.trim().isEmpty()) return;

            Product scanned = Product.createNew(name, unit, category, imageUri, expiresAtMs, shelfLifeRaw);

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                rtdb.ensureUserProfile(user);
                rtdb.upsertInventoryIncrement(user, scanned);
            }

            refresh();
        } catch (JSONException ignored) {
        }
    }

    /**
     * Fetches the full inventory and the user's expiry-window preference from
     * RTDB, then calls {@link #applyAllFilters} to re-render the list.
     * Clears the list gracefully if there is no authenticated user.
     */
    private void refresh() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            allProducts.clear();
            setupCategorySpinner();
            applyAllFilters();
            return;
        }

        rtdb.fetchAllInventory(user, new RtdbRepository.ProductsCallback() {
            @Override
            public void onSuccess(List<Product> products) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        allProducts.clear();
                        allProducts.addAll(products);
                        setupCategorySpinner();
                        rtdb.fetchUserPreferences(user, new RtdbRepository.UserPrefsCallback() {
                            @Override
                            public void onSuccess(String units, int daysBeforeExpireChoice) {
                                aboutToExpireWindowMs = (long) daysBeforeExpireChoice * 24L * 60L * 60L * 1000L;
                                applyAllFilters();
                            }

                            @Override
                            public void onFailure(com.google.firebase.database.DatabaseError error) {
                                aboutToExpireWindowMs = 2L * 24L * 60L * 60L * 1000L;
                                applyAllFilters();
                            }
                        });
                    }
                });
            }

            @Override
            public void onFailure(com.google.firebase.database.DatabaseError error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        allProducts.clear();
                        setupCategorySpinner();
                        applyAllFilters();
                    }
                });
            }
        });
    }

    /**
     * Shows an options dialog for the tapped product with actions:
     * "Change amount", "Change expiration date", and "Remove".
     *
     * @param product the product the user tapped
     */
    private void showProductOptionsDialog(Product product) {
        String[] options = new String[]{"Change amount", "Change expiration date", "Remove"};

        new AlertDialog.Builder(this)
                .setTitle(product.name)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            showChangeAmountDialog(product);
                        } else if (which == 1) {
                            showChangeExpirationDateDialog(product);
                        } else if (which == 2) {
                            confirmRemoveProduct(product);
                        }
                    }
                })
                .show();
    }

    /**
     * Shows a numeric-input dialog to update the amount of a product.
     * If the new amount is ≤ 0 the product node is removed from RTDB.
     *
     * @param product the product whose amount will be changed
     */
    private void showChangeAmountDialog(Product product) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(product.amount));

        new AlertDialog.Builder(this)
                .setTitle("Change amount")
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText() == null ? "" : input.getText().toString().trim();
                        int newAmount;
                        try {
                            newAmount = Integer.parseInt(text);
                        } catch (NumberFormatException e) {
                            return;
                        }
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) {
                            rtdb.updateInventoryAmount(user, product, newAmount);
                            refresh();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Shows a confirmation dialog before permanently deleting a product from
     * the RTDB inventory.
     *
     * @param product the product to remove
     */
    private void confirmRemoveProduct(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("Remove product")
                .setMessage("Remove \"" + product.name + "\" from your inventory?")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) {
                            rtdb.deleteInventoryItem(user, product);
                            refresh();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Opens a {@link android.app.DatePickerDialog} seeded to the product's
     * current expiry date. Enforces a minimum date of tomorrow and delegates
     * to {@link com.example.myfridge.rtdb.RtdbRepository#changeInventoryExpirationDate}
     * on confirmation.
     *
     * @param product the product whose expiry date will be changed
     */
    private void showChangeExpirationDateDialog(Product product) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Calendar now = Calendar.getInstance();
        Calendar minCal = Calendar.getInstance();
        minCal.setTimeInMillis(System.currentTimeMillis());
        minCal.set(Calendar.HOUR_OF_DAY, 0);
        minCal.set(Calendar.MINUTE, 0);
        minCal.set(Calendar.SECOND, 0);
        minCal.set(Calendar.MILLISECOND, 0);
        minCal.add(Calendar.DAY_OF_YEAR, 1); // at least 1 day after today
        long minMs = minCal.getTimeInMillis();

        Calendar initial = Calendar.getInstance();
        if (product.expiresAtMs > 0L) {
            initial.setTimeInMillis(product.expiresAtMs);
        } else {
            initial.setTimeInMillis(minMs);
        }
        // Use midday to avoid DST boundary issues when converting ms <-> yyyy-MM-dd.
        initial.set(Calendar.HOUR_OF_DAY, 12);
        initial.set(Calendar.MINUTE, 0);
        initial.set(Calendar.SECOND, 0);
        initial.set(Calendar.MILLISECOND, 0);

        DatePickerDialog dlg = new DatePickerDialog(
                this,
                new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        Calendar selected = Calendar.getInstance();
                        selected.set(Calendar.YEAR, year);
                        selected.set(Calendar.MONTH, month);
                        selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                        selected.set(Calendar.HOUR_OF_DAY, 12);
                        selected.set(Calendar.MINUTE, 0);
                        selected.set(Calendar.SECOND, 0);
                        selected.set(Calendar.MILLISECOND, 0);

                        long selectedMs = selected.getTimeInMillis();
                        if (selectedMs < minMs) {
                            Toast.makeText(
                                    StorageActivity.this,
                                    "Date must be at least 1 day after today.",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        rtdb.changeInventoryExpirationDate(user, product, selectedMs);
                        refresh();
                    }
                },
                initial.get(Calendar.YEAR),
                initial.get(Calendar.MONTH),
                initial.get(Calendar.DAY_OF_MONTH)
        );
        dlg.getDatePicker().setMinDate(minMs);
        dlg.show();
    }

    /**
     * Filters {@link #allProducts} using the current search query, selected
     * category, "about to expire" checkbox state, and sort mode, then submits
     * the result to the adapter and updates the count label.
     */
    private void applyAllFilters() {
        String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim().toLowerCase();
        boolean onlyExpiringSoon = cbAboutToExpire.isChecked();

        List<Product> filtered = new ArrayList<>();
        Calendar todayCal = Calendar.getInstance();
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        todayCal.set(Calendar.MILLISECOND, 0);
        long todayMidnight = todayCal.getTimeInMillis();
        long daysWindow = aboutToExpireWindowMs / 86400000L;

        for (Product p : allProducts) {
            if (!q.isEmpty() && !matchesSearch(p, q)) continue;
            if (!"All".equalsIgnoreCase(selectedCategory) && !equalsIgnoreCase(p.category, selectedCategory)) continue;

            if (onlyExpiringSoon) {
                if (p.expiresAtMs <= 0L) continue;
                Calendar expCal = Calendar.getInstance();
                expCal.setTimeInMillis(p.expiresAtMs);
                expCal.set(Calendar.HOUR_OF_DAY, 0);
                expCal.set(Calendar.MINUTE, 0);
                expCal.set(Calendar.SECOND, 0);
                expCal.set(Calendar.MILLISECOND, 0);
                long daysUntilExpiry = (expCal.getTimeInMillis() - todayMidnight) / 86400000L;
                if (daysUntilExpiry < 0 || daysUntilExpiry > daysWindow) continue;
            }

            filtered.add(p);
        }

        applySort(filtered);
        adapter.submit(filtered);
        txtCount.setText(filtered.size() + " / " + allProducts.size());
    }

    /**
     * Builds the category spinner from the distinct categories present in
     * {@link #allProducts} (prepended with "All") and restores the previously
     * selected category if it still exists.
     */
    private void setupCategorySpinner() {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add("All");
        for (Product p : allProducts) {
            if (p.category != null && !p.category.trim().isEmpty()) {
                categories.add(p.category.trim());
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(categories));
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(adapter);

        // Keep selection if possible.
        int idx = 0;
        for (int i = 0; i < adapter.getCount(); i++) {
            if (equalsIgnoreCase(adapter.getItem(i), selectedCategory)) {
                idx = i;
                break;
            }
        }
        spCategory.setSelection(idx);

        spCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedCategory = String.valueOf(parent.getItemAtPosition(position));
                applyAllFilters();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    /**
     * Initialises the sort spinner with the three sort options
     * (recently-added, A→Z, Z→A) and wires its selection listener to
     * {@link #applyAllFilters}.
     */
    private void setupSortSpinner() {
        List<String> options = new ArrayList<>();
        options.add("Recently added");
        options.add("A > Z");
        options.add("Z < A");

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSort.setAdapter(adapter);
        spSort.setSelection(0);

        spSort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) sortMode = SortMode.RECENTLY_ADDED;
                else if (position == 1) sortMode = SortMode.A_TO_Z;
                else sortMode = SortMode.Z_TO_A;
                applyAllFilters();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    /**
     * Sorts {@code list} in-place according to the current {@link SortMode}.
     *
     * @param list the list of products to sort; may be {@code null} (no-op)
     */
    private void applySort(List<Product> list) {
        if (list == null) return;
        if (sortMode == SortMode.RECENTLY_ADDED) {
            Collections.sort(list, new Comparator<Product>() {
                @Override
                public int compare(Product a, Product b) {
                    return Long.compare(b.updatedAtMs, a.updatedAtMs);
                }
            });
        } else if (sortMode == SortMode.A_TO_Z) {
            Collections.sort(list, new Comparator<Product>() {
                @Override
                public int compare(Product a, Product b) {
                    return safeLower(a.name).compareTo(safeLower(b.name));
                }
            });
        } else if (sortMode == SortMode.Z_TO_A) {
            Collections.sort(list, new Comparator<Product>() {
                @Override
                public int compare(Product a, Product b) {
                    return safeLower(b.name).compareTo(safeLower(a.name));
                }
            });
        }
    }

    /**
     * Returns {@code true} if the product's name, category or unit contains
     * the lower-case query string {@code q}.
     *
     * @param p the product to test
     * @param q lower-cased search query
     * @return {@code true} if any field matches
     */
    private static boolean matchesSearch(Product p, String q) {
        return contains(p.name, q) || contains(p.category, q) || contains(p.unit, q);
    }

    /**
     * Null-safe substring check (case-insensitive, as {@code q} is pre-lowercased).
     *
     * @param field the field to search in; may be {@code null}
     * @param q     the lower-cased query to look for
     * @return {@code true} if {@code field} is non-null and contains {@code q}
     */
    private static boolean contains(String field, String q) {
        return field != null && field.toLowerCase().contains(q);
    }

    /**
     * Null-safe, trimming case-insensitive equality check.
     *
     * @param a first string; may be {@code null}
     * @param b second string; may be {@code null}
     * @return {@code true} if both are non-null and equal ignoring case after trimming
     */
    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    /**
     * Returns a trimmed, lower-cased version of {@code s}, or an empty string
     * if {@code s} is {@code null}.
     *
     * @param s the string to normalise; may be {@code null}
     * @return a non-null lower-cased, trimmed string
     */
    private static String safeLower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}

