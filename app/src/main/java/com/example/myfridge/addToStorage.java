package com.example.myfridge;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

public class addToStorage extends AppCompatActivity {
    TextView tVMsg;
    ImageView iV;
    Bitmap imageBitmap;
    String currentPath;
    String lastImageUriString;
    GeminiManager gM;
    private final String TAG = "addToStorage";
    private final int REQUEST_CAMERA_PERMISSION = 1421;
    private static final int REQUEST_FULL_IMAGE_CAPTURE = 3699;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_to_storage);

        iV = findViewById(R.id.iV);
        tVMsg = findViewById(R.id.tV);
        gM = GeminiManager.getInstance();
        tVMsg.setMovementMethod(new ScrollingMovementMethod());
    }

    public void enterPhoto(View view) {
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
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
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
                        "  \"shelf_life\": \"string (empty if is_fridge_item=false; e.g., '7 days', '12 months')\",\n" +
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
                                org.json.JSONObject o = new org.json.JSONObject(result);
                                boolean isFridgeItem = o.optBoolean("is_fridge_item", false);
                                String reason = o.optString("reason", "").trim();

                                if (!isFridgeItem) {
                                    tVMsg.setText("This doesn't look like a fridge food." + (reason.isEmpty() ? "" : (" Reason: " + reason)));
                                    return;
                                }

                                String name = o.optString("product_name", "").trim();
                                String shelfLife = o.optString("shelf_life", "").trim();
                                String unit = o.optString("measurement_unit", "").trim();
                                String category = o.optString("category", "").trim();

                                if (name.isEmpty()) {
                                    tVMsg.setText("Couldn't identify the product name. " + (reason.isEmpty() ? "" : ("Reason: " + reason)));
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
                                tVMsg.setText("Failed to parse AI response. Please try again.");
                            }
                        });
                    }

                    @Override
                    public void onFailure(Throwable error) {
                        runOnUiThread(() -> {
                            if (pD.isShowing()) {
                                pD.dismiss();
                            }
                            tVMsg.setText("Error: " + error.getMessage());
                            Log.e(TAG, "onActivityResult/ Error: " + error.getMessage());
                        });
                    }
                });
            }
        }
    }
}
