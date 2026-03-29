package com.example.myfridge.shopping;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myfridge.R;

import java.util.ArrayList;
import java.util.List;

public class ShoppingAdapter extends RecyclerView.Adapter<ShoppingAdapter.VH> {

    public interface OnShoppingItemChangeListener {
        void onBoughtChanged(@NonNull ShoppingItem item, boolean bought);
        void onItemClicked(@NonNull ShoppingItem item);
    }

    private final List<ShoppingItem> items = new ArrayList<>();
    private OnShoppingItemChangeListener boughtListener;

    public void setOnShoppingItemChangeListener(OnShoppingItemChangeListener listener) {
        this.boughtListener = listener;
    }

    public void submit(List<ShoppingItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @Nullable
    public ShoppingItem getByKey(String key) {
        for (ShoppingItem item : items) {
            if (key != null && key.equals(item.key)) return item;
        }
        return null;
    }

    public boolean hasBoughtItems() {
        for (ShoppingItem item : items) {
            if (item.bought) return true;
        }
        return false;
    }

    public void removeBoughtItems() {
        items.removeIf(item -> item.bought);
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

        holder.cbBought.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.bought = isChecked;
            if (boughtListener != null) {
                boughtListener.onBoughtChanged(item, isChecked);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (boughtListener != null) {
                boughtListener.onItemClicked(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

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

