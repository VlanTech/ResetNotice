package com.vlan.resetnotice;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Fixed, independent interaction surface with a theme-tinted translucent
 * gradient. Content scrolls behind the fading edge; controls never move. */
final class FrostedTopBar {
    private static int dp(Activity a, float n) {
        return Math.round(n * a.getResources().getDisplayMetrics().density);
    }
    private static int alpha(int color, int a) {
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    static void install(Activity activity, ScrollView scroll, LinearLayout content,
                        View header, int page, int initialHeightDp) {
        FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(page);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout overlay = new LinearLayout(activity);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setClipChildren(false);
        FrameLayout surface = new FrameLayout(activity);
        surface.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{alpha(page, 254), alpha(page, 248), alpha(page, 232)}));
        surface.setClickable(true); // The header, not the list underneath, owns this touch region.
        surface.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels, dp(activity, 600));
        header.setPadding(dp(activity, 18), dp(activity, 10), dp(activity, 18), dp(activity, 10));
        surface.addView(header, new FrameLayout.LayoutParams(width, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        overlay.addView(surface, new LinearLayout.LayoutParams(-1, -2));
        View fade = new View(activity);
        fade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{alpha(page, 232), alpha(page, 140), alpha(page, 0)}));
        fade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        overlay.addView(fade, new LinearLayout.LayoutParams(-1, dp(activity, 20)));
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
        // The first card begins below the glass. During scrolling, all list
        // content is allowed to slide underneath it. Measure rather than
        // assume a fixed header height for translated text or large fonts.
        content.setPadding(content.getPaddingLeft(), dp(activity, initialHeightDp + 16),
                content.getPaddingRight(), content.getPaddingBottom());
        surface.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int inset = b - t + dp(activity, 16);
            if (content.getPaddingTop() != inset)
                content.setPadding(content.getPaddingLeft(), inset, content.getPaddingRight(), content.getPaddingBottom());
        });
        activity.setContentView(root);
    }
}
