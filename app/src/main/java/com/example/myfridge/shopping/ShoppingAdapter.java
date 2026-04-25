package com.example.myfridge.shopping;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myfridge.R;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link RecyclerView.Adapter} that displays a list of {@link ShoppingItem}
 * rows using {@code R.layout.item_shopping_item}.
 * <p>
 * Supports:
 * <ul>
 *   <li>Toggling the "bought" checkbox and propagating the change via
 *       {@link OnShoppingItemChangeListener#onBoughtChanged}</li>
 *   <li>Row-tap callbacks via {@link OnShoppingItemChangeListener#onItemClicked}</li>
 *   <li>Utility methods: {@link #getByKey}, {@link #hasBoughtItems},
 *       {@link #removeBoughtItems}</li>
 * </ul>
 * </p>
 */
public class ShoppingAdapter extends RecyclerView.Adapter<ShoppingAdapter.VH> {

    /**
     * Callback interface for shopping list item interactions.
     */
    public interface OnShoppingItemChangeListener {
        /**
         * Called when the "bought" checkbox state changes.
         *
         * @param item   the shopping item that was toggled
         * @param bought the new checked state
         */
        void onBoughtChanged(@NonNull ShoppingItem item, boolean bought);

        /**
         * Called when the user taps anywhere on the item row.
         *
         * @param item the tapped shopping item
         */
        void onItemClicked(@NonNull ShoppingItem item);
    }

    private final List<ShoppingItem> items = new ArrayList<>();
    private OnShoppingItemChangeListener boughtListener;

    /**
     * Sets the listener to receive bought-toggle and row-tap callbacks.
     *
     * @param listener the listener; may be {@code null} to clear
     */
    public void setOnShoppingItemChangeListener(OnShoppingItemChangeListener listener) {
        this.boughtListener = listener;
    }

    /**
     * Replaces the adapter's data set and triggers {@link #notifyDataSetChanged}.
     *
     * @param newItems the new list; {@code null} is treated as empty
     */
    public void submit(List<ShoppingItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    /**
     * Finds the item with the given key in the current data set.
     *
     * @param key the item key to search for
     * @return the matching {@link ShoppingItem} or {@code null} if not found
     */
    @Nullable
    public ShoppingItem getByKey(String key) {
        for (ShoppingItem item : items) {
            if (key != null && key.equals(item.key)) return item;
        }
        return null;
    }

    /**
     * Returns {@code true} if at least one item in the current list has been
     * marked as bought.
     *
     * @return {@code true} if there are any bought items
     */
    public boolean hasBoughtItems() {
        for (ShoppingItem item : items) {
            if (item.bought) return true;
        }
        return false;
    }

    /**
     * Removes all items whose {@link ShoppingItem#bought} flag is {@code true}
     * from the adapter's local list and triggers {@link #notifyDataSetChanged}.
     * Does <em>not</em> delete the items from RTDB — the caller is responsible
     * for that.
     */
    public void removeBoughtItems() {
        items.removeIf(new java.util.function.Predicate<ShoppingItem>() {
            @Override
            public boolean test(ShoppingItem item) {
                return item.bought;
            }
        });
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shopping_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ShoppingItem item = items.get(position);

        holder.cbBought.setOnCheckedChangeListener(null);
        holder.cbBought.setChecked(item.bought);

        holder.txtName.setText(item.name);
        holder.txtMeta.setText((item.category == null ? "" : item.category) + " • " + "qty");
        holder.txtQty.setText(String.valueOf(item.howMany));

        holder.cbBought.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                item.bought = isChecked;
                if (boughtListener != null) {
                    boughtListener.onBoughtChanged(item, isChecked);
                }
            }
        });

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (boughtListener != null) {
                    boughtListener.onItemClicked(item);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder that caches references to the views in
     * {@code R.layout.item_shopping_item}.
     */
    static class VH extends RecyclerView.ViewHolder {
        final CheckBox cbBought;
        final TextView txtName;
        final TextView txtMeta;
        final TextView txtQty;

        VH(@NonNull View itemView) {
            super(itemView);
            cbBought = itemView.findViewById(R.id.cb_bought);
            txtName = itemView.findViewById(R.id.txt_name);
            txtMeta = itemView.findViewById(R.id.txt_meta);
            txtQty = itemView.findViewById(R.id.txt_qty);
        }
    }
}

