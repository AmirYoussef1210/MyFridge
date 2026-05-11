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
import android.content.DialogInterface;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
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

/**
 * Fragment that displays the user's food inventory (fridge/storage).
 * <p>
 * Products can be added via the {@link addToStorage} screen (barcode scan or manual entry).
 * The list supports filtering by search text, category, and "about to expire" status,
 * and can be sorted by recently added, A–Z, or Z–A.
 * Each product row opens an action dialog for changing amount, expiration date, or removing it.
 * </p>
 */
public class StorageFragment extends Fragment {
    private enum SortMode { RECENTLY_ADDED, A_TO_Z, Z_TO_A }
    private RtdbRepository rtdb;
    private ProductAdapter adapter;
    private TextView txtCount;
    private ProgressBar progressLoading;
    private TextInputEditText etSearch;
    private CheckBox cbAboutToExpire;
    private Spinner spCategory;
    private Spinner spSort;
    private final List<Product> allProducts = new ArrayList<>();
    private String selectedCategory = "All";
    private SortMode sortMode = SortMode.RECENTLY_ADDED;
    // Default expiry window: 2 days. Updated from user preferences after each refresh.
    private long aboutToExpireWindowMs = 2L * 24L * 60L * 60L * 1000L;

    // Registered before the fragment is attached; receives the result from the addToStorage screen.
    private final ActivityResultLauncher<Intent> addToStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), this::onAddResult);

    /**
     * Inflates the storage layout, sets up the RecyclerView, FAB, search field,
     * "about to expire" checkbox, and sort spinner. Does not load data yet — that
     * happens in {@link #onResume()}.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.activity_storage, container, false);
        rtdb = new RtdbRepository();
        adapter = new ProductAdapter();
        txtCount = root.findViewById(R.id.txt_storage_count);
        progressLoading = root.findViewById(R.id.progress_loading);
        etSearch = root.findViewById(R.id.et_storage_search);
        cbAboutToExpire = root.findViewById(R.id.cb_about_to_expire);
        spCategory = root.findViewById(R.id.sp_category);
        spSort = root.findViewById(R.id.sp_sort);

        RecyclerView rv = root.findViewById(R.id.rv_storage);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);
        adapter.setOnProductClickListener(this::showProductOptionsDialog);

        FloatingActionButton fab = root.findViewById(R.id.fab_add_to_storage);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addToStorageLauncher.launch(new Intent(requireContext(), addToStorage.class));
            }
        });

        // Re-filter the list every time the user types in the search box
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { applyAllFilters(); }
        });
        cbAboutToExpire.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                applyAllFilters();
            }
        });
        setupSortSpinner();
        return root;
    }

    /**
     * Refreshes the product list every time the fragment becomes visible
     * (e.g. when the user comes back from the add-product screen).
     */
    @Override public void onResume() { super.onResume(); refresh(); }

    /**
     * Called when the user returns from the {@link addToStorage} screen.
     * If the result is OK, parses the product JSON from the intent, saves it
     * to Firebase, and refreshes the list.
     * <p>
     * The product comes back as a JSON string (not a Parcelable) to avoid
     * dependency on a specific Product version across activities.
     * </p>
     *
     * @param result the activity result containing the product JSON
     */
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
                // upsertInventoryIncrement adds 1 to the quantity if the product already exists
                rtdb.upsertInventoryIncrement(user, scanned);
            }
            refresh();
        } catch (JSONException ignored) { }
    }

    /**
     * Loads all inventory products from Firebase for the current user, then fetches
     * the user's preferences to update the expiry window, and finally applies all
     * active filters to display the correct subset.
     * <p>
     * Two sequential network calls happen here: first inventory, then preferences.
     * The preferences call is nested inside the inventory success callback so the
     * expiry window is always up-to-date before filtering.
     * </p>
     */
    private void refresh() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            allProducts.clear();
            setupCategorySpinner();
            applyAllFilters();
            return;
        }
        progressLoading.setVisibility(View.VISIBLE);
        rtdb.fetchAllInventory(user, new RtdbRepository.ProductsCallback() {
            @Override public void onSuccess(List<Product> products) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressLoading.setVisibility(View.GONE);
                        allProducts.clear();
                        allProducts.addAll(products);
                        setupCategorySpinner();
                        // Fetch user preferences to get the correct "days before expire" window,
                        // then apply filters so the expiry checkbox uses the right threshold.
                        rtdb.fetchUserPreferences(user, new RtdbRepository.UserPrefsCallback() {
                            @Override public void onSuccess(String units, int daysBeforeExpireChoice) {
                                aboutToExpireWindowMs = (long) daysBeforeExpireChoice * 24L * 60L * 60L * 1000L;
                                applyAllFilters();
                            }
                            @Override public void onFailure(com.google.firebase.database.DatabaseError error) { applyAllFilters(); }
                        });
                    }
                });
            }
            @Override public void onFailure(com.google.firebase.database.DatabaseError error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressLoading.setVisibility(View.GONE);
                        allProducts.clear();
                        setupCategorySpinner();
                        applyAllFilters();
                    }
                });
            }
        });
    }

    /**
     * Shows an action dialog when the user taps a product row.
     * Options: change amount, change expiration date, or remove the product.
     *
     * @param product the product that was tapped
     */
    private void showProductOptionsDialog(Product product) {
        String[] options = new String[]{"Change amount", "Change expiration date", "Remove"};
        new AlertDialog.Builder(requireContext()).setTitle(product.name).setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) showChangeAmountDialog(product);
                else if (which == 1) showChangeExpirationDateDialog(product);
                else confirmRemoveProduct(product);
            }
        }).show();
    }

    /**
     * Opens a dialog with a number input so the user can update the quantity of a product.
     * The current amount is shown as a hint. Saves to Firebase and refreshes on confirm.
     *
     * @param product the product whose amount is being changed
     */
    private void showChangeAmountDialog(Product product) {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(product.amount));
        new AlertDialog.Builder(requireContext())
                .setTitle("Change amount").setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = input.getText() == null ? "" : input.getText().toString().trim();
                        int newAmount;
                        try { newAmount = Integer.parseInt(text); } catch (NumberFormatException e) { return; }
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) { rtdb.updateInventoryAmount(user, product, newAmount); refresh(); }
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    /**
     * Asks the user to confirm before permanently removing a product from the inventory.
     *
     * @param product the product to remove
     */
    private void confirmRemoveProduct(Product product) {
        new AlertDialog.Builder(requireContext()).setTitle("Remove product")
                .setMessage("Remove \"" + product.name + "\" from your inventory?")
                .setPositiveButton("Remove", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                        if (user != null) { rtdb.deleteInventoryItem(user, product); refresh(); }
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    /**
     * Opens a DatePickerDialog so the user can pick a new expiration date for a product.
     * <p>
     * The minimum selectable date is tomorrow (today + 1 day). The picker opens pre-filled
     * to the product's current expiry date, or tomorrow if none is set. The time is fixed
     * to noon (12:00) to avoid timezone edge cases when comparing dates.
     * </p>
     *
     * @param product the product whose expiration date is being changed
     */
    private void showChangeExpirationDateDialog(Product product) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        // Calculate tomorrow at midnight as the earliest allowed date
        Calendar minCal = Calendar.getInstance();
        minCal.set(Calendar.HOUR_OF_DAY, 0); minCal.set(Calendar.MINUTE, 0); minCal.set(Calendar.SECOND, 0); minCal.set(Calendar.MILLISECOND, 0);
        minCal.add(Calendar.DAY_OF_YEAR, 1);
        long minMs = minCal.getTimeInMillis();
        // Pre-fill the picker with the product's current expiry date, or tomorrow if not set
        Calendar initial = Calendar.getInstance();
        initial.setTimeInMillis(product.expiresAtMs > 0L ? product.expiresAtMs : minMs);
        initial.set(Calendar.HOUR_OF_DAY, 12); initial.set(Calendar.MINUTE, 0); initial.set(Calendar.SECOND, 0); initial.set(Calendar.MILLISECOND, 0);
        DatePickerDialog dlg = new DatePickerDialog(requireContext(), new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                Calendar selected = Calendar.getInstance();
                selected.set(Calendar.YEAR, year); selected.set(Calendar.MONTH, month); selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                selected.set(Calendar.HOUR_OF_DAY, 12); selected.set(Calendar.MINUTE, 0); selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0);
                long selectedMs = selected.getTimeInMillis();
                if (selectedMs < minMs) { Toast.makeText(requireContext(), "Date must be at least 1 day after today.", Toast.LENGTH_SHORT).show(); return; }
                rtdb.changeInventoryExpirationDate(user, product, selectedMs); refresh();
            }
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        dlg.getDatePicker().setMinDate(minMs);
        dlg.show();
    }

    /**
     * Runs all active filters on {@link #allProducts} and submits the result to the adapter.
     * <p>
     * Filters applied in order:
     * 1. Search text — matches product name, category, or unit (case-insensitive).
     * 2. Category spinner — only shows products in the selected category.
     * 3. "About to expire" checkbox — only shows products expiring within {@link #aboutToExpireWindowMs}.
     *    Products with no expiry date set are excluded when this filter is on.
     * </p>
     * Also updates the count label ("X / Y") and calls {@link #applySort} before submitting.
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
            // 1. Text search across name, category, and unit
            if (!q.isEmpty() && !(contains(p.name, q) || contains(p.category, q) || contains(p.unit, q))) continue;
            // 2. Category filter
            if (!"All".equalsIgnoreCase(selectedCategory) && !equalsIgnoreCase(p.category, selectedCategory)) continue;
            // 3. Expiry filter: compare calendar days so "tomorrow" is always 1 day away
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
     * Builds the category spinner from the distinct categories present in {@link #allProducts},
     * always starting with "All". Tries to preserve the previously selected category
     * after a refresh so the user's selection is not reset.
     */
    private void setupCategorySpinner() {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add("All");
        for (Product p : allProducts) if (p.category != null && !p.category.trim().isEmpty()) categories.add(p.category.trim());
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new ArrayList<>(categories));
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(a);
        // Restore the previously selected category if it still exists in the new list
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

    /**
     * Sets up the sort-order spinner with the three available options and wires it to
     * update {@link #sortMode} and re-filter when the selection changes.
     */
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

    /**
     * Sorts the given list in-place according to the current {@link #sortMode}.
     * RECENTLY_ADDED sorts by {@code updatedAtMs} descending (newest first).
     * A_TO_Z and Z_TO_A sort alphabetically by product name (case-insensitive).
     *
     * @param list the filtered product list to sort
     */
    private void applySort(List<Product> list) {
        if (sortMode == SortMode.RECENTLY_ADDED) Collections.sort(list, new Comparator<Product>() {
            @Override
            public int compare(Product a, Product b) { return Long.compare(b.updatedAtMs, a.updatedAtMs); }
        });
        else if (sortMode == SortMode.A_TO_Z) Collections.sort(list, new Comparator<Product>() {
            @Override
            public int compare(Product a, Product b) { return safeLower(a.name).compareTo(safeLower(b.name)); }
        });
        else Collections.sort(list, new Comparator<Product>() {
            @Override
            public int compare(Product a, Product b) { return safeLower(b.name).compareTo(safeLower(a.name)); }
        });
    }

    /** Returns true if {@code field} contains {@code q} (case-insensitive). Null-safe. */
    private static boolean contains(String field, String q) { return field != null && field.toLowerCase().contains(q); }

    /** Null-safe, trim-safe case-insensitive equality check. */
    private static boolean equalsIgnoreCase(String a, String b) { return a != null && b != null && a.trim().equalsIgnoreCase(b.trim()); }

    /** Returns the trimmed lowercase version of {@code s}, or an empty string if null. */
    private static String safeLower(String s) { return s == null ? "" : s.trim().toLowerCase(); }
}
