package com.example.myfridge.shopping;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myfridge.R;
import com.example.myfridge.rtdb.RtdbRepository;
import com.example.myfridge.rtdb.RtdbKeyUtil;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;

public class ShoppingActivity extends AppCompatActivity {
    private RtdbRepository rtdb;
    private ShoppingAdapter adapter;

    private RecyclerView rv;
    private TextView tvEmpty;

    private EditText etName;
    private EditText etHowMany;
    private Spinner spCategory;

    private final List<String> categoryValues = Arrays.asList("dairy", "produce", "other");
    private final List<ShoppingItem> defaultOptions = Arrays.asList(
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

    private @Nullable FirebaseUser user;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shopping);

        rtdb = new RtdbRepository();
        adapter = new ShoppingAdapter();

        rv = findViewById(R.id.rv_shopping);
        tvEmpty = findViewById(R.id.tv_shopping_empty);
        etName = findViewById(R.id.et_shopping_name);
        etHowMany = findViewById(R.id.et_shopping_how_many);
        spCategory = findViewById(R.id.sp_shopping_category);

        RecyclerView.LayoutManager lm = new LinearLayoutManager(this);
        rv.setLayoutManager(lm);
        rv.setAdapter(adapter);

        adapter.setOnShoppingItemChangeListener(new ShoppingAdapter.OnShoppingItemChangeListener() {
            @Override
            public void onBoughtChanged(@androidx.annotation.NonNull ShoppingItem item, boolean bought) {
                FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                if (u == null) return;
                rtdb.setShoppingItemBought(u, item, bought);
            }

            @Override
            public void onItemClicked(@androidx.annotation.NonNull ShoppingItem item) {
                showItemActions(item);
            }
        });

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                Arrays.asList("Dairy", "Produce", "Other")
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(categoryAdapter);

        spCategory.setSelection(0);

        Button btnAdd = findViewById(R.id.btn_shopping_add);
        btnAdd.setOnClickListener(v -> addProductToShopping());
        Button btnAddDefaults = findViewById(R.id.btn_shopping_defaults);
        btnAddDefaults.setOnClickListener(v -> showDefaultPickerDialog());

        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        user = current;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (user == null) {
            Toast.makeText(this, "Please log in again.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        fetchAndRender();
    }

    private void fetchAndRender() {
        if (user == null) return;
        rtdb.fetchShoppingList(user, new RtdbRepository.ShoppingItemsCallback() {
            @Override
            public void onSuccess(List<ShoppingItem> items) {
                runOnUiThread(() -> {
                    adapter.submit(items);
                    boolean empty = items == null || items.isEmpty();
                    tvEmpty.setText(empty ? "Your shopping list is empty." : "");
                    tvEmpty.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
                });
            }

            @Override
            public void onFailure(com.google.firebase.database.DatabaseError error) {
                runOnUiThread(() -> Toast.makeText(ShoppingActivity.this, "Failed to load shopping list.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void addProductToShopping() {
        if (user == null) return;

        String name = etName.getText() == null ? "" : etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Enter product name.", Toast.LENGTH_SHORT).show();
            return;
        }

        String qtyRaw = etHowMany.getText() == null ? "" : etHowMany.getText().toString().trim();
        int howMany = 1;
        if (!TextUtils.isEmpty(qtyRaw)) {
            try {
                howMany = Integer.parseInt(qtyRaw);
            } catch (NumberFormatException ignored) {
                howMany = 1;
            }
        }
        howMany = Math.max(1, howMany);

        int catIndex = spCategory.getSelectedItemPosition();
        String category = categoryValues.get(Math.max(0, Math.min(categoryValues.size() - 1, catIndex)));

        String key = RtdbKeyUtil.safeKey(name);
        long now = System.currentTimeMillis();
        ShoppingItem item = new ShoppingItem(key, name, category, howMany, false, false, now);

        rtdb.upsertShoppingItem(user, item);

        // Clear input quickly; reload from RTDB to reflect increments.
        etName.setText("");
        etHowMany.setText("");
        fetchAndRender();
    }

    private void showDefaultPickerDialog() {
        if (user == null) return;
        String[] labels = new String[defaultOptions.size()];
        boolean[] selected = new boolean[defaultOptions.size()];
        for (int i = 0; i < defaultOptions.size(); i++) {
            ShoppingItem item = defaultOptions.get(i);
            labels[i] = item.name + " (" + item.category + ")";
            selected[i] = false;
        }

        new AlertDialog.Builder(this)
                .setTitle("Pick default items")
                .setMultiChoiceItems(labels, selected, (dialog, which, isChecked) -> selected[which] = isChecked)
                .setPositiveButton("Add selected", (dialog, which) -> {
                    if (user == null) return;
                    int count = 0;
                    for (int i = 0; i < selected.length; i++) {
                        if (!selected[i]) continue;
                        ShoppingItem base = defaultOptions.get(i);
                        ShoppingItem add = new ShoppingItem(
                                RtdbKeyUtil.safeKey(base.name),
                                base.name,
                                base.category,
                                1,
                                false,
                                false,
                                System.currentTimeMillis()
                        );
                        rtdb.upsertShoppingItem(user, add);
                        count++;
                    }
                    if (count > 0) {
                        Toast.makeText(this, "Added " + count + " items.", Toast.LENGTH_SHORT).show();
                        fetchAndRender();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showItemActions(ShoppingItem item) {
        String[] options = new String[]{"Change amount", "Remove item"};
        new AlertDialog.Builder(this)
                .setTitle(item.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showChangeAmountDialog(item);
                    else if (which == 1) confirmRemove(item);
                })
                .show();
    }

    private void showChangeAmountDialog(ShoppingItem item) {
        if (user == null) return;
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(String.valueOf(item.howMany));

        new AlertDialog.Builder(this)
                .setTitle("Change amount")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String raw = input.getText() == null ? "" : input.getText().toString().trim();
                    if (raw.isEmpty()) return;
                    int amount;
                    try {
                        amount = Integer.parseInt(raw);
                    } catch (NumberFormatException ignored) {
                        return;
                    }
                    rtdb.updateShoppingItemAmount(user, item, amount);
                    fetchAndRender();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRemove(ShoppingItem item) {
        if (user == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Remove item")
                .setMessage("Remove \"" + item.name + "\" from shopping list?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    rtdb.deleteShoppingItem(user, item);
                    fetchAndRender();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

