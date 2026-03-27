package com.example.myfridge.fragments;

import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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

public class ShoppingFragment extends Fragment {
    private RtdbRepository rtdb;
    private ShoppingAdapter adapter;
    private TextView tvEmpty;
    private EditText etName;
    private EditText etHowMany;
    private androidx.appcompat.widget.AppCompatCheckBox cbKeepWhenBought;
    private @Nullable FirebaseUser user;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_shopping, container, false);
        rtdb = new RtdbRepository();
        adapter = new ShoppingAdapter();
        tvEmpty = root.findViewById(R.id.tv_shopping_empty);
        etName = root.findViewById(R.id.et_shopping_name);
        etHowMany = root.findViewById(R.id.et_shopping_how_many);
        cbKeepWhenBought = root.findViewById(R.id.cb_keep_when_bought);
        RecyclerView rv = root.findViewById(R.id.rv_shopping);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        adapter.setOnShoppingItemChangeListener(new ShoppingAdapter.OnShoppingItemChangeListener() {
            @Override public void onBoughtChanged(@NonNull ShoppingItem item, boolean bought) {
                FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                if (u != null) rtdb.setShoppingItemBought(u, item, bought);
            }
            @Override public void onItemClicked(@NonNull ShoppingItem item) { showItemActions(item); }
        });

        root.findViewById(R.id.btn_shopping_add).setOnClickListener(v -> addProductToShopping());
        root.findViewById(R.id.btn_shopping_defaults).setOnClickListener(v -> showDefaultPickerDialog());
        user = FirebaseAuth.getInstance().getCurrentUser();
        return root;
    }

    @Override public void onResume() { super.onResume(); fetchAndRender(); }

    @Override
    public void onPause() {
        super.onPause();
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u != null) rtdb.clearBoughtUnsavedShoppingItems(u);
    }

    private void fetchAndRender() {
        if (user == null) {
            if (isAdded()) Toast.makeText(requireContext(), "Please log in again.", Toast.LENGTH_SHORT).show();
            return;
        }
        rtdb.fetchShoppingList(user, new RtdbRepository.ShoppingItemsCallback() {
            @Override public void onSuccess(List<ShoppingItem> items) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    adapter.submit(items);
                    boolean empty = items == null || items.isEmpty();
                    tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                });
            }
            @Override public void onFailure(com.google.firebase.database.DatabaseError error) {
                if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Failed to load shopping list.", Toast.LENGTH_SHORT).show());
            }
        });
    }

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
        ShoppingItem item = new ShoppingItem(RtdbKeyUtil.safeKey(name), name, "other", howMany, false, saved, System.currentTimeMillis());
        rtdb.upsertShoppingItem(u, item);
        if (saved) rtdb.upsertRecurringDefault(u, item);
        etName.setText("");
        etHowMany.setText("");
        cbKeepWhenBought.setChecked(false);
        fetchAndRender();
    }

    private void showDefaultPickerDialog() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        rtdb.fetchRecurringDefaults(u, new RtdbRepository.RecurringDefaultsCallback() {
            @Override
            public void onSuccess(@NonNull List<ShoppingItem> recurringDefaults) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
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
                            .setMultiChoiceItems(labels, selected, (dialog, which, isChecked) -> selected[which] = isChecked)
                            .setView(keepBox)
                            .setPositiveButton("Add selected", (dialog, which) -> {
                                boolean saved = keepBox.isChecked();
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
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                });
            }

            @Override
            public void onFailure(@NonNull com.google.firebase.database.DatabaseError error) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), "Failed to load defaults.", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void showItemActions(ShoppingItem item) {
        String keepLabel = item.saved ? "Do not keep after bought" : "Keep after bought";
        String[] options = new String[]{"Change amount", keepLabel, "Remove item"};
        new AlertDialog.Builder(requireContext())
                .setTitle(item.name)
                .setItems(options, (dialog, which) -> {
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
                }).show();
    }

    private void showChangeAmountDialog(ShoppingItem item) {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        if (u == null) return;
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(item.howMany));
        new AlertDialog.Builder(requireContext())
                .setTitle("Change amount").setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String raw = input.getText() == null ? "" : input.getText().toString().trim();
                    if (raw.isEmpty()) return;
                    int amount;
                    try { amount = Integer.parseInt(raw); } catch (NumberFormatException ignored) { return; }
                    rtdb.updateShoppingItemAmount(u, item, amount);
                    fetchAndRender();
                }).setNegativeButton("Cancel", null).show();
    }
}

