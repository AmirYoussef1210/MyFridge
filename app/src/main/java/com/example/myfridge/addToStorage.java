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
                        "You are a product analysis expert. Analyze the attached image and provide the details of the product in a valid JSON format only. Do not include any introductory text or markdown formatting like ```json.\n" +
                        "\n" +
                        "Task:\n" +
                        "Identify the product in the image and extract the following information:\n" +
                        "\n" +
                        "product_name: The common name of the product.\n" +
                        "\n" +
                        "shelf_life: The typical time it takes to spoil under standard storage conditions (e.g., \"7 days\", \"12 months\").\n" +
                        "\n" +
                        "measurement_unit: State whether it is typically measured in \"kg\" or \"ml\".\n" +
                        "\n" +
                        "category: The food category (e.g., Dairy, Meat, Vegetable, Fruit, etc.).\n" +
                        "\n" +
                        "Output Format (JSON):\n" +
                        "{\n" +
                        "\"product_name\": \"string\",\n" +
                        "\"shelf_life\": \"string\",\n" +
                        "\"measurement_unit\": \"string\",\n" +
                        "\"category\": \"string\"\n" +
                        "}";

                gM.sendTextWIthPhotoPrompt(prompt, imageBitmap, new GeminiCallBack() {
                    @Override
                    public void onSuccess(String result) {
                        runOnUiThread(() -> {
                            if (pD.isShowing()) {
                                pD.dismiss();
                            }
                            tVMsg.setText(result);

                            // Try to parse Gemini JSON and return to Storage screen.
                            try {
                                org.json.JSONObject o = new org.json.JSONObject(result);
                                String name = o.optString("product_name", "").trim();
                                String shelfLife = o.optString("shelf_life", "").trim();
                                String unit = o.optString("measurement_unit", "").trim();
                                String category = o.optString("category", "").trim();

                                if (!name.isEmpty()) {
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
                                }
                            } catch (Exception ignored) {
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
