package com.vlan.resetnotice;

import android.content.Context;
import android.content.SharedPreferences;

/** Per-widget binding to a stable, non-recycled account ID. */
final class WidgetBindingStore {
    static final int NONE = -1;
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("account_widgets_v1", Context.MODE_PRIVATE);
    }
    private static String key(int id) { return "widget_" + id; }
    static int profile(Context c, int id) {
        return id > 0 ? prefs(c).getInt(key(id), NONE) : NONE;
    }
    static boolean bind(Context c, int id, int profile) {
        return id > 0 && profile > 0 && prefs(c).edit().putInt(key(id), profile).commit();
    }
    static void delete(Context c, int id) {
        if (id > 0) prefs(c).edit().remove(key(id)).commit();
    }
    static void restore(Context c, int[] oldIds, int[] newIds) {
        int n = Math.min(oldIds.length, newIds.length);
        int[] profiles = new int[n];
        SharedPreferences.Editor e = prefs(c).edit();
        for (int i = 0; i < n; i++) {
            profiles[i] = profile(c, oldIds[i]);
            e.remove(key(oldIds[i]));
        }
        for (int i = 0; i < n; i++)
            if (newIds[i] > 0 && profiles[i] > 0) e.putInt(key(newIds[i]), profiles[i]);
        e.commit();
    }
}
