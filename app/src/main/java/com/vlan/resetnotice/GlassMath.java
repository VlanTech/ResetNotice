package com.vlan.resetnotice;

/** Independent blur radius and opacity. This filters the app's own
 * decorative material; the launcher wallpaper is never read. */
final class GlassMath {
    static int blurRadius(int strength) {
        int s = Math.max(0, Math.min(100, strength));
        return s == 0 ? 0 : Math.max(1, Math.round(18f * s / 100f));
    }
    static int bucket(int strength) {
        int s = Math.max(0, Math.min(100, strength));
        return s == 0 ? 0 : Math.max(5, Math.round(s / 5f) * 5);
    }
    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >>> 16) & 255) * (1-t) + ((b >>> 16) & 255) * t);
        int g = Math.round(((a >>> 8) & 255) * (1-t) + ((b >>> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1-t) + (b & 255) * t);
        return 0xff000000 | (r << 16) | (g << 8) | bl;
    }
    /** Two separable box-filter passes approximate a Gaussian diffusion,
     * with a clamped border and no dependence on layout transparency. */
    static int[] blur(int[] source, int width, int height, int radius) {
        if (source.length != width * height || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid texture");
        if (radius <= 0) return source.clone();
        int[] horizontal = new int[source.length], vertical = new int[source.length];
        int[] input = source;
        for (int i = 0; i < 2; i++) {
            pass(input, horizontal, width, height, radius, true);
            pass(horizontal, vertical, width, height, radius, false);
            input = vertical;
        }
        return vertical;
    }
    private static void pass(int[] in, int[] out, int width, int height, int radius, boolean horizontal) {
        int lines = horizontal ? height : width, length = horizontal ? width : height;
        int count = radius * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int r=0,g=0,b=0;
            for (int k=-radius;k<=radius;k++) {
                int value = pixel(in,width,length,line,k,horizontal);
                r += (value >>> 16) & 255; g += (value >>> 8) & 255; b += value & 255;
            }
            for (int i=0;i<length;i++) {
                int index = horizontal ? line * width + i : i * width + line;
                out[index] = 0xff000000 | (r/count << 16) | (g/count << 8) | b/count;
                int drop = pixel(in,width,length,line,i-radius,horizontal);
                int add = pixel(in,width,length,line,i+radius+1,horizontal);
                r += ((add >>> 16)&255) - ((drop >>> 16)&255);
                g += ((add >>> 8)&255) - ((drop >>> 8)&255);
                b += (add&255) - (drop&255);
            }
        }
    }
    private static int pixel(int[] pixels,int width,int length,int line,int position,boolean horizontal) {
        int p = Math.max(0,Math.min(length-1,position));
        return pixels[horizontal ? line*width+p : p*width+line];
    }
}
