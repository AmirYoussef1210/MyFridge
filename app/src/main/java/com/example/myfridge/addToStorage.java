package com.example.myfridge;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myfridge.storage.ShelfLifeParser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

/**
 * Activity for adding a new product to the fridge inventory.
 * <p>
 * Supports two input modes:
 * <ul>
 *   <li><b>Camera / gallery</b> — the user takes or picks a photo, which is
 *       sent to the Gemini AI model for identification. The model returns a JSON
 *       object with product name, shelf life, unit and category.</li>
 *   <li><b>Manual entry</b> — the user types a product name; the Gemini model
 *       looks it up and returns the same JSON schema.</li>
 * </ul>
 * In both cases a confirmation dialog lets the user review and edit the
 * AI-suggested details before the product JSON is returned to the calling
 * activity via {@link android.app.Activity#setResult}.
 * </p>
 */
public class addToStorage extends AppCompatActivity {
    ImageView iV;
    Bitmap imageBitmap;
    String currentPath;
    String lastImageUriString;
    GeminiManager gM;
    private NetworkChangeReceiver networkReceiver;
    private final String TAG = "addToStorage";
    private final int REQUEST_CAMERA_PERMISSION = 1421;
    private static final int REQUEST_FULL_IMAGE_CAPTURE = 3699;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_to_storage);

        iV = findViewById(R.id.iV);
        gM = GeminiManager.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.btn_add_manually).setOnClickListener(v -> addManually());
    }

    /**
     * Shows an {@link android.app.AlertDialog} with a text input asking for the
     * product name, then delegates to {@link #lookupProductWithAI} when the user
     * confirms.
     */
    private void addManually() {
        EditText input = new EditText(this);
        input.setHint("e.g. Milk, Chicken breast, Yogurt");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(dp16, dp16, dp16, dp16);

        new AlertDialog.Builder(this)
                .setTitle("Add item manually")
                .setMessage("Enter the product name and AI will fill in the details.")
                .setView(input)
                .setPositiveButton("Look up", (dialog, which) -> {
                    String name = input.getText() == null ? "" : input.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(this, "Please enter a product name.", Toast.LENGTH_SHORT).show(); return; }
                    lookupProductWithAI(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Sends a text-only prompt to the Gemini model asking whether the given
     * product name is a refrigerator food item, and retrieves shelf-life,
     * unit and category information. On success calls
     * {@link #showManualConfirmDialog} so the user can review the result.
     *
     * @param productName the product name typed by the user
     */
    private void lookupProductWithAI(String productName) {
        ProgressDialog pD = new ProgressDialog(this);
        pD.setTitle("Looking up product");
        pD.setMessage("Checking if \"" + productName + "\" belongs in the fridge...");
        pD.setCancelable(false);
        pD.show();

        String prompt = "System Instructions:\n" +
                "You are a fridge inventory assistant. The user is manually adding the product \"" + productName + "\" to their fridge inventory. Decide if it is a FOOD item that belongs in a household refrigerator (i.e., something edible that can be stored in the fridge).\n" +
                "Exclude anything that is clearly NOT food (e.g., empty containers, plates/utensils, clothing, cleaning products) and anything where the content cannot be reasonably determined.\n" +
                "\n" +
                "Return ONLY valid JSON (no markdown, no extra text).\n" +
                "\n" +
                "Output JSON schema:\n" +
                "{\n" +
                "  \"is_fridge_item\": true|false,\n" +
                "  \"reason\": \"string (short, 1 sentence)\",\n" +
                "  \"product_name\": \"string (empty if is_fridge_item=false)\",\n" +
                "  \"shelf_life\": \"string (how long it stays fresh when stored in the fridge; empty if is_fridge_item=false; e.g., '7 days', '3 weeks')\",\n" +
                "  \"measurement_unit\": \"kg\"|\"ml\"|\"piece\" (empty if is_fridge_item=false)\",\n" +
                "  \"category\": \"dairy\"|\"produce\"|\"meat\"|\"vegetable\"|\"fruit\"|\"other\" (empty if is_fridge_item=false) \n" +
                "}\n" +
                "\n" +
                "Notes:\n" +
                "- If the item is food but cannot be stored in a fridge, set is_fridge_item=false.\n" +
                "- If the item is food that belongs in the fridge, set is_fridge_item=true and fill in product_name/shelf_life/measurement_unit/category.\n" +
                "- measurement_unit should be consistent with typical grocery packaging: use 'piece' for eggs/butter sticks/cheese blocks, 'kg' for many produce items, and 'ml' for liquids.\n" +
                "- category should be lowercase.\n";

        gM.sendTextPrompt(prompt, new GeminiCallBack() {
            @Override
            public void onSuccess(String result) {
                runOnUiThread(() -> {
                    pD.dismiss();
                    try {
                        org.json.JSONObject o = new org.json.JSONObject(stripMarkdown(result));
                        boolean isFridgeItem = o.optBoolean("is_fridge_item", false);
                        String reason = o.optString("reason", "").trim();

                        if (!isFridgeItem) {
                            showError("Not a fridge item",
                                    "\"" + productName + "\" doesn't seem to belong in the fridge." +
                                    (reason.isEmpty() ? "" : "\n\n" + reason));
                            return;
                        }

                        String name      = o.optString("product_name", productName).trim();
                        String shelfLife = o.optString("shelf_life", "").trim();
                        String unit      = o.optString("measurement_unit", "piece").trim();
                        String category  = o.optString("category", "other").trim();

                        long now = System.currentTimeMillis();
                        long duration = ShelfLifeParser.parseToDurationMillis(shelfLife);
                        long expiresAtMs = duration > 0 ? now + duration : 0L;

                        showManualConfirmDialog(name, unit, category, shelfLife, expiresAtMs);
                    } catch (Exception e) {
                        showError("AI Response", result);
                    }
                });
            }

            @Override
            public void onFailure(Throwable error) {
                runOnUiThread(() -> {
                    pD.dismiss();
                    showError("AI Error", "Something went wrong: " + error.getMessage());
                });
            }
        });
    }

    /**
     * Builds and shows a scrollable dialog pre-filled with the AI-suggested
     * product details. The user can edit any field, tap a date to override the
     * expiry date via a {@link android.app.DatePickerDialog}, then confirm to
     * return the product JSON to the calling activity.
     *
     * @param name        AI-suggested product name
     * @param unit        measurement unit (e.g. {@code "kg"}, {@code "ml"}, {@code "piece"})
     * @param category    product category (e.g. {@code "dairy"})
     * @param shelfLife   raw shelf-life string (e.g. {@code "7 days"}) used as a fallback label
     * @param expiresAtMs expiry timestamp in milliseconds since epoch; {@code 0} if unknown
     */
    private void showManualConfirmDialog(String name, String unit, String category, String shelfLife, long expiresAtMs) {
        int dp16 = (int) (16 * getResources().getDisplayMetrics().density);
        int dp4  = (int) (4  * getResources().getDisplayMetrics().density);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp16, dp4, dp16, dp4);

        EditText etName = new EditText(this);
        etName.setText(name);
        etName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        layout.addView(fieldLabel("Product name"));
        layout.addView(etName);

        EditText etCategory = new EditText(this);
        etCategory.setText(category);
        layout.addView(fieldLabel("Category"));
        layout.addView(etCategory);

        EditText etUnit = new EditText(this);
        etUnit.setText(unit);
        etUnit.setHint("kg / ml / piece");
        layout.addView(fieldLabel("Unit"));
        layout.addView(etUnit);

        final long[] expiresHolder = {expiresAtMs};
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        TextView tvExpiry = new TextView(this);
        tvExpiry.setPadding(dp4, dp4 * 2, dp4, dp4 * 2);
        tvExpiry.setTextColor(Color.parseColor("#1565C0"));
        tvExpiry.setTextSize(16);
        tvExpiry.setText(expiresAtMs > 0 ? sdf.format(new Date(expiresAtMs)) : "Tap to set expiry date");
        tvExpiry.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            if (expiresHolder[0] > 0) cal.setTimeInMillis(expiresHolder[0]);
            new DatePickerDialog(this, (picker, year, month, day) -> {
                Calendar sel = Calendar.getInstance();
                sel.set(year, month, day, 12, 0, 0);
                expiresHolder[0] = sel.getTimeInMillis();
                tvExpiry.setText(sdf.format(new Date(expiresHolder[0])));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        layout.addView(fieldLabel("Expiry date (tap to change)"));
        layout.addView(tvExpiry);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);

        new AlertDialog.Builder(this)
                .setTitle("Confirm item")
                .setMessage("AI-suggested details — edit if needed before adding.")
                .setView(scroll)
                .setPositiveButton("Add to fridge", (dialog, which) -> {
                    String finalName = etName.getText() == null ? "" : etName.getText().toString().trim();
                    if (finalName.isEmpty()) { Toast.makeText(this, "Product name is required.", Toast.LENGTH_SHORT).show(); return; }
                    try {
                        org.json.JSONObject out = new org.json.JSONObject();
                        out.put("name",         finalName);
                        out.put("unit",         etUnit.getText() == null ? unit : etUnit.getText().toString().trim());
                        out.put("category",     etCategory.getText() == null ? category : etCategory.getText().toString().trim());
                        out.put("imageUri",     "");
                        out.put("shelfLifeRaw", shelfLife);
                        out.put("expiresAtMs",  expiresHolder[0]);
                        out.put("addedAtMs",    System.currentTimeMillis());

                        Intent data = new Intent();
                        data.putExtra(StorageActivity.EXTRA_PRODUCT_JSON, out.toString());
                        setResult(Activity.RESULT_OK, data);
                        finish();
                    } catch (Exception e) {
                        showError("Error", "Failed to save product.");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Strips a Markdown code-fence wrapper (e.g. {@code ```json ... ```}) from
     * the model's raw output so the remainder can be parsed as plain JSON.
     *
     * @param raw the raw string returned by Gemini; may be {@code null}
     * @return the trimmed content between the fences, or an empty string if
     *         {@code raw} is {@code null}
     */
    private static String stripMarkdown(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline != -1) s = s.substring(firstNewline + 1);
            if (s.endsWith("```")) s = s.substring(0, s.lastIndexOf("```"));
        }
        return s.trim();
    }

    /**
     * Creates a small grey label {@link android.widget.TextView} suitable for
     * use as a field heading inside the confirm dialog layout.
     *
     * @param text the label text to display
     * @return a styled {@link android.widget.TextView}
     */
    private TextView fieldLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor("#757575"));
        int dp12 = (int) (12 * getResources().getDisplayMetrics().density);
        tv.setPadding(0, dp12, 0, 0);
        return tv;
    }

    /**
     * Launches a chooser that lets the user either take a new photo with the
     * camera or pick an existing image from the gallery. The selected image is
     * processed in {@link #onActivityResult}.
     *
     * @param view the view that triggered this call (camera button in the layout)
     */
    public void enterPhoto(View view) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {
                // User denied once before — show rationale then re-request
                new AlertDialog.Builder(this)
                        .setTitle("Camera permission needed")
                        .setMessage("Camera access is required to take a photo of your product.")
                        .setPositiveButton("Allow", (d, w) -> ActivityCompat.requestPermissions(
                                this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION))
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                // First-time request (system will show the dialog)
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            }
            return;
        }
        String filename = "tempfile";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        Uri imageUri;
        Intent takePhotoIntent = null;

        try {
            File imgFile = File.createTempFile(filename, ".jpg", storageDir);
            currentPath = imgFile.getAbsolutePath();

            imageUri = FileProvider.getUriForFile(
                    addToStorage.this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    imgFile
            );
            takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to create temporary file", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error creating temp file", e);
            return;
        }

        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        Intent chooserIntent = Intent.createChooser(galleryIntent, "Select Source");
        if (takePhotoIntent != null) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{takePhotoIntent});
        }

        if (chooserIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(chooserIntent, REQUEST_FULL_IMAGE_CAPTURE);
        } else {
            Toast.makeText(this, "No compatible application found.", Toast.LENGTH_LONG).show();
        }
    }

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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission just granted — proceed straight to the camera/gallery chooser
                enterPhoto(null);
            } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {
                // Denied but "Don't ask again" was NOT checked — a single toast is enough
                Toast.makeText(this, "Camera permission is needed to take photos.", Toast.LENGTH_SHORT).show();
            } else {
                // "Don't ask again" was checked — requestPermissions would silently do nothing,
                // so direct the user to Settings instead of spamming permission dialogs/toasts
                new AlertDialog.Builder(this)
                        .setTitle("Camera permission required")
                        .setMessage("Camera permission was permanently denied. Please enable it in Settings to take photos.")
                        .setPositiveButton("Open Settings", (d, w) -> {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", getPackageName(), null));
                            startActivity(intent);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }

    /**
     * Displays a simple error {@link android.app.AlertDialog} with an OK button.
     *
     * @param title   dialog title
     * @param message the error message body
     */
    private void showError(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data_back) {
        super.onActivityResult(requestCode, resultCode, data_back);

        if ((requestCode == REQUEST_FULL_IMAGE_CAPTURE) && (resultCode == Activity.RESULT_OK)) {
            Bitmap finalBitmap = null;

            if (data_back != null && data_back.getData() != null) {
                try {
                    Uri picked = data_back.getData();
                    lastImageUriString = picked.toString();
                    finalBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), picked);
                } catch (IOException e) {
                    Log.e(TAG, "Error loading gallery image", e);
                    Toast.makeText(this, "Failed to load image from gallery.", Toast.LENGTH_LONG).show();
                    return;
                }
            } else if (currentPath != null) {
                finalBitmap = BitmapFactory.decodeFile(currentPath);
                lastImageUriString = Uri.fromFile(new File(currentPath)).toString();
            }

            if (finalBitmap != null) {
                imageBitmap = finalBitmap;
                iV.setImageBitmap(imageBitmap);

                final ProgressDialog pD = new ProgressDialog(this);
                pD.setTitle("Sent Prompt");
                pD.setMessage("Waiting for description....");
                pD.setCancelable(false);
                pD.show();

                String prompt = "System Instructions:\n" +
                        "You are a fridge inventory assistant. Analyze the attached image and decide if it depicts a FOOD item that belongs in a household refrigerator (i.e., something edible that can be stored in the fridge).\n" +
                        "Exclude anything that is clearly NOT food (e.g., empty containers, plates/utensils, clothing, cleaning products) and anything where the content cannot be reasonably determined.\n" +
                        "\n" +
                        "Return ONLY valid JSON (no markdown, no extra text).\n" +
                        "\n" +
                        "Output JSON schema:\n" +
                        "{\n" +
                        "  \"is_fridge_item\": true|false,\n" +
                        "  \"reason\": \"string (short, 1 sentence)\",\n" +
                        "  \"product_name\": \"string (empty if is_fridge_item=false)\",\n" +
                        "  \"shelf_life\": \"string (how long it stays fresh when stored in the fridge; empty if is_fridge_item=false; e.g., '7 days', '3 weeks')\",\n" +
                        "  \"measurement_unit\": \"kg\"|\"ml\"|\"piece\" (empty if is_fridge_item=false)\",\n" +
                        "  \"category\": \"dairy\"|\"produce\"|\"meat\"|\"vegetable\"|\"fruit\"|\"other\" (empty if is_fridge_item=false) \n" +
                        "}\n" +
                        "\n" +
                        "Notes:\n" +
                        "- If the item is food but cannot be stored in a fridge, set is_fridge_item=false.\n" +
                        "- If the item is food that belongs in the fridge, set is_fridge_item=true and fill in product_name/shelf_life/measurement_unit/category.\n" +
                        "- measurement_unit should be consistent with typical grocery packaging: use 'piece' for eggs/butter sticks/cheese blocks, 'kg' for many produce items, and 'ml' for liquids.\n" +
                        "- category should be lowercase.\n";

                gM.sendTextWIthPhotoPrompt(prompt, imageBitmap, new GeminiCallBack() {
                    @Override
                    public void onSuccess(String result) {
                        runOnUiThread(() -> {
                            if (pD.isShowing()) {
                                pD.dismiss();
                            }

                            // Try to parse Gemini JSON and return to Storage screen.
                            try {
                                org.json.JSONObject o = new org.json.JSONObject(stripMarkdown(result));
                                boolean isFridgeItem = o.optBoolean("is_fridge_item", false);
                                String reason = o.optString("reason", "").trim();

                                if (!isFridgeItem) {
                                    showError("Not a fridge item", "This doesn't look like a fridge food." + (reason.isEmpty() ? "" : ("\n\nReason: " + reason)));
                                    return;
                                }

                                String name = o.optString("product_name", "").trim();
                                String shelfLife = o.optString("shelf_life", "").trim();
                                String unit = o.optString("measurement_unit", "").trim();
                                String category = o.optString("category", "").trim();

                                if (name.isEmpty()) {
                                    showError("Unrecognized Item", "Couldn't identify the product name." + (reason.isEmpty() ? "" : ("\n\nReason: " + reason)));
                                    return;
                                }

                                long now = System.currentTimeMillis();
                                long expiresAtMs = 0L;
                                long duration = com.example.myfridge.storage.ShelfLifeParser.parseToDurationMillis(shelfLife);
                                if (duration > 0L) {
                                    expiresAtMs = now + duration;
                                }

                                org.json.JSONObject out = new org.json.JSONObject();
                                out.put("name", name);
                                out.put("unit", unit);
                                out.put("category", category);
                                out.put("imageUri", lastImageUriString == null ? "" : lastImageUriString);
                                out.put("shelfLifeRaw", shelfLife);
                                out.put("expiresAtMs", expiresAtMs);
                                out.put("addedAtMs", now);

                                Intent data = new Intent();
                                data.putExtra(StorageActivity.EXTRA_PRODUCT_JSON, out.toString());
                                setResult(Activity.RESULT_OK, data);
                                finish();
                            } catch (Exception ignored) {
                                showError("AI Response", result);
                            }
                        });
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        runOnUiThread(() -> {
                            if (pD.isShowing()) {
                                pD.dismiss();
                            }
                            showError("AI Error", "Something went wrong: " + error.getMessage());
                            Log.e(TAG, "onActivityResult/ Error: " + error.getMessage());
                        });
                    }
                });
            }
        }
    }
}
