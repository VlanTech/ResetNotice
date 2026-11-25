package com.vlan.resetnotice;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent installation marker prevents repeated adapter recreation and
 * guarantees a full migration of existing diagonal-stack widget instances. */
final class WidgetDeckState {
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("widget_deck_layout", Context.MODE_PRIVATE);
    }
    static boolean installed(Context c, int id, int signature) {
        return prefs(c).getInt("widget_" + id, 0) == signature;
    }
    static void mark(Context c, int id, int signature) {
        prefs(c).edit().putInt("widget_" + id, signature).commit();
    }
    static void clear(Context c, int id) { prefs(c).edit().remove("widget_" + id).commit(); }
}
