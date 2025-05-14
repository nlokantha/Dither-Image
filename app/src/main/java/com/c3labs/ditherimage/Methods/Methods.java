package com.c3labs.ditherimage.Methods;

import android.graphics.Bitmap;
import android.graphics.Color;

public class Methods {
    // Color palette with consistent mapping to your C# code
    private final int[][] colorPalette = {
            {255, 255, 255}, // white    -> 0001
            {0, 0, 0},        // black    -> 0000
            {255, 0, 0},      // red      -> 0011
            {255, 255, 0},    // yellow   -> 0010
            {0, 0, 255},      // blue     -> 0101
            {0, 255, 0}      // green    -> 0110
    };

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
}
