package com.example.myfridge;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

/**
 * Base activity that provides the shared app bar and overflow (General) menu.
 * Subclasses supply their body layout via {@link #getMasterContentLayoutId()} — it is inflated
 * into the {@code master_content_host} frame from {@link R.layout#activity_master}.
 */
public abstract class MasterActivity extends AppCompatActivity {

    private MaterialToolbar masterToolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_master);

        masterToolbar = findViewById(R.id.master_toolbar);
        setSupportActionBar(masterToolbar);

        getLayoutInflater().inflate(getMasterContentLayoutId(), findViewById(R.id.master_content_host), true);
        onAfterMasterContentInflated(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_general, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_settings) {
            onSettingsMenuSelected();
            return true;
        }
        return onMasterMenuItemSelected(item) || super.onOptionsItemSelected(item);
    }

    /**
     * Handle toolbar menu items other than {@link R.id#menu_settings}.
     * @return true if consumed
     */
    protected boolean onMasterMenuItemSelected(MenuItem item) {
        return false;
    }

    /** Called when user chooses Settings from the General overflow menu. */
    protected void onSettingsMenuSelected() {
    }

    protected MaterialToolbar getMasterToolbar() {
        return masterToolbar;
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
