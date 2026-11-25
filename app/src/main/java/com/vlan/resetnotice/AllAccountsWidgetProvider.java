package com.vlan.resetnotice;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.util.List;
import android.util.Log;
import android.util.SizeF;
import java.util.ArrayList;

/** Non-overlapping, full-height vertical cards. Native ListView owns
 * scrolling and edge fades; the provider never runs a launcher animation. */
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
        List<Integer> accounts = orderedAccounts(c);
        for (int id : ids) refresh(c, manager, id, accounts);
    }
    static List<Integer> orderedAccounts(Context c) {
        return AppPreferences.NEXT_RESET.equals(AppPreferences.order(c)) ?
                AccountOrder.byNextReset(c, System.currentTimeMillis()) : AccountOrder.manual(c);
    }
    static int rowHeight(Context c, AppWidgetManager manager, int id) {
        Bundle options = manager.getAppWidgetOptions(id);
        int min = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        int max = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        boolean landscape = c.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (Build.VERSION.SDK_INT >= 31) {
            ArrayList<SizeF> sizes = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if (sizes != null && !sizes.isEmpty()) {
                int smallest = Integer.MAX_VALUE, largest = 0;
                for (SizeF size : sizes) if (size != null && size.getHeight() > 0) {
                    int h = Math.round(size.getHeight());
                    smallest = Math.min(smallest, h); largest = Math.max(largest, h);
                }
                if (largest > 0) { min = smallest; max = largest; }
            }
        }
        return WidgetDeckMath.rowHeight(min, max, landscape);
    }
    private static void refresh(Context c, AppWidgetManager manager, int id, List<Integer> accounts) {
        try {
            int height = rowHeight(c, manager, id);
            boolean direct = WidgetDeckMath.direct(Build.VERSION.SDK_INT, accounts.size());
            int signature = WidgetDeckMath.signature(direct, height);
            boolean full = !WidgetDeckState.installed(c, id, signature);
            RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget_account_list);
            styleEmpty(c, views, accounts.isEmpty());
            if (direct) {
                // Stable collection item IDs, and only a partial update after
                // the first complete install, allow the host to retain scrolling.
                RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder()
                        .setHasStableIds(true).setViewTypeCount(3);
                for (int profile : accounts) items.addItem(profile, QuotaWidgetProvider.renderStackItem(c, profile, height));
                views.setRemoteAdapter(R.id.all_accounts_list, items.build());
            } else if (full) {
                Intent service = new Intent(c, WidgetStackService.class)
                        .setData(Uri.parse("resetnotice://widget/list/" + id))
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                views.setRemoteAdapter(R.id.all_accounts_list, service);
            }
            if (full) {
                views.setEmptyView(R.id.all_accounts_list, R.id.deck_empty);
                Intent open = new Intent(c, MainActivity.class).setData(Uri.parse("resetnotice://widget/list/open/" + id))
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                views.setPendingIntentTemplate(R.id.all_accounts_list, PendingIntent.getActivity(c, id, open, flags));
                views.setOnClickPendingIntent(R.id.deck_empty, PendingIntent.getActivity(c, id,
                        new Intent(c, MainActivity.class).setData(Uri.parse("resetnotice://widget/list/empty/" + id)),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                manager.updateAppWidget(id, views);
                WidgetDeckState.mark(c, id, signature);
            } else {
                // The array overload persists incremental properties in the
                // system's cached RemoteViews on API 17+, including reattach.
                manager.partiallyUpdateAppWidget(new int[]{id}, views);
            }
            if (!direct) manager.notifyAppWidgetViewDataChanged(id, R.id.all_accounts_list);
        } catch (RuntimeException e) {
            WidgetDeckState.clear(c, id);
            Log.w("ResetNoticeWidget", "Vertical account deck refresh failed", e);
        }
    }
    private static void styleEmpty(Context c, RemoteViews views, boolean empty) {
        Context localized = AppPreferences.localized(c);
        views.setTextViewText(R.id.deck_empty, localized.getString(empty ? R.string.widget_no_accounts : R.string.widget_deck_loading));
        views.setTextColor(R.id.deck_empty, localized.getColor(R.color.accent));
        int color = localized.getColor(R.color.card);
        views.setInt(R.id.deck_empty, "setBackgroundColor", Color.argb(
                WidgetMath.glassAlpha(AppPreferences.widgetTransparency(c)), Color.red(color), Color.green(color), Color.blue(color)));
    }
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        if (!BootState.unlocked(c)) return;
        List<Integer> accounts = orderedAccounts(c);
        for (int id : ids) refresh(c, manager, id, accounts);
        WidgetRefreshReceiver.schedule(c);
    }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager manager, int id, Bundle options) {
        if (!BootState.unlocked(c)) return;
        refresh(c, manager, id, orderedAccounts(c)); WidgetRefreshReceiver.schedule(c);
    }
    @Override public void onDeleted(Context c, int[] ids) {
        for (int id : ids) WidgetDeckState.clear(c, id);
        WidgetRefreshReceiver.schedule(c);
    }
    @Override public void onRestored(Context c, int[] oldIds, int[] newIds) {
        for (int id : oldIds) WidgetDeckState.clear(c, id);
        for (int id : newIds) WidgetDeckState.clear(c, id);
    }
}
