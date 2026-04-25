package com.example.myfridge;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Simple "About" screen that displays information about the application.
 * <p>
 * Provides a clickable GitHub link that prompts the user to confirm before
 * opening the developer's GitHub profile in the device browser.
 * </p>
 */
public class AboutActivity extends AppCompatActivity {

    /**
     * Inflates the about layout and attaches a click listener to the GitHub
     * text view that shows a confirmation dialog before launching the browser.
     *
     * @param savedInstanceState previously saved instance state (may be {@code null})
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView tvGithub = findViewById(R.id.tv_about_github);
        tvGithub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new androidx.appcompat.app.AlertDialog.Builder(AboutActivity.this)
                        .setTitle("Open GitHub")
                        .setMessage("Open github.com/AmirYoussef1210 in your browser?")
                        .setPositiveButton("Open", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/AmirYoussef1210"));
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }
}
