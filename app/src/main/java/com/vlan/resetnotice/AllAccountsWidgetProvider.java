package com.vlan.resetnotice;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import java.util.List;

/** One real card per widget. Each page is an account ID, not a scroll offset. */
public final class AllAccountsWidgetProvider extends AppWidgetProvider {
    static final String PAGE_ACTION = "com.vlan.resetnotice.WIDGET_PAGE";
    static ComponentName component(Context c) { return new ComponentName(c, AllAccountsWidgetProvider.class); }
    static boolean requestPin(Activity activity) {
        if (Build.VERSION.SDK_INT < 26) return false;
        AppWidgetManager w = AppWidgetManager.getInstance(activity);
        try { return w.isRequestPinAppWidgetSupported() && w.requestPinAppWidget(component(activity), null, null); }
        catch (IllegalStateException | SecurityException ignored) { return false; }
    }
    static List<Integer> orderedAccounts(Context c) {
        return AppPreferences.NEXT_RESET.equals(AppPreferences.order(c)) ?
                AccountOrder.byNextReset(c, System.currentTimeMillis()) : AccountOrder.manual(c);
    }
    static void refreshAll(Context c, AppWidgetManager manager) {
        int[] ids = manager.getAppWidgetIds(component(c));
        if (ids.length == 0) return;
        List<Integer> accounts = orderedAccounts(c);
        for (int id : ids) refresh(c, manager, id, accounts);
    }
    static int selection(Context c, int id, List<Integer> accounts) {
        return WidgetPageMath.selected(accounts, WidgetBindingStore.profile(c, id));
    }
    private static void refresh(Context c, AppWidgetManager manager, int id, List<Integer> accounts) {
        try {
            int selected = selection(c, id, accounts);
            if (selected != WidgetBindingStore.profile(c, id)) {
                if (selected > 0) WidgetBindingStore.bind(c, id, selected);
                else WidgetBindingStore.delete(c, id);
            }
            manager.updateAppWidget(id, QuotaWidgetProvider.renderPagedItem(c, id, selected,
                    accounts.indexOf(selected), accounts.size()));
        } catch (RuntimeException e) {
            Log.w("ResetNoticeWidget", "Account page refresh failed", e);
        }
    }
    static PendingIntent pageIntent(Context c, int id, int direction) {
        Intent intent = new Intent(c, WidgetPageReceiver.class).setAction(PAGE_ACTION)
                .setData(Uri.parse("resetnotice://widget/page/" + id + "/" + direction))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra("direction", direction);
        return PendingIntent.getBroadcast(c, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static void move(Context c, int id, int direction) {
        if (!BootState.unlocked(c) || id <= 0 || (direction != -1 && direction != 1)) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
        if (info == null || !component(c).equals(info.provider)) return;
        List<Integer> accounts = orderedAccounts(c);
        int next = WidgetPageMath.move(accounts, selection(c, id, accounts), direction);
        if (next > 0) WidgetBindingStore.bind(c, id, next);
        refresh(c, manager, id, accounts);
        WidgetRefreshReceiver.schedule(c);
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
        for (int id : ids) WidgetBindingStore.delete(c, id);
        WidgetRefreshReceiver.schedule(c);
    }
    @Override public void onRestored(Context c, int[] oldIds, int[] newIds) {
        WidgetBindingStore.restore(c, oldIds, newIds);
    }
}
