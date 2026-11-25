package com.vlan.resetnotice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** One explicitly requested reminder. It does not start a timer service,
 * repeat itself, or open an activity. */
public final class ProbeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AlarmEngine.PROBE_ACTION.equals(intent.getAction())) return;
        long expected = intent.getLongExtra("expected", 0L);
        if (expected <= 0 || expected != AlarmDiagnostics.expected(context) ||
                AlarmDiagnostics.received(context) != 0) return;
        long now = System.currentTimeMillis();
        if (now < expected) {
            AlarmEngine.armProbe(context, expected);
            return;
        }
        boolean notified = AlarmEngine.sendProbeNotification(context, expected, now);
        AlarmDiagnostics.finishProbe(context, now, notified);
    }
}
