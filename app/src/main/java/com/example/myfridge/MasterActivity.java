package com.example.myfridge;

import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Base activity that provides a top-right options button whose popup contains
 * the Settings entry (and any extras added by subclasses).
 * Subclasses supply their body layout via {@link #getMasterContentLayoutId()} — it is inflated
 * into the {@code master_content_host} frame from {@link R.layout#activity_master}.
 */
public abstract class MasterActivity extends AppCompatActivity {

    private NetworkChangeReceiver networkReceiver;

    @Override
    protected void onResume() {
        super.onResume();
        networkReceiver = new NetworkChangeReceiver(this);
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_master);

        ImageButton menuButton = findViewById(R.id.master_menu_button);
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(MasterActivity.this, v);
                popup.getMenuInflater().inflate(R.menu.menu_general, popup.getMenu());
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.menu_settings) {
                            onSettingsMenuSelected();
                            return true;
                        }
                        return onMasterMenuItemSelected(item);
                    }
                });
                popup.show();
            }
        });

        getLayoutInflater().inflate(getMasterContentLayoutId(), findViewById(R.id.master_content_host), true);
        onAfterMasterContentInflated(savedInstanceState);
    }

    /**
     * Handle popup menu items other than {@link R.id#menu_settings}.
     * @return true if consumed
     */
    protected boolean onMasterMenuItemSelected(MenuItem item) {
        return false;
    }

    /** Called when user chooses Settings from the options popup menu. */
    protected void onSettingsMenuSelected() {
    }

    @LayoutRes
    protected abstract int getMasterContentLayoutId();

    /**
     * Runs after {@link #getMasterContentLayoutId()} has been inflated into the content host.
     * Wire views and child logic here.
     */
    protected void onAfterMasterContentInflated(@Nullable Bundle savedInstanceState) {
    }
}
