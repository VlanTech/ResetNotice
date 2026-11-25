package com.vlan.resetnotice;

import android.content.Context;
import android.content.SharedPreferences;

/** Local-only diagnostics; timestamps measure system receiver delivery, not
 * whether the activity or foreground service happens to be alive. */
final class AlarmDiagnostics {
    static final long PROBE_DELAY = 60_000L;
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("alarm_diagnostics", Context.MODE_PRIVATE);
    }
    static void startProbe(Context context, long expected) {
        prefs(context).edit().putLong("probe_expected", expected).putLong("probe_received", 0)
                .putInt("probe_mode", 0).putBoolean("probe_notified", false).commit();
    }
    static void probeMode(Context context, int mode) {
        prefs(context).edit().putInt("probe_mode", mode).commit();
    }
    static void finishProbe(Context context, long received, boolean notified) {
        prefs(context).edit().putLong("probe_received", received).putBoolean("probe_notified", notified).commit();
    }
    static long expected(Context context) { return prefs(context).getLong("probe_expected", 0); }
    static long received(Context context) { return prefs(context).getLong("probe_received", 0); }
    static int mode(Context context) { return prefs(context).getInt("probe_mode", 0); }
    static boolean notified(Context context) { return prefs(context).getBoolean("probe_notified", false); }
    static void alarmReceived(Context context, long expected, long received) {
        prefs(context).edit().putLong("last_expected", expected).putLong("last_received", received).commit();
    }
    static long lastExpected(Context context) { return prefs(context).getLong("last_expected", 0); }
    static long lastReceived(Context context) { return prefs(context).getLong("last_received", 0); }
}
