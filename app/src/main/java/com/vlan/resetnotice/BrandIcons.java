package com.vlan.resetnotice;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

/** Compact theme-specific logo atlas; tiles are decoded only when needed. */
final class BrandIcons {
    private static final String[] BRANDS = {"openai", "grok", "claude", "gemini", "deepseek", "qwen",
            "perplexity", "copilot", "kimi", "mistral", "doubao", "zhipu",
            "wenxin", "minimax", "meta", "poe", "ollama", "huggingface"};
    private static final int TILE = 96;
    private static final Bitmap[] ATLASES = new Bitmap[2];
    private static final Bitmap[][] CACHE = new Bitmap[2][BRANDS.length];

    static void show(ImageView logo, String name, int themeTint) {
        String brand = BrandMatcher.match(name);
        logo.clearColorFilter();
        int index = -1;
        for (int i = 0; i < BRANDS.length; i++) if (BRANDS[i].equals(brand)) { index = i; break; }
        if (index >= 0) {
            int theme = (logo.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES ? 1 : 0;
            if (ATLASES[theme] == null) {
                BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false;
                ATLASES[theme] = BitmapFactory.decodeResource(logo.getResources(), R.drawable.brand_logos, options);
            }
            Bitmap sheet = ATLASES[theme];
            if (sheet != null && sheet.getWidth() == TILE * 6 && sheet.getHeight() == TILE * 3) {
                if (CACHE[theme][index] == null) CACHE[theme][index] =
                        Bitmap.createBitmap(sheet, (index % 6) * TILE, (index / 6) * TILE, TILE, TILE);
                logo.setImageBitmap(CACHE[theme][index]);
                logo.setContentDescription(name);
                return;
            }
        }
        logo.setImageResource(R.drawable.ic_spark);
        logo.setColorFilter(themeTint);
        logo.setContentDescription(name);
    }
}
