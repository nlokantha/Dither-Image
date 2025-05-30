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


    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.ResolveListener resolveListener;

    private ListView deviceListView;
    private Button scanButton;
    private Button sendButton;
    private TextView statusText;

    private ArrayAdapter<String> deviceAdapter;
    private ArrayList<String> deviceList;
    private HashMap<String, ESP32Device> deviceMap;
    private ESP32Device selectedDevice;

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
        statusText = findViewById(R.id.statusText);

        progressBar = findViewById(R.id.progressBar);

        selectImageBtn.setOnClickListener(v -> openGallery());
        saveImageBtn.setOnClickListener(v -> saveImage());
        saveHexBtn.setOnClickListener(v -> saveHexFile());

        saveHexBtn.setVisibility(View.GONE);

        initViews();
        initNSD();
        setupListeners();
    }

    private void initViews() {
        deviceListView = findViewById(R.id.deviceListView);
        scanButton = findViewById(R.id.buttonScan);
        sendButton = findViewById(R.id.buttonSend2);


        deviceList = new ArrayList<>();
        deviceMap = new HashMap<>();
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        deviceListView.setAdapter(deviceAdapter);

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
                    Bitmap contrasted = adjustContrast(rotatedBitmap, 1.0f);
                    Bitmap saturatedImage = increaseSaturation(contrasted, 1.8f);



//                    Bitmap shadowBoosted = adjustShadows(saturatedImage, 0.3f);
//

                    ditheredBitmap = applyFloydSteinbergDithering(saturatedImage);
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

    private Bitmap adjustWhiteBalance(Bitmap src, float redGain, float greenGain, float blueGain) {
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = src.getPixel(x, y);

                int r = clamp((int) (Color.red(pixel) * redGain));
                int g = clamp((int) (Color.green(pixel) * greenGain));
                int b = clamp((int) (Color.blue(pixel) * blueGain));

                int newPixel = Color.rgb(r, g, b);
                result.setPixel(x, y, newPixel);
            }
        }

        return result;
    }

    private Bitmap adjustShadows(Bitmap src, float shadowBoost) {
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = src.getPixel(x, y);

                int r = adjustShadowChannel(Color.red(pixel), shadowBoost);
                int g = adjustShadowChannel(Color.green(pixel), shadowBoost);
                int b = adjustShadowChannel(Color.blue(pixel), shadowBoost);

                result.setPixel(x, y, Color.rgb(r, g, b));
            }
        }

        return result;
    }

    private int adjustShadowChannel(int value, float boost) {
        // Only apply boost to darker values (e.g., below 128)
        if (value < 128) {
            float factor = 1 + boost * (1 - (value / 128f));  // stronger boost on darker values
            value = (int) (value * factor);
        }
        return clamp(value);
    }

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

    private Bitmap adjustBlacks(Bitmap src, float blackLevel) {
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = src.getPixel(x, y);

                int r = adjustBlackChannel(Color.red(pixel), blackLevel);
                int g = adjustBlackChannel(Color.green(pixel), blackLevel);
                int b = adjustBlackChannel(Color.blue(pixel), blackLevel);

                result.setPixel(x, y, Color.rgb(r, g, b));
            }
        }

        return result;
    }

    private int adjustBlackChannel(int value, float blackLevel) {
        // blackLevel: 0.0 = no change, positive = deeper blacks
        float adjusted = value * (1 - blackLevel);
        return clamp((int) adjusted);
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

                diffuseError(pixels, x + 1, y, width, height, errR, errG, errB, 7f / 16);
                diffuseError(pixels, x - 1, y + 1, width, height, errR, errG, errB, 3f / 16);
                diffuseError(pixels, x, y + 1, width, height, errR, errG, errB, 5f / 16);
                diffuseError(pixels, x + 1, y + 1, width, height, errR, errG, errB, 1f / 16);

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

    private void initNSD() {
        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                runOnUiThread(() -> statusText.setText("Discovery failed"));
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop discovery failed: Error code:" + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                Log.d(TAG, "Service discovery started");
                runOnUiThread(() -> statusText.setText("Scanning for ESP32 devices..."));
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
                runOnUiThread(() -> statusText.setText("Scan completed"));
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.d(TAG, "Service discovery success: " + service);
                if (service.getServiceType().equals(SERVICE_TYPE)) {
                    nsdManager.resolveService(service, createResolveListener());
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e(TAG, "Service lost: " + service);
                String serviceName = service.getServiceName();
                runOnUiThread(() -> {
                    deviceList.remove(serviceName);
                    deviceMap.remove(serviceName);
                    deviceAdapter.notifyDataSetChanged();
                });
            }
        };
    }

    private NsdManager.ResolveListener createResolveListener() {
        return new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed: " + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                Log.i(TAG, "Resolve Succeeded: " + serviceInfo);

                String serviceName = serviceInfo.getServiceName();
                InetAddress host = serviceInfo.getHost();
                int port = serviceInfo.getPort();

                // Check if it's an ESP32 device
//                if (serviceName.contains("ESP32") || serviceName.contains("esp32")) {
                    ESP32Device device = new ESP32Device(serviceName, host.getHostAddress(), port);

                    runOnUiThread(() -> {
                        if (!deviceMap.containsKey(serviceName)) {
                            deviceList.add(serviceName + " (" + host.getHostAddress() + ":" + port + ")");
                            deviceMap.put(serviceName, device);
                            deviceAdapter.notifyDataSetChanged();
                        }
                    });
                }
//            }
        };
    }

    private void setupListeners() {
        scanButton.setOnClickListener(v -> startDiscovery());

        sendButton.setOnClickListener(v -> {
            if (selectedDevice != null) {
                sendDataToESP32("A");
            }
        });

        deviceListView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = deviceList.get(position);
            String deviceName = selectedItem.split(" \\(")[0];
            selectedDevice = deviceMap.get(deviceName);

            sendButton.setEnabled(true);
            statusText.setText("Selected: " + deviceName);
            Toast.makeText(this, "Selected: " + deviceName, Toast.LENGTH_SHORT).show();
        });
    }

    private void startDiscovery() {
        deviceList.clear();
        deviceMap.clear();
        deviceAdapter.notifyDataSetChanged();
        selectedDevice = null;
        sendButton.setEnabled(false);

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    private void sendDataToESP32(String data) {
        if (selectedDevice == null) return;

        new SendDataTask().execute(selectedDevice, data);
    }

    private class SendDataTask extends AsyncTask<Object, Void, String> {
        @Override
        protected void onPreExecute() {
            statusText.setText("Sending data...");
        }

        @Override
        protected String doInBackground(Object... params) {
            ESP32Device device = (ESP32Device) params[0];
            String data = (String) params[1];

            try {
                URL url = new URL("http://" + device.getIpAddress() + ":" + device.getPort() + "/");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                // Send data
                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(data);
                writer.flush();
                writer.close();

                // Read response
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                reader.close();

                return "Success: " + response.toString();

            } catch (IOException e) {
                Log.e(TAG, "Error sending data", e);
                return "Error: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            statusText.setText(result);
            if (result.startsWith("Success")) {
                Toast.makeText(MainActivity.this, "Data sent successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Failed to send data", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ditheredBitmap != null && !ditheredBitmap.isRecycled()) {
            ditheredBitmap.recycle();
        }
        if (nsdManager != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Exception e) {
                Log.e(TAG, "Error stopping discovery", e);
            }
        }
    }

    // ESP32Device class
    private static class ESP32Device {
        private String name;
        private String ipAddress;
        private int port;

        public ESP32Device(String name, String ipAddress, int port) {
            this.name = name;
            this.ipAddress = ipAddress;
            this.port = port;
        }

        public String getName() { return name; }
        public String getIpAddress() { return ipAddress; }
        public int getPort() { return port; }
    }
}