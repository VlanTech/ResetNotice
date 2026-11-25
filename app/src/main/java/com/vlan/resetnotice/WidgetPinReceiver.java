package com.vlan.resetnotice;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class WidgetPinReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!QuotaWidgetProvider.PIN_ACTION.equals(intent.getAction())) return;
        int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        int profile = intent.getIntExtra(QuotaWidgetProvider.PROFILE_EXTRA, WidgetBindingStore.NONE);
        if (!QuotaWidgetProvider.validWidget(context, id)) return;
        if (ReminderStore.profile(context, profile) != null) WidgetBindingStore.bind(context, id, profile);
        QuotaWidgetProvider.updateOne(context, AppWidgetManager.getInstance(context), id);
        WidgetRefreshReceiver.schedule(context);
    }
}
