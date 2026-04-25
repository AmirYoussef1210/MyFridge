package com.example.myfridge.fragments;

import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myfridge.R;
import com.example.myfridge.rtdb.RtdbKeyUtil;
import com.example.myfridge.rtdb.RtdbRepository;
import com.example.myfridge.shopping.ShoppingAdapter;
import com.example.myfridge.shopping.ShoppingItem;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fragment that displays and manages the user's shopping list.
 * <p>
 * The user can add custom items, pick from a list of common defaults,
 * mark items as bought, and clear bought items. Items marked as "saved"
 * are kept in the list even after being marked as bought, and are also
 * stored in Firebase as recurring defaults so they reappear next time.
 * </p>
 */
public class ShoppingFragment extends Fragment {
    private RtdbRepository rtdb;
    private ShoppingAdapter adapter;
    private TextView tvEmpty;
    private ProgressBar progressLoading;
    private EditText etName;
    private EditText etHowMany;
    private androidx.appcompat.widget.AppCompatCheckBox cbKeepWhenBought;
    private View layoutClearBought;
    private @Nullable FirebaseUser user;

    // Built-in default items shown in the "Pick defaults" dialog before the user's own recurring items.
    private final List<ShoppingItem> baseDefaultOptions = Arrays.asList(
            new ShoppingItem("milk", "Milk", "dairy", 1, false, false, 0L),
            new ShoppingItem("yogurt", "Yogurt", "dairy", 1, false, false, 0L),
            new ShoppingItem("butter", "Butter", "dairy", 1, false, false, 0L),
            new ShoppingItem("cheese", "Cheese", "dairy", 1, false, false, 0L),
            new ShoppingItem("eggs", "Eggs", "dairy", 1, false, false, 0L),
            new ShoppingItem("apples", "Apples", "produce", 1, false, false, 0L),
            new ShoppingItem("bananas", "Bananas", "produce", 1, false, false, 0L),
            new ShoppingItem("lettuce", "Lettuce", "produce", 1, false, false, 0L),
            new ShoppingItem("tomatoes", "Tomatoes", "produce", 1, false, false, 0L),
            new ShoppingItem("onions", "Onions", "produce", 1, false, false, 0L),
            new ShoppingItem("carrots", "Carrots", "produce", 1, false, false, 0L)
    );

    /**
     * Inflates the shopping list layout, sets up the RecyclerView and its adapter,
     * wires the Add / Defaults / Clear-bought buttons, and registers the adapter
     * listener that reacts when the user checks/unchecks an item or taps on it.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_shopping, container, false);
        rtdb = new RtdbRepository();
        adapter = new ShoppingAdapter();
        tvEmpty = root.findViewById(R.id.tv_shopping_empty);
        progressLoading = root.findViewById(R.id.progress_loading);
        etName = root.findViewById(R.id.et_shopping_name);
        etHowMany = root.findViewById(R.id.et_shopping_how_many);
        cbKeepWhenBought = root.findViewById(R.id.cb_keep_when_bought);
        RecyclerView rv = root.findViewById(R.id.rv_shopping);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        // When the user checks/unchecks the bought checkbox on a row, save the change to Firebase
        // and show or hide the "Clear bought" bar at the bottom.
        adapter.setOnShoppingItemChangeListener(new ShoppingAdapter.OnShoppingItemChangeListener() {
            @Override public void onBoughtChanged(@NonNull ShoppingItem item, boolean bought) {
                FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                if (u != null) rtdb.setShoppingItemBought(u, item, bought);
                layoutClearBought.setVisibility(adapter.hasBoughtItems() ? View.VISIBLE : View.GONE);
            }
            @Override public void onItemClicked(@NonNull ShoppingItem item) { showItemActions(item); }
        });

        layoutClearBought = root.findViewById(R.id.layout_clear_bought);
        root.findViewById(R.id.btn_shopping_add).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addProductToShopping();
            }
        });
        root.findViewById(R.id.btn_shopping_defaults).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDefaultPickerDialog();
            }
        });
        root.findViewById(R.id.btn_clear_bought).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClearBought();
            }
        });
        user = FirebaseAuth.getInstance().getCurrentUser();
        return root;
    }

    /**
     * Reloads the shopping list from Firebase every time the fragment becomes visible.
     */
    @Override public void onResume() { super.onResume(); fetchAndRender(); }

    /**
     * When the user leaves this screen, automatically removes any items that were
     * marked as bought but were NOT set as "saved/recurring" — so they don't pile up
     * in Firebase.
     */
    @Override
    public void onPause() {
        super.onPause();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) rtdb.clearBoughtUnsavedShoppingItems(u);
    }

    /**
     * Fetches the full shopping list from Firebase and updates the UI.
     * Shows the spinner while loading, hides the "empty" label if items exist,
     * and shows or hides the "Clear bought" bar depending on whether any item is checked.
     */
    private void fetchAndRender() {
        if (user == null) {
            if (isAdded()) Toast.makeText(requireContext(), "Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }
        progressLoading.setVisibility(View.VISIBLE);
        rtdb.fetchShoppingList(user, new RtdbRepository.ShoppingItemsCallback() {
            @Override public void onSuccess(List<ShoppingItem> items) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressLoading.setVisibility(View.GONE);
                        adapter.submit(items);
                        boolean empty = items == null || items.isEmpty();
                        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                        // Check if any item is already bought so we know whether to show the clear bar
                        boolean hasBought = false;
                        if (items != null) for (ShoppingItem i : items) if (i.bought) { hasBought = true; break; }
                        layoutClearBought.setVisibility(hasBought ? View.VISIBLE : View.GONE);
                    }
                });
            }
            @Override public void onFailure(com.google.firebase.database.DatabaseError error) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressLoading.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Failed to load shopping list.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * Reads the name and quantity the user typed, validates them, and adds the item
     * to the shopping list in Firebase.
     * <p>
     * Special case: if the item key already exists in the list but the user ticked
     * "Keep when bought" and the existing item isn't saved yet, only the saved flag
     * is updated instead of showing a duplicate error.
     * </p>
     */
    private void addProductToShopping() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        String name = etName.getText() == null ? "" : etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) { Toast.makeText(requireContext(), "Enter product name.", Toast.LENGTH_SHORT).show(); return; }
        String qtyRaw = etHowMany.getText() == null ? "" : etHowMany.getText().toString().trim();
        int howMany = 1;
        if (!TextUtils.isEmpty(qtyRaw)) {
            try { howMany = Integer.parseInt(qtyRaw); } catch (NumberFormatException ignored) { howMany = 1; }
        }
        howMany = Math.max(1, howMany);
        boolean saved = cbKeepWhenBought.isChecked();
        String key = RtdbKeyUtil.safeKey(name);
        ShoppingItem existing = adapter.getByKey(key);
        if (existing != null) {
            if (saved && !existing.saved) {
                // Item already in the list but the user now wants it saved — just flip the flag
                rtdb.setShoppingItemSaved(u, existing, true);
                rtdb.upsertRecurringDefault(u, existing);
                etName.setText("");
                etHowMany.setText("");
                cbKeepWhenBought.setChecked(false);
                fetchAndRender();
            } else {
                Toast.makeText(requireContext(), "\"" + name + "\" is already in the list.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        ShoppingItem item = new ShoppingItem(key, name, "other", howMany, false, saved, System.currentTimeMillis());
        rtdb.upsertShoppingItem(u, item);
        if (saved) rtdb.upsertRecurringDefault(u, item);
        etName.setText("");
        etHowMany.setText("");
        cbKeepWhenBought.setChecked(false);
        fetchAndRender();
    }

    /**
     * Opens a multi-select dialog that lets the user pick items to add to the list.
     * <p>
     * The dialog merges the built-in {@link #baseDefaultOptions} with the user's own
     * recurring defaults fetched from Firebase. Using a LinkedHashMap keyed by item key
     * ensures the user's saved version of an item overrides the built-in one, while
     * preserving display order (built-ins first, then extras).
     * The "Keep after bought" checkbox at the bottom applies to all items added in this batch.
     * </p>
     */
    private void showDefaultPickerDialog() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        rtdb.fetchRecurringDefaults(u, new RtdbRepository.RecurringDefaultsCallback() {
            @Override
            public void onSuccess(@NonNull List<ShoppingItem> recurringDefaults) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Merge built-in defaults with the user's recurring ones.
                        // LinkedHashMap keeps insertion order; user items overwrite built-ins with the same key.
                        Map<String, ShoppingItem> merged = new LinkedHashMap<>();
                        for (ShoppingItem item : baseDefaultOptions) merged.put(item.key, item);
                        for (ShoppingItem item : recurringDefaults) {
                            String key = item.key == null || item.key.trim().isEmpty()
                                    ? RtdbKeyUtil.safeKey(item.name)
                                    : item.key;
                            merged.put(key, item);
                        }
                        List<ShoppingItem> dialogDefaults = new ArrayList<>(merged.values());
                        String[] labels = new String[dialogDefaults.size()];
                        boolean[] selected = new boolean[dialogDefaults.size()];
                        for (int i = 0; i < dialogDefaults.size(); i++) {
                            labels[i] = dialogDefaults.get(i).name + " (" + dialogDefaults.get(i).category + ")";
                        }
                        final androidx.appcompat.widget.AppCompatCheckBox keepBox = new androidx.appcompat.widget.AppCompatCheckBox(requireContext());
                        keepBox.setText("Keep selected items after bought");
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Pick default items")
                                .setMultiChoiceItems(labels, selected, new DialogInterface.OnMultiChoiceClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                        selected[which] = isChecked;
                                    }
                                })
                                .setView(keepBox)
                                .setPositiveButton("Add selected", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        boolean saved = keepBox.isChecked();
                                        // Loop through all checkboxes and add only the ones the user ticked
                                        for (int i = 0; i < selected.length; i++) {
                                            if (!selected[i]) continue;
                                            ShoppingItem base = dialogDefaults.get(i);
                                            ShoppingItem add = new ShoppingItem(
                                                    RtdbKeyUtil.safeKey(base.name), base.name, base.category, 1, false, saved, System.currentTimeMillis()
                                            );
                                            rtdb.upsertShoppingItem(u, add);
                                            if (saved) rtdb.upsertRecurringDefault(u, add);
                                        }
                                        fetchAndRender();
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
            }

            @Override
            public void onFailure(@NonNull com.google.firebase.database.DatabaseError error) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(requireContext(), "Failed to load defaults.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }

    /**
     * Shows a small action menu when the user taps on an existing shopping item.
     * Options: change amount, toggle the "keep after bought" flag, or remove the item.
     * Toggling the saved flag also adds or removes the item from the recurring defaults in Firebase.
     */
    private void showItemActions(ShoppingItem item) {
        String keepLabel = item.saved ? "Do not keep after bought" : "Keep after bought";
        String[] options = new String[]{"Change amount", keepLabel, "Remove item"};
        new AlertDialog.Builder(requireContext())
                .setTitle(item.name)
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                        if (u == null) return;
                        if (which == 0) showChangeAmountDialog(item);
                        else if (which == 1) {
                            boolean nextSaved = !item.saved;
                            rtdb.setShoppingItemSaved(u, item, nextSaved);
                            if (nextSaved) rtdb.upsertRecurringDefault(u, item);
                            else rtdb.deleteRecurringDefault(u, item);
                            fetchAndRender();
                        }
                        else { rtdb.deleteShoppingItem(u, item); fetchAndRender(); }
                    }
                }).show();
    }

    /**
     * Asks the user to confirm before deleting all items that are currently marked as bought.
     * After confirmation, removes them from Firebase and updates the adapter and UI immediately
     * without waiting for a full network fetch.
     */
    private void confirmClearBought() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear bought items")
                .setMessage("Remove all checked items from the list?")
                .setPositiveButton("Clear", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                        if (u == null) return;
                        rtdb.clearAllBoughtShoppingItems(u);
                        adapter.removeBoughtItems();
                        layoutClearBought.setVisibility(View.GONE);
                        tvEmpty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Opens a dialog with a number input field so the user can update how many of
     * a given item they need. Saves the new amount to Firebase and refreshes the list.
     *
     * @param item the shopping item whose quantity is being changed
     */
    private void showChangeAmountDialog(ShoppingItem item) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(item.howMany));
        new AlertDialog.Builder(requireContext())
                .setTitle("Change amount").setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String raw = input.getText() == null ? "" : input.getText().toString().trim();
                        if (raw.isEmpty()) return;
                        int amount;
                        try { amount = Integer.parseInt(raw); } catch (NumberFormatException ignored) { return; }
                        rtdb.updateShoppingItemAmount(u, item, amount);
                        fetchAndRender();
                    }
                }).setNegativeButton("Cancel", null).show();
    }
}
