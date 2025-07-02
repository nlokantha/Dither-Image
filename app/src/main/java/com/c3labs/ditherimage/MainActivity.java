package com.c3labs.ditherimage;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1;
    private static final String TAG = "tag";
    private ImageView originalImageView, ditheredImageView;
    private Button selectImageBtn, saveImageBtn, saveHexBtn;
    private Bitmap ditheredBitmap;
    private ProgressBar progressBar;

    private static final String SERVICE_TYPE = "_http._tcp.";

    private ListView deviceListView;
    private Button scanButton;
    private Button sendButton;
    private TextView statusText;



    private final int[][] PALETTE_BGR = {
            {0x00, 0x00, 0x00},  // BLACK (index 0)
            {0xFF, 0xFF, 0xFF},  // WHITE (index 1)
            {0x00, 0xFF, 0xFF},  // YELLOW (index 2)
            {0x00, 0x00, 0xFF},  // RED (index 3)
            {0xFF, 0x00, 0x00},  // BLUE (index 4)
            {0x00, 0xFF, 0x00}   // GREEN (index 5)
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        originalImageView = findViewById(R.id.original_image);
        ditheredImageView = findViewById(R.id.dithered_image);
        selectImageBtn = findViewById(R.id.select_image_btn);
        saveImageBtn = findViewById(R.id.buttonSave);
        saveHexBtn = findViewById(R.id.buttonSaveHex);
        statusText = findViewById(R.id.statusText);

        progressBar = findViewById(R.id.progressBar);

        selectImageBtn.setOnClickListener(v -> openGallery());
        saveImageBtn.setOnClickListener(v -> saveImage());
//        saveHexBtn.setOnClickListener(v -> saveHexFile());
        saveHexBtn.setOnClickListener(v -> saveHexFile());

        saveHexBtn.setVisibility(View.GONE);

        initViews();

    }

    private void initViews() {
        deviceListView = findViewById(R.id.deviceListView);
        scanButton = findViewById(R.id.buttonScan);
        sendButton = findViewById(R.id.buttonSend2);


        sendButton.setEnabled(false);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap originalBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                if(originalBitmap.getWidth() != 1600 && originalBitmap.getHeight() != 1200){
                    Toast.makeText(this, "Invalid Image", Toast.LENGTH_SHORT).show();
                    return;
                }
                Bitmap rotatedBitmap = rotateBitmap(originalBitmap, -90);
                originalImageView.setImageBitmap(originalBitmap);

                new Thread(() -> {
//                    Bitmap whiteBalanced = adjustWhiteBalance(rotatedBitmap, 0.95f, 1.0f, 2f);
//                    Bitmap blacksBalanced = adjustBlacks(rotatedBitmap, 0.1f);
//                    Bitmap saturatedImage = increaseSaturation(rotatedBitmap, 1.2f);
//                    Bitmap contrasted = adjustContrast(rotatedBitmap, 1.2f);
//                    Bitmap saturatedImage = increaseSaturation(rotatedBitmap, 1.5f);

//                    Bitmap boosted = boostColors(rotatedBitmap);
//                    Bitmap blueEnhanced = enhanceBlues(boosted);
//                    Bitmap contrasted = adjustContrast(boosted, 1.2f);
                    ditheredBitmap = applyFloydSteinbergDithering(rotatedBitmap);

//                    Bitmap boosted = boostColors(rotatedBitmap);




//                    Bitmap shadowBoosted = adjustShadows(saturatedImage, 0.3f);
//

//                    ditheredBitmap = applyFloydSteinbergDithering(boosted);
                    runOnUiThread(() -> {
                        saveHexBtn.setVisibility(View.VISIBLE);
                        ditheredImageView.setImageBitmap(ditheredBitmap);

                        originalBitmap.recycle();
                    });
                }).start();
            } catch (IOException e) {
                Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
    }

    private Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private Bitmap increaseSaturation(Bitmap src, float amount) {
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(amount); // >1 = more color; <1 = desaturate

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColorFilter(filter);
        canvas.drawBitmap(src, 0, 0, paint);

        return result;
    }

//

    private Bitmap adjustContrast(Bitmap src, float contrast) {
        // Formula: output = (input - 128) * contrast + 128
        float scale = contrast;
        float translate = 128 * (1 - contrast);

        ColorMatrix colorMatrix = new ColorMatrix(new float[] {
                scale, 0, 0, 0, translate,
                0, scale, 0, 0, translate,
                0, 0, scale, 0, translate,
                0, 0, 0, 1, 0
        });

        ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        paint.setColorFilter(filter);
        canvas.drawBitmap(src, 0, 0, paint);

        return result;
    }

//private Bitmap boostColors(Bitmap original) {
//    int width = original.getWidth();
//    int height = original.getHeight();
//    Bitmap boosted = original.copy(Bitmap.Config.ARGB_8888, true);
//
//    int[] pixels = new int[width * height];
//    boosted.getPixels(pixels, 0, width, 0, 0, width, height);
//
//    // Enhanced color boosting that preserves relationships
//    for (int i = 0; i < pixels.length; i++) {
//        int r = Color.red(pixels[i]);
//        int g = Color.green(pixels[i]);
//        int b = Color.blue(pixels[i]);
//
//        // Calculate colorfulness (distance from gray)
//        float max = Math.max(r, Math.max(g, b));
//        float min = Math.min(r, Math.min(g, b));
//        float delta = max - min;
//        float lightness = (max + min) / 2f;
//
//        // Boost colors that aren't already saturated
//        if (delta < 100) {
//            float boostFactor = 1.5f + (1 - delta/100f);
//
//            // Boost while preserving hue
//            float avg = (r + g + b) / 3f;
//            r = clamp((int)(avg + (r - avg) * boostFactor));
//            g = clamp((int)(avg + (g - avg) * boostFactor));
//            b = clamp((int)(avg + (b - avg) * boostFactor));
//        }
//
//        // Special handling for blues which often appear dark
//        if (b > r * 1.2 && b > g * 1.2 && b < 150) {
//            b = clamp(b + 50);
//        }
//
//        // Special handling for greens which often appear dark
//        if (g > r * 1.2 && g > b * 1.2 && g < 150) {
//            g = clamp(g + 50);
//        }
//
//        pixels[i] = Color.rgb(r, g, b);
//    }
//
//    boosted.setPixels(pixels, 0, width, 0, 0, width, height);
//    return boosted;
//}
private Bitmap boostColors(Bitmap original) {
    int width = original.getWidth();
    int height = original.getHeight();
    Bitmap boosted = original.copy(Bitmap.Config.ARGB_8888, true);

    int[] pixels = new int[width * height];
    boosted.getPixels(pixels, 0, width, 0, 0, width, height);

    for (int i = 0; i < pixels.length; i++) {
        int r = Color.red(pixels[i]);
        int g = Color.green(pixels[i]);
        int b = Color.blue(pixels[i]);

        // Calculate colorfulness
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;

        // Special handling for greens
        if (g > r && g > b) {  // If green is dominant
            // Boost green more aggressively
            float greenBoost = 1.8f;
            g = clamp((int)(g * greenBoost));

            // Reduce other channels to maintain hue
            if (delta < 50) {
                r = clamp((int)(r * 0.7f));
                b = clamp((int)(b * 0.7f));
            }
        }
        // Keep the rest of your boosting logic
        else if (delta < 100) {
            float boostFactor = 1.5f + (1 - delta/100f);
            float avg = (r + g + b) / 3f;
            r = clamp((int)(avg + (r - avg) * boostFactor));
            g = clamp((int)(avg + (g - avg) * boostFactor));
            b = clamp((int)(avg + (b - avg) * boostFactor));
        }

        pixels[i] = Color.rgb(r, g, b);
    }

    boosted.setPixels(pixels, 0, width, 0, 0, width, height);
    return boosted;
}
    private Bitmap applyFloydSteinbergDithering(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        Bitmap ditheredBitmap = original.copy(Bitmap.Config.ARGB_8888, true);

        // Create line buffers (3 lines)
        int[][][] lineBuffers = new int[3][width][3];

        // Initialize first two lines
        for (int y = 0; y < 2 && y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = original.getPixel(x, y);
                lineBuffers[y][x][0] = Color.blue(pixel);  // B
                lineBuffers[y][x][1] = Color.green(pixel); // G
                lineBuffers[y][x][2] = Color.red(pixel);    // R (converted to BGR)
            }
        }

        int[] pixels = new int[width * height];
        ditheredBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int y = 0; y < height; y++) {
            // Read next line if available
            if (y + 2 < height) {
                for (int x = 0; x < width; x++) {
                    int pixel = original.getPixel(x, y+2);
                    lineBuffers[2][x][0] = Color.blue(pixel);
                    lineBuffers[2][x][1] = Color.green(pixel);
                    lineBuffers[2][x][2] = Color.red(pixel);
                }
            }

            // Process current line
            for (int x = 0; x < width; x++) {
                int[] pixel = lineBuffers[0][x];
                byte index = findNearestColor(pixel);

                // Store the palette color in output bitmap
                pixels[y * width + x] = Color.rgb(
                        PALETTE_BGR[index][2], // R
                        PALETTE_BGR[index][1], // G
                        PALETTE_BGR[index][0]  // B
                );

                // Calculate error for each channel
                for (int c = 0; c < 3; c++) {
                    int error = pixel[c] - PALETTE_BGR[index][c];
                    spreadError(lineBuffers, x, y, c, error, width, height);
                }
            }

            // Shift line buffers up
            System.arraycopy(lineBuffers[1], 0, lineBuffers[0], 0, width);
            System.arraycopy(lineBuffers[2], 0, lineBuffers[1], 0, width);
        }

        ditheredBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return ditheredBitmap;
    }

    private byte findNearestColor(int[] pixel) {
        int minDistance = Integer.MAX_VALUE;
        byte bestIndex = 0;
        for (byte i = 0; i < PALETTE_BGR.length; i++) {
            int bDiff = pixel[0] - PALETTE_BGR[i][0];
            int gDiff = pixel[1] - PALETTE_BGR[i][1];
            int rDiff = pixel[2] - PALETTE_BGR[i][2];
            int distance = rDiff*rDiff + gDiff*gDiff + bDiff*bDiff;
            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void spreadError(int[][][] lineBuffers, int x, int y,
                             int channel, int error, int width, int height) {
        // Current line (right neighbors)
        if (x + 1 < width) {
            lineBuffers[0][x+1][channel] = clamp(lineBuffers[0][x+1][channel] + (error * 8) / 42);
        }
        if (x + 2 < width) {
            lineBuffers[0][x+2][channel] = clamp(lineBuffers[0][x+2][channel] + (error * 4) / 42);
        }

        // Next line
        if (y + 1 < height) {
            if (x > 1) {
                lineBuffers[1][x-2][channel] = clamp(lineBuffers[1][x-2][channel] + (error * 2) / 42);
            }
            if (x > 0) {
                lineBuffers[1][x-1][channel] = clamp(lineBuffers[1][x-1][channel] + (error * 4) / 42);
            }
            lineBuffers[1][x][channel] = clamp(lineBuffers[1][x][channel] + (error * 8) / 42);
            if (x + 1 < width) {
                lineBuffers[1][x+1][channel] = clamp(lineBuffers[1][x+1][channel] + (error * 4) / 42);
            }
            if (x + 2 < width) {
                lineBuffers[1][x+2][channel] = clamp(lineBuffers[1][x+2][channel] + (error * 2) / 42);
            }
        }

        // Line after next
        if (y + 2 < height) {
            if (x > 1) {
                lineBuffers[2][x-2][channel] = clamp(lineBuffers[2][x-2][channel] + error / 42);
            }
            if (x > 0) {
                lineBuffers[2][x-1][channel] = clamp(lineBuffers[2][x-1][channel] + (error * 2) / 42);
            }
            lineBuffers[2][x][channel] = clamp(lineBuffers[2][x][channel] + (error * 4) / 42);
            if (x + 1 < width) {
                lineBuffers[2][x+1][channel] = clamp(lineBuffers[2][x+1][channel] + (error * 2) / 42);
            }
            if (x + 2 < width) {
                lineBuffers[2][x+2][channel] = clamp(lineBuffers[2][x+2][channel] + error / 42);
            }
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    // Update your convertToHex method to use the new palette indices
    private String getColorCodeApprox(Color color) {
        int r = (int)(color.red() * 255);
        int g = (int)(color.green() * 255);
        int b = (int)(color.blue() * 255);

        // Convert to BGR for matching
        int[] pixel = {b, g, r};
        byte index = findNearestColor(pixel);

        // Map to your existing binary codes
        switch(index) {
            case 0: return "0000"; // BLACK
            case 1: return "0001"; // WHITE
            case 2: return "0010"; // YELLOW
            case 3: return "0011"; // RED
            case 4: return "0101"; // BLUE
            case 5: return "0110"; // GREEN
            default: return "0000"; // fallback
        }
    }


    private void saveImage() {
        if (ditheredBitmap == null) {
            Toast.makeText(this, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Dithering App");
        if (!dir.exists() && !dir.mkdirs()) {
            Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show();
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File file = new File(dir, "Dithered_" + timeStamp + ".png");

        try (FileOutputStream out = new FileOutputStream(file)) {
            ditheredBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), file.getName(), null);
            Toast.makeText(this, "Image saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error saving image", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void saveHexFile() {
        if (ditheredBitmap == null) {
            Toast.makeText(this, "No image to convert", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String hexData = convertToHex(ditheredBitmap);
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOCUMENTS), "Dithering App");
                if (!dir.exists() && !dir.mkdirs()) {
                    runOnUiThread(() -> Toast.makeText(this, "Failed to create directory", Toast.LENGTH_SHORT).show());
                    return;
                }

                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File file = new File(dir, "Dithered_" + timeStamp + ".c");

                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(hexData.getBytes());
                    runOnUiThread(() -> Toast.makeText(this,
                            "Hex file saved to " + file.getAbsolutePath(), Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Error saving hex file: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                e.printStackTrace();
            }
        }).start();
    }




    private String convertToHex(Bitmap bitmap) {
    StringBuilder sb = new StringBuilder();
    String variableName = "dithered_image_" +
            new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    int[] pixels = new int[width * height];
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

    // Declare fixed-size array if needed (change 288000 to actual size if known)
    int totalPixels = width * height;
    int totalBytes = (totalPixels + 1) / 2;
    sb.append("const unsigned char ").append(variableName)
            .append("[").append(totalBytes).append("] = {\n");

    // Show and configure progress bar
    runOnUiThread(() -> {
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setMax(totalBytes);
        progressBar.setProgress(0);
    });

    int count = 0;
    for (int i = 0; i < pixels.length; i += 2) {
        String colorCode1 = getColorCodeApprox(Color.valueOf(pixels[i]));
        String colorCode2 = "0000";
        if (i + 1 < pixels.length) {
            colorCode2 = getColorCodeApprox(Color.valueOf(pixels[i + 1]));
        }

        String hexByte = binaryToHex(colorCode1 + colorCode2);
        sb.append(hexByte).append(",");

        count++;
        int finalCount = count;
        if (count % 10 == 0 || count == totalBytes) { // reduce frequency of UI updates
            runOnUiThread(() -> progressBar.setProgress(finalCount));
        }


        if (count % 400 == 0) {
            sb.append("\n");
        }
    }

    // Remove trailing comma
    if (sb.charAt(sb.length() - 1) == ',') {
        sb.setLength(sb.length() - 1);
    }

    sb.append("\n};");

    // Hide progress bar
    runOnUiThread(() -> progressBar.setVisibility(View.GONE));

    return sb.toString();
}
    // Snap color channel to 0 or 255 based on tolerance
    private int normalize(int value) {
        if (value >= 245) return 255;
        if (value <= 10) return 0;
        return value;
    }

    private String binaryToHex(String binary) {
        int decimal = Integer.parseInt(binary, 2);
        return String.format("0x%02X", decimal);
    }




    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ditheredBitmap != null && !ditheredBitmap.isRecycled()) {
            ditheredBitmap.recycle();
        }

    }


}