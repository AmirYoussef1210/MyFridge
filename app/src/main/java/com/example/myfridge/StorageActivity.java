package com.example.myfridge;

import android.app.Activity;
import android.content.Intent;
import android.text.InputType;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public class StorageActivity extends AppCompatActivity {
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

    private final List<Product> allProducts = new ArrayList<>();
    private String selectedCategory = "All";
    private SortMode sortMode = SortMode.RECENTLY_ADDED;

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

        adapter.setOnProductLongClickListener(product -> showProductOptionsDialog(product));

        FloatingActionButton fab = findViewById(R.id.fab_add_to_storage);
        fab.setOnClickListener(v -> addToStorageLauncher.launch(new Intent(this, addToStorage.class)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                applyAllFilters();
            }
        });

        cbAboutToExpire.setOnCheckedChangeListener((buttonView, isChecked) -> applyAllFilters());

        setupSortSpinner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

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
                runOnUiThread(() -> {
                    allProducts.clear();
                    allProducts.addAll(products);
                    setupCategorySpinner();
                    applyAllFilters();
                });
            }

            @Override
            public void onFailure(com.google.firebase.database.DatabaseError error) {
                runOnUiThread(() -> {
                    allProducts.clear();
                    setupCategorySpinner();
                    applyAllFilters();
                });
            }
        });
    }

    private void showProductOptionsDialog(Product product) {
        String[] options = new String[]{"Change amount", "Remove"};

        new AlertDialog.Builder(this)
                .setTitle(product.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showChangeAmountDialog(product);
                    } else if (which == 1) {
                        confirmRemoveProduct(product);
                    }
                })
                .show();
    }

    private void showChangeAmountDialog(Product product) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(product.amount));

        new AlertDialog.Builder(this)
                .setTitle("Change amount")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
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
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemoveProduct(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("Remove product")
                .setMessage("Remove \"" + product.name + "\" from your inventory?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        rtdb.deleteInventoryItem(user, product);
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyAllFilters() {
        String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim().toLowerCase();
        boolean onlyExpiringSoon = cbAboutToExpire.isChecked();

        List<Product> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();
        long twoDaysMs = 2L * 24L * 60L * 60L * 1000L;

        for (Product p : allProducts) {
            if (!q.isEmpty() && !matchesSearch(p, q)) continue;
            if (!"All".equalsIgnoreCase(selectedCategory) && !equalsIgnoreCase(p.category, selectedCategory)) continue;

            if (onlyExpiringSoon) {
                if (p.expiresAtMs <= 0L) continue;
                long diff = p.expiresAtMs - now;
                if (diff < 0L || diff > twoDaysMs) continue;
            }

            filtered.add(p);
        }

        applySort(filtered);
        adapter.submit(filtered);
        txtCount.setText(filtered.size() + " / " + allProducts.size());
    }

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

    private void applySort(List<Product> list) {
        if (list == null) return;
        if (sortMode == SortMode.RECENTLY_ADDED) {
            Collections.sort(list, (a, b) -> Long.compare(b.updatedAtMs, a.updatedAtMs));
        } else if (sortMode == SortMode.A_TO_Z) {
            Collections.sort(list, Comparator.comparing(p -> safeLower(p.name)));
        } else if (sortMode == SortMode.Z_TO_A) {
            Collections.sort(list, (a, b) -> safeLower(b.name).compareTo(safeLower(a.name)));
        }
    }

    private static boolean matchesSearch(Product p, String q) {
        return contains(p.name, q) || contains(p.category, q) || contains(p.unit, q);
    }

    private static boolean contains(String field, String q) {
        return field != null && field.toLowerCase().contains(q);
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}

