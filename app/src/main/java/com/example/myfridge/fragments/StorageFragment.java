package com.example.myfridge.fragments;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myfridge.R;
import com.example.myfridge.StorageActivity;
import com.example.myfridge.addToStorage;
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

public class StorageFragment extends Fragment {
    private enum SortMode { RECENTLY_ADDED, A_TO_Z, Z_TO_A }
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
    private long aboutToExpireWindowMs = 2L * 24L * 60L * 60L * 1000L;

    private final ActivityResultLauncher<Intent> addToStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onAddResult);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.activity_storage, container, false);
        rtdb = new RtdbRepository();
        adapter = new ProductAdapter();
        txtCount = root.findViewById(R.id.txt_storage_count);
        etSearch = root.findViewById(R.id.et_storage_search);
        cbAboutToExpire = root.findViewById(R.id.cb_about_to_expire);
        spCategory = root.findViewById(R.id.sp_category);
        spSort = root.findViewById(R.id.sp_sort);

        RecyclerView rv = root.findViewById(R.id.rv_storage);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        adapter.setOnProductClickListener(this::showProductOptionsDialog);

        FloatingActionButton fab = root.findViewById(R.id.fab_add_to_storage);
        fab.setOnClickListener(v -> addToStorageLauncher.launch(new Intent(requireContext(), addToStorage.class)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { applyAllFilters(); }
        });
        cbAboutToExpire.setOnCheckedChangeListener((buttonView, isChecked) -> applyAllFilters());
        setupSortSpinner();
        return root;
    }

    @Override public void onResume() { super.onResume(); refresh(); }

    private void onAddResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK) return;
        Intent data = result.getData();
        if (data == null) return;
        String productJson = data.getStringExtra(StorageActivity.EXTRA_PRODUCT_JSON);
        if (productJson == null || productJson.trim().isEmpty()) return;
        try {
            JSONObject o = new JSONObject(productJson);
            Product scanned = Product.createNew(
                    o.optString("name", ""),
                    o.optString("unit", ""),
                    o.optString("category", ""),
                    o.optString("imageUri", ""),
                    o.optLong("expiresAtMs", 0L),
                    o.optString("shelfLifeRaw", "")
            );
            if (scanned.name.trim().isEmpty()) return;
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                rtdb.ensureUserProfile(user);
                rtdb.upsertInventoryIncrement(user, scanned);
            }
            refresh();
        } catch (JSONException ignored) { }
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
            @Override public void onSuccess(List<Product> products) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    allProducts.clear();
                    allProducts.addAll(products);
                    setupCategorySpinner();
                    rtdb.fetchUserPreferences(user, new RtdbRepository.UserPrefsCallback() {
                        @Override public void onSuccess(String units, int daysBeforeExpireChoice) {
                            aboutToExpireWindowMs = (long) daysBeforeExpireChoice * 24L * 60L * 60L * 1000L;
                            applyAllFilters();
                        }
                        @Override public void onFailure(com.google.firebase.database.DatabaseError error) { applyAllFilters(); }
                    });
                });
            }
            @Override public void onFailure(com.google.firebase.database.DatabaseError error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> { allProducts.clear(); setupCategorySpinner(); applyAllFilters(); });
            }
        });
    }

    private void showProductOptionsDialog(Product product) {
        String[] options = new String[]{"Change amount", "Change expiration date", "Remove"};
        new AlertDialog.Builder(requireContext()).setTitle(product.name).setItems(options, (dialog, which) -> {
            if (which == 0) showChangeAmountDialog(product);
            else if (which == 1) showChangeExpirationDateDialog(product);
            else confirmRemoveProduct(product);
        }).show();
    }

    private void showChangeAmountDialog(Product product) {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(product.amount));
        new AlertDialog.Builder(requireContext())
                .setTitle("Change amount").setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    int newAmount;
                    try { newAmount = Integer.parseInt(text); } catch (NumberFormatException e) { return; }
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) { rtdb.updateInventoryAmount(user, product, newAmount); refresh(); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void confirmRemoveProduct(Product product) {
        new AlertDialog.Builder(requireContext()).setTitle("Remove product")
                .setMessage("Remove \"" + product.name + "\" from your inventory?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) { rtdb.deleteInventoryItem(user, product); refresh(); }
                }).setNegativeButton("Cancel", null).show();
    }

    private void showChangeExpirationDateDialog(Product product) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        Calendar minCal = Calendar.getInstance();
        minCal.set(Calendar.HOUR_OF_DAY, 0); minCal.set(Calendar.MINUTE, 0); minCal.set(Calendar.SECOND, 0); minCal.set(Calendar.MILLISECOND, 0);
        minCal.add(Calendar.DAY_OF_YEAR, 1);
        long minMs = minCal.getTimeInMillis();
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(product.expiresAtMs > 0L ? product.expiresAtMs : minMs);
        initial.set(Calendar.HOUR_OF_DAY, 12); initial.set(Calendar.MINUTE, 0); initial.set(Calendar.SECOND, 0); initial.set(Calendar.MILLISECOND, 0);
        DatePickerDialog dlg = new DatePickerDialog(requireContext(), (DatePicker view, int year, int month, int dayOfMonth) -> {
            Calendar selected = Calendar.getInstance();
            selected.set(Calendar.YEAR, year); selected.set(Calendar.MONTH, month); selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            selected.set(Calendar.HOUR_OF_DAY, 12); selected.set(Calendar.MINUTE, 0); selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0);
            long selectedMs = selected.getTimeInMillis();
            if (selectedMs < minMs) { Toast.makeText(requireContext(), "Date must be at least 1 day after today.", Toast.LENGTH_SHORT).show(); return; }
            rtdb.changeInventoryExpirationDate(user, product, selectedMs); refresh();
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMinDate(minMs);
        dlg.show();
    }

    private void applyAllFilters() {
        String q = etSearch.getText() == null ? "" : etSearch.getText().toString().trim().toLowerCase();
        boolean onlyExpiringSoon = cbAboutToExpire.isChecked();
        List<Product> filtered = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Product p : allProducts) {
            if (!q.isEmpty() && !(contains(p.name, q) || contains(p.category, q) || contains(p.unit, q))) continue;
            if (!"All".equalsIgnoreCase(selectedCategory) && !equalsIgnoreCase(p.category, selectedCategory)) continue;
            if (onlyExpiringSoon) {
                if (p.expiresAtMs <= 0L) continue;
                long diff = p.expiresAtMs - now;
                if (diff < 0L || diff > aboutToExpireWindowMs) continue;
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
        for (Product p : allProducts) if (p.category != null && !p.category.trim().isEmpty()) categories.add(p.category.trim());
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new ArrayList<>(categories));
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(a);
        int idx = 0;
        for (int i = 0; i < a.getCount(); i++) if (equalsIgnoreCase(a.getItem(i), selectedCategory)) { idx = i; break; }
        spCategory.setSelection(idx);
        spCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedCategory = String.valueOf(parent.getItemAtPosition(position)); applyAllFilters();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void setupSortSpinner() {
        List<String> options = new ArrayList<>();
        options.add("Recently added"); options.add("A > Z"); options.add("Z < A");
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSort.setAdapter(a); spSort.setSelection(0);
        spSort.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) sortMode = SortMode.RECENTLY_ADDED; else if (position == 1) sortMode = SortMode.A_TO_Z; else sortMode = SortMode.Z_TO_A;
                applyAllFilters();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void applySort(List<Product> list) {
        if (sortMode == SortMode.RECENTLY_ADDED) Collections.sort(list, (a, b) -> Long.compare(b.updatedAtMs, a.updatedAtMs));
        else if (sortMode == SortMode.A_TO_Z) Collections.sort(list, Comparator.comparing(p -> safeLower(p.name)));
        else Collections.sort(list, (a, b) -> safeLower(b.name).compareTo(safeLower(a.name)));
    }
    private static boolean contains(String field, String q) { return field != null && field.toLowerCase().contains(q); }
    private static boolean equalsIgnoreCase(String a, String b) { return a != null && b != null && a.trim().equalsIgnoreCase(b.trim()); }
    private static String safeLower(String s) { return s == null ? "" : s.trim().toLowerCase(); }
}

