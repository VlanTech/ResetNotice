package com.vlan.resetnotice;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.util.List;

/** Native Android collection widget: vertically flick to the next account. */
public final class AllAccountsWidgetProvider extends AppWidgetProvider {
    static ComponentName component(Context c) { return new ComponentName(c, AllAccountsWidgetProvider.class); }
    static boolean requestPin(Activity activity) {
        if (Build.VERSION.SDK_INT < 26) return false;
        AppWidgetManager w = AppWidgetManager.getInstance(activity);
        try { return w.isRequestPinAppWidgetSupported() && w.requestPinAppWidget(component(activity), null, null); }
        catch (IllegalStateException | SecurityException ignored) { return false; }
    }
    static void refreshAll(Context c, AppWidgetManager manager) {
        int[] ids = manager.getAppWidgetIds(component(c));
        if (ids.length == 0) return;
        // Android 12+ can deliver small collections directly. Older hosts,
        // and large collections where bitmap payloads could grow too large,
        // retain the streaming RemoteViewsService adapter.
        for (int id : ids) install(c, manager, id);
        if (Build.VERSION.SDK_INT < 31 || orderedAccounts(c).size() > 8)
            manager.notifyAppWidgetViewDataChanged(ids, R.id.all_accounts_stack);
    }
    static List<Integer> orderedAccounts(Context c) {
        return AppPreferences.NEXT_RESET.equals(AppPreferences.order(c)) ?
                AccountOrder.byNextReset(c, System.currentTimeMillis()) : AccountOrder.manual(c);
    }
    private static void install(Context c, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_stack);
        Context localized = AppPreferences.localized(c);
        views.setTextViewText(R.id.stack_empty, localized.getString(R.string.widget_no_accounts));
        views.setTextColor(R.id.stack_empty, localized.getColor(R.color.accent));
        int color = localized.getColor(R.color.card);
        views.setInt(R.id.stack_empty, "setBackgroundColor", Color.argb(
                WidgetMath.glassAlpha(AppPreferences.widgetTransparency(c)), Color.red(color), Color.green(color), Color.blue(color)));
        List<Integer> accounts = orderedAccounts(c);
        if (Build.VERSION.SDK_INT >= 31 && accounts.size() <= 8) {
            RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder()
                    .setHasStableIds(true).setViewTypeCount(3);
            for (int profile : accounts) items.addItem(profile, QuotaWidgetProvider.renderStackItem(c, profile));
            views.setRemoteAdapter(R.id.all_accounts_stack, items.build());
        } else {
            Intent service = new Intent(c, WidgetStackService.class)
                    .setData(Uri.parse("resetnotice://widget/stack/" + id))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            views.setRemoteAdapter(R.id.all_accounts_stack, service);
        }
        views.setEmptyView(R.id.all_accounts_stack, R.id.stack_empty);
        Intent open = new Intent(c, MainActivity.class).setData(Uri.parse("resetnotice://widget/stack/open/" + id))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        views.setPendingIntentTemplate(R.id.all_accounts_stack, PendingIntent.getActivity(c, id, open, flags));
        views.setOnClickPendingIntent(R.id.stack_empty, PendingIntent.getActivity(c, id,
                new Intent(c, MainActivity.class).setData(Uri.parse("resetnotice://widget/stack/empty/" + id)),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.updateAppWidget(id, views);
    }
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        if (!BootState.unlocked(c)) return;
        for (int id : ids) install(c, manager, id);
        if (Build.VERSION.SDK_INT < 31 || orderedAccounts(c).size() > 8)
            manager.notifyAppWidgetViewDataChanged(ids, R.id.all_accounts_stack);
        WidgetRefreshReceiver.schedule(c);
    }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager manager, int id, Bundle options) {
        if (!BootState.unlocked(c)) return;
        install(c, manager, id); WidgetRefreshReceiver.schedule(c);
    }
    @Override public void onDeleted(Context c, int[] ids) { WidgetRefreshReceiver.schedule(c); }
}
