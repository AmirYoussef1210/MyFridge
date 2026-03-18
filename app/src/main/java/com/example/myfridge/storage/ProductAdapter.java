package com.example.myfridge.storage;

import android.text.format.DateFormat;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myfridge.R;

import java.util.Date;
import java.util.ArrayList;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    private final List<Product> items = new ArrayList<>();
    public interface OnProductLongClickListener {
        void onLongClick(@NonNull Product product);
    }

    private OnProductLongClickListener longClickListener;

    public void setOnProductLongClickListener(OnProductLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void submit(List<Product> products) {
        items.clear();
        if (products != null) {
            items.addAll(products);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_storage_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product p = items.get(position);
        holder.name.setText(p.name);
        holder.meta.setText((p.category == null ? "" : p.category) + " • " + (p.unit == null ? "" : p.unit));
        holder.amount.setText(String.valueOf(p.amount));
        holder.dates.setText(buildDatesText(p));

        if (p.imageUri != null && !p.imageUri.trim().isEmpty()) {
            try {
                holder.image.setImageURI(Uri.parse(p.imageUri));
            } catch (Exception e) {
                holder.image.setImageResource(R.drawable.ic_fridge);
            }
        } else {
            holder.image.setImageResource(R.drawable.ic_fridge);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(p);
                return true;
            }
            return false;
        });
    }

    private String buildDatesText(Product p) {
        String added = formatDate(p.createdAtMs);
        String expires;
        if (p.expiresAtMs > 0L) {
            expires = formatDate(p.expiresAtMs);
        } else if (p.shelfLifeRaw != null && !p.shelfLifeRaw.trim().isEmpty()) {
            expires = p.shelfLifeRaw.trim();
        } else {
            expires = "Unknown";
        }
        return "Added: " + added + " • Expires: " + expires;
    }

    private String formatDate(long ms) {
        if (ms <= 0L) return "Unknown";
        return DateFormat.format("yyyy-MM-dd", new Date(ms)).toString();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView meta;
        final TextView dates;
        final TextView amount;

        VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.img_product);
            name = itemView.findViewById(R.id.txt_name);
            meta = itemView.findViewById(R.id.txt_meta);
            dates = itemView.findViewById(R.id.txt_dates);
            amount = itemView.findViewById(R.id.txt_amount);
        }
    }
}

