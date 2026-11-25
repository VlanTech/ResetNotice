package com.vlan.resetnotice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AlarmEngine.deliver(context, intent);
        TimerService.refresh(context);
    }
}
