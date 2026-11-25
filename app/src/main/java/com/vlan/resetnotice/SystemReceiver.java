package com.vlan.resetnotice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class SystemReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) AlarmEngine.cancelLegacyProbe(context);
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED".equals(action)) {
            AlarmEngine.rescheduleAll(context);
            TimerService.refresh(context);
        }
    }
}
