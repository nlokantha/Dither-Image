package com.c3labs.ditherimage;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE = 1;
    private static final String TAG = "tag";
    private ImageView originalImageView, ditheredImageView;
    private Button selectImageBtn, saveImageBtn, saveHexBtn;
    private Bitmap ditheredBitmap;

    // Color palette with consistent mapping to your C# code
    private final int[][] colorPalette = {
            {255, 255, 255}, // white    -> 0001
            {0, 0, 0},        // black    -> 0000
            {255, 0, 0},      // red      -> 0011
            {255, 255, 0},    // yellow   -> 0010
            {0, 0, 255},      // blue     -> 0101
            {0, 255, 0}      // green    -> 0110
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

        selectImageBtn.setOnClickListener(v -> openGallery());
        saveImageBtn.setOnClickListener(v -> saveImage());
        saveHexBtn.setOnClickListener(v -> saveHexFile());
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
                originalImageView.setImageBitmap(originalBitmap);

                new Thread(() -> {
                    ditheredBitmap = applyFloydSteinbergDithering(originalBitmap);
                    runOnUiThread(() -> {
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

    private Bitmap applyFloydSteinbergDithering(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();
        Bitmap ditheredBitmap = original.copy(Bitmap.Config.ARGB_8888, true);

        int[] pixels = new int[width * height];
        ditheredBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int oldPixel = pixels[index];

                int oldR = Color.red(oldPixel);
                int oldG = Color.green(oldPixel);
                int oldB = Color.blue(oldPixel);

                int[] closestColor = findClosestColor(oldR, oldG, oldB);
                int newR = closestColor[0];
                int newG = closestColor[1];
                int newB = closestColor[2];

                pixels[index] = Color.rgb(newR, newG, newB);

                int errR = oldR - newR;
                int errG = oldG - newG;
                int errB = oldB - newB;

                diffuseError(pixels, x + 1, y, width, height, errR, errG, errB, 7.0f / 16);
                diffuseError(pixels, x - 1, y + 1, width, height, errR, errG, errB, 3.0f / 16);
                diffuseError(pixels, x, y + 1, width, height, errR, errG, errB, 5.0f / 16);
                diffuseError(pixels, x + 1, y + 1, width, height, errR, errG, errB, 1.0f / 16);

//                Log.d("Dither", "Pixel (" + x + "," + y + ") " +
//                        "Old: (" + oldR + "," + oldG + "," + oldB + ") → " +
//                        "New: (" + newR + "," + newG + "," + newB + ")");
            }
        }

        ditheredBitmap.setPixels(pixels, 0, width, 0, 0, width, height);

        return ditheredBitmap;
    }

    private int[] findClosestColor(int r, int g, int b) {
        int minDist = Integer.MAX_VALUE;
        int[] bestMatch = colorPalette[0];

        for (int[] color : colorPalette) {
            int dr = color[0] - r;
            int dg = color[1] - g;
            int db = color[2] - b;
            int dist = dr * dr + dg * dg + db * db;

            if (dist < minDist) {
                minDist = dist;
                bestMatch = color;
            }
        }
        return bestMatch;
    }

    private void diffuseError(int[] pixels, int x, int y, int width, int height,
                              int errR, int errG, int errB, float factor) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;

        int index = y * width + x;
        int pixel = pixels[index];

        int newR = clamp(Color.red(pixel) + (int)(errR * factor));
        int newG = clamp(Color.green(pixel) + (int)(errG * factor));
        int newB = clamp(Color.blue(pixel) + (int)(errB * factor));

        pixels[index] = Color.rgb(newR, newG, newB);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
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

//    private String convertToHex(Bitmap bitmap) {
//        StringBuilder sb = new StringBuilder();
//        String variableName = "dithered_image_" +
//                new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
//
//        sb.append("const unsigned char ").append(variableName).append("[] = {\n");
//
//        int width = bitmap.getWidth();
//        int height = bitmap.getHeight();
//        int[] pixels = new int[width * height];
//        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
//
//        int count = 0;
//        for (int y = 0; y < height; y++) {
//            for (int x = 0; x < width; x += 2) { // Process 2 pixels at a time
//                if (count > 0 && count % 16 == 0) {
//                    sb.append("\n");
//                }
//
//                // Get first pixel
//                Color pixel1 = Color.valueOf(pixels[y * width + x]);
//                String colorCode1 = getColorCode(pixel1);
//
//                // Get second pixel (or default to black if odd width)
//                String colorCode2 = "0000";
//                if (x + 1 < width) {
//                    Color pixel2 = Color.valueOf(pixels[y * width + x + 1]);
//                    colorCode2 = getColorCode(pixel2);
//                }
//
//                // Combine two 4-bit color codes into one byte
//                String combined = colorCode1 + colorCode2;
//                String hexByte = binaryToHex(combined);
//                sb.append(hexByte).append(", ");
//                count++;
//            }
//        }
//
//        // Remove trailing comma and space
//        if (sb.length() > 2) {
//            sb.setLength(sb.length() - 2);
//        }
//
//        sb.append("\n};");
//        return sb.toString();
//    }
//
//    private String getColorCode(Color color) {
//        int r = (int)(color.red() * 255);
//        int g = (int)(color.green() * 255);
//        int b = (int)(color.blue() * 255);
//
//        // Match with the same palette used in C# code
//        if (r == 255 && g == 255 && b == 255) return "0001"; // white
//        if (r == 0 && g == 0 && b == 0) return "0000";       // black
//        if (r == 255 && g == 0 && b == 0) return "0011";     // red
//        if (r == 0 && g == 255 && b == 0) return "0110";     // green
//        if (r == 0 && g == 0 && b == 255) return "0101";     // blue
//        if (r == 255 && g == 255 && b == 0) return "0010";   // yellow
//
//        // Fallback to black if no match (shouldn't happen after dithering)
//        throw new IllegalArgumentException(
//                "Unrecognized color: R=" + r + ", G=" + g + ", B=" + b
//        );
//    }
//
//    private String binaryToHex(String binary) {
//        int decimal = Integer.parseInt(binary, 2);
////        return "0x" + Integer.toString(decimal, 16).toUpperCase();
//        return String.format("0x%02X", decimal);
//    }
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
        if (count % 400 == 0) {
            sb.append("\n");
        }
    }

    // Remove trailing comma
    if (sb.charAt(sb.length() - 1) == ',') {
        sb.setLength(sb.length() - 1);
    }

    sb.append("\n};");
    return sb.toString();
}

    // Match colors with tolerance like the C# version
    private String getColorCodeApprox(Color color) {
        int r = (int)(color.red() * 255);
        int g = (int)(color.green() * 255);
        int b = (int)(color.blue() * 255);

        r = normalize(r);
        g = normalize(g);
        b = normalize(b);

        if (r == 255 && g == 255 && b == 255) return "0001"; // white
        if (r == 0 && g == 0 && b == 0) return "0000";       // black
        if (r == 255 && g == 0 && b == 0) return "0011";     // red
        if (r == 0 && g == 255 && b == 0) return "0110";     // green
        if (r == 0 && g == 0 && b == 255) return "0101";     // blue
        if (r == 255 && g == 255 && b == 0) return "0010";   // yellow

        throw new IllegalArgumentException("Unrecognized color: R=" + r + ", G=" + g + ", B=" + b);
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