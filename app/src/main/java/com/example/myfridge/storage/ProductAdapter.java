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

/**
 * {@link RecyclerView.Adapter} that displays a list of {@link Product} items in
 * {@code R.layout.item_storage_product} rows.
 * <p>
 * Call {@link #submit(List)} to replace the current data set. Attach a click
 * listener via {@link #setOnProductClickListener} to receive callbacks when the
 * user taps a row (used to show the product options dialog).
 * </p>
 */
public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    private final List<Product> items = new ArrayList<>();

    /**
     * Callback interface for row tap events.
     */
    public interface OnProductClickListener {
        /**
         * Called when the user taps a product row.
         *
         * @param product the tapped product
         */
        void onClick(@NonNull Product product);
    }

    private OnProductClickListener clickListener;

    /**
     * Sets the listener to be notified when a product row is tapped.
     *
     * @param listener the click listener; may be {@code null} to clear
     */
    public void setOnProductClickListener(OnProductClickListener listener) {
        this.clickListener = listener;
    }

    /**
     * Replaces the adapter's data set with {@code products} and triggers a full
     * {@link #notifyDataSetChanged()} rebind.
     *
     * @param products the new list of products; {@code null} is treated as empty
     */
    public void submit(List<Product> products) {
        items.clear();
        if (products != null) {
            items.addAll(products);
        }
        notifyDataSetChanged();
    }

    /**
     * Inflates a new {@code R.layout.item_storage_product} row and wraps it in a {@link VH}.
     *
     * @param parent   the RecyclerView into which the new view will be added
     * @param viewType unused — all rows share the same layout
     * @return a newly inflated {@link VH}
     */
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_storage_product, parent, false);
        return new VH(v);
    }

    /**
     * Binds the product at {@code position} to {@code holder}, setting its name,
     * category/unit metadata, amount, dates, and thumbnail image.
     *
     * @param holder   the ViewHolder to bind
     * @param position the position of the item in the data set
     */
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

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListener != null) {
                    clickListener.onClick(p);
                }
            }
        });
    }

    /**
     * Builds the "Added: … • Expires: …" string shown in the product row.
     * Falls back to the raw shelf-life string, then to "Unknown", when an
     * exact expiry timestamp is unavailable.
     *
     * @param p the product to build the label for
     * @return formatted date string
     */
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

    /**
     * Formats a Unix timestamp in milliseconds to a {@code "yyyy-MM-dd"} string.
     *
     * @param ms the timestamp to format; {@code ≤ 0} returns {@code "Unknown"}
     * @return formatted date string or {@code "Unknown"}
     */
    private String formatDate(long ms) {
        if (ms <= 0L) return "Unknown";
        return DateFormat.format("yyyy-MM-dd", new Date(ms)).toString();
    }

    /**
     * Returns the number of products currently displayed by the adapter.
     *
     * @return the size of the data set
     */
    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder that caches references to the views in
     * {@code R.layout.item_storage_product}.
     */
    static class VH extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView meta;
        final TextView dates;
        final TextView amount;

        /**
         * Inflates and caches view references from the given item view.
         *
         * @param itemView the root view of the {@code R.layout.item_storage_product} row
         */
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

