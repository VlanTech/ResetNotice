package com.vlan.resetnotice;

import android.content.Context;
import android.widget.ImageView;
import android.widget.RemoteViews;

/** Background tint only. Wallpaper backdrop blur belongs to the launcher. */
final class WidgetGlass {
    private static int plain(Context c) {
        String theme = AppPreferences.theme(c);
        if (AppPreferences.LIGHT.equals(theme)) return R.drawable.widget_bg_light;
        if (AppPreferences.DARK.equals(theme)) return R.drawable.widget_bg_dark;
        return R.drawable.widget_bg_auto;
    }
    static void applyRemote(RemoteViews views, Context context) {
        views.setImageViewResource(R.id.widget_glass, plain(context));
        views.setInt(R.id.widget_glass, "setImageAlpha", WidgetMath.glassAlpha(AppPreferences.widgetTransparency(context)));
    }
    static void preview(ImageView image, Context c) {
        image.setImageResource(plain(c));
        image.setImageAlpha(WidgetMath.glassAlpha(AppPreferences.widgetTransparency(c)));
    }
}
