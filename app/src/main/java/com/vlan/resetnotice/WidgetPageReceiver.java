package com.vlan.resetnotice;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Receives only explicit, immutable page-button intents from our widget. */
public final class WidgetPageReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!AllAccountsWidgetProvider.PAGE_ACTION.equals(intent.getAction())) return;
        AllAccountsWidgetProvider.move(context,
                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1),
                intent.getIntExtra("direction", 0));
    }
}
