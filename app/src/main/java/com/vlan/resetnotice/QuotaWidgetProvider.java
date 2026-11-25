package com.vlan.resetnotice;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import java.util.UUID;

/** Local account widgets. The launcher owns visible chronometer ticking;
 * the app only rebinds real targets at edits, rollovers and system refreshes. */
public final class QuotaWidgetProvider extends AppWidgetProvider {
    private static final String TAG = "ResetNoticeWidget";
    static final String PIN_ACTION = "com.vlan.resetnotice.WIDGET_PINNED";
    static final String PROFILE_EXTRA = "profile_id";

    static ComponentName component(Context c) {
        return new ComponentName(c, QuotaWidgetProvider.class);
    }
    static boolean validWidget(Context c, int id) {
        if (id <= 0) return false;
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id);
        return info != null && component(c).equals(info.provider);
    }

    static boolean requestPin(Activity activity, int profile) {
        if (Build.VERSION.SDK_INT < 26 || ReminderStore.profile(activity, profile) == null) return false;
        AppWidgetManager manager = AppWidgetManager.getInstance(activity);
        if (!manager.isRequestPinAppWidgetSupported()) return false;
        // Explicit, non-exported one-shot receiver. The launcher must be
        // able to fill the AppWidget ID after the user accepts the request.
        Intent callback = new Intent(activity, WidgetPinReceiver.class).setAction(PIN_ACTION)
                .setData(Uri.parse("resetnotice://pin/" + UUID.randomUUID()))
                .putExtra(PROFILE_EXTRA, profile);
        int flags = PendingIntent.FLAG_ONE_SHOT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        PendingIntent success = PendingIntent.getBroadcast(activity, 0, callback, flags);
        try {
            if (manager.requestPinAppWidget(component(activity), null, success)) return true;
        } catch (IllegalStateException | SecurityException e) {
            Log.w(TAG, "Launcher cannot pin widgets", e);
        }
        success.cancel();
        return false;
    }

    static void updateAll(Context context) {
        if (!BootState.unlocked(context)) return;
        try {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(component(context));
            for (int id : ids) updateOne(context, manager, id);
        } catch (RuntimeException e) { Log.w(TAG, "Widget refresh unavailable", e); }
    }

    static void updateOne(Context context, AppWidgetManager manager, int id) {
        if (!BootState.unlocked(context)) return;
        try {
            manager.updateAppWidget(id, render(context, id));
        } catch (RuntimeException e) { Log.w(TAG, "Widget refresh failed", e); }
    }

    private static RemoteViews render(Context context, int id) {
        Context c = AppPreferences.localized(context);
        String theme = AppPreferences.theme(context);
        int layout = AppPreferences.LIGHT.equals(theme) ? R.layout.widget_account_light :
                AppPreferences.DARK.equals(theme) ? R.layout.widget_account_dark : R.layout.widget_account;
        RemoteViews v = new RemoteViews(context.getPackageName(), layout);
        int profileId = WidgetBindingStore.profile(context, id);
        ReminderStore.Profile profile = ReminderStore.profile(context, profileId);
        v.setViewVisibility(R.id.widget_short, View.GONE);
        v.setViewVisibility(R.id.widget_week, View.GONE);
        if (profile == null) {
            v.setTextViewText(R.id.widget_ai, c.getString(R.string.widget_name));
            v.setTextViewText(R.id.widget_account_name, c.getString(R.string.widget_choose));
            v.setTextViewText(R.id.widget_empty, c.getString(profileId > 0 ? R.string.widget_missing : R.string.widget_description));
            v.setViewVisibility(R.id.widget_empty, View.VISIBLE);
            v.setImageViewResource(R.id.widget_logo, R.drawable.portrait);
            Intent intent = new Intent(context, WidgetConfigActivity.class)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setData(Uri.parse("resetnotice://widget/config/" + id))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
            v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            return v;
        }
        v.setTextViewText(R.id.widget_ai, profile.platform);
        v.setTextViewText(R.id.widget_account_name, profile.account);
        Bitmap logo = BrandIcons.bitmap(c, profile.platform);
        if (logo != null) v.setImageViewBitmap(R.id.widget_logo, logo);
        else v.setImageViewResource(R.id.widget_logo, R.drawable.portrait);
        long now = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        ReminderStore.Slot shortSlot = ReminderStore.load(context, profile.id, ReminderStore.SHORT);
        ReminderStore.Slot weekSlot = ReminderStore.load(context, profile.id, ReminderStore.WEEK);
        boolean shortShown = renderSlot(v, c, shortSlot, profile.shortEnabled, now, elapsed, true);
        boolean weekShown = renderSlot(v, c, weekSlot, true, now, elapsed, false);
        v.setTextViewText(R.id.widget_empty, c.getString(R.string.widget_empty));
        v.setViewVisibility(R.id.widget_empty, shortShown || weekShown ? View.GONE : View.VISIBLE);
        Intent open = new Intent(context, MainActivity.class)
                .setData(Uri.parse("resetnotice://widget/open/" + id))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("open_profile", profile.id);
        v.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static boolean renderSlot(RemoteViews v, Context c, ReminderStore.Slot s, boolean enabled,
                                      long now, long elapsed, boolean shortSlot) {
        long target = WidgetMath.displayTarget(enabled, s.target, s.periodMinutes, now);
        int container = shortSlot ? R.id.widget_short : R.id.widget_week;
        v.setViewVisibility(container, target > 0 ? View.VISIBLE : View.GONE);
        if (target <= 0) return false;
        int label = shortSlot ? R.id.widget_short_label : R.id.widget_week_label;
        int chronometer = shortSlot ? R.id.widget_short_clock : R.id.widget_week_clock;
        int staticText = shortSlot ? R.id.widget_short_static : R.id.widget_week_static;
        int date = shortSlot ? R.id.widget_short_date : R.id.widget_week_date;
        int progress = shortSlot ? R.id.widget_short_progress : R.id.widget_week_progress;
        v.setTextViewText(label, I18n.slot(c, s));
        v.setTextViewText(date, ResetMath.displayDateTime(target, now));
        v.setProgressBar(progress, 10000, ResetMath.countdownLevel(target, s.periodMinutes, now), false);
        if (Build.VERSION.SDK_INT >= 24) {
            v.setViewVisibility(staticText, View.GONE);
            v.setViewVisibility(chronometer, View.VISIBLE);
            v.setChronometerCountDown(chronometer, true);
            v.setChronometer(chronometer, WidgetMath.chronometerBase(elapsed, target, now), null, true);
        } else {
            v.setViewVisibility(chronometer, View.GONE);
            v.setViewVisibility(staticText, View.VISIBLE);
            v.setTextViewText(staticText, I18n.countdown(c, target - now));
        }
        return true;
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateOne(context, manager, id);
    }
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        updateOne(context, manager, id);
    }
    @Override public void onDeleted(Context context, int[] ids) {
        for (int id : ids) WidgetBindingStore.delete(context, id);
    }
    @Override public void onRestored(Context context, int[] oldIds, int[] newIds) {
        WidgetBindingStore.restore(context, oldIds, newIds);
    }
}
