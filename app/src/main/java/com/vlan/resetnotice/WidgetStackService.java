package com.vlan.resetnotice;

import android.content.Context;
import android.content.Intent;
import android.appwidget.AppWidgetManager;
import android.os.Build;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.ArrayList;
import java.util.List;

/** Only the system widget host is permitted to bind this collection service. */
public final class WidgetStackService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Cards(getApplicationContext(), intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1));
    }
    private static final class Cards implements RemoteViewsFactory {
        private final Context context;
        private final int widgetId;
        private int height = WidgetDeckMath.DEFAULT_HEIGHT;
        private List<Integer> ids = new ArrayList<>();
        Cards(Context context, int widgetId) { this.context = context; this.widgetId = widgetId; }
        @Override public void onCreate() { onDataSetChanged(); }
        @Override public void onDataSetChanged() {
            if (!BootState.unlocked(context)) { ids = new ArrayList<>(); return; }
            if (widgetId > 0) height = AllAccountsWidgetProvider.rowHeight(context, AppWidgetManager.getInstance(context), widgetId);
            ids = AllAccountsWidgetProvider.orderedAccounts(context);
        }
        @Override public void onDestroy() { ids = new ArrayList<>(); }
        @Override public int getCount() { return ids.size(); }
        @Override public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= ids.size()) return null;
            return QuotaWidgetProvider.renderStackItem(context, ids.get(position), height);
        }
        @Override public RemoteViews getLoadingView() {
            RemoteViews loading = new RemoteViews(context.getPackageName(), R.layout.widget_deck_loading);
            if (BootState.unlocked(context)) {
                Context localized = AppPreferences.localized(context);
                loading.setTextViewText(R.id.deck_loading_text, localized.getString(R.string.widget_deck_loading));
                loading.setTextColor(R.id.deck_loading_text, localized.getColor(R.color.accent));
            }
            if (Build.VERSION.SDK_INT >= 31)
                loading.setViewLayoutHeight(R.id.deck_loading_body, height, TypedValue.COMPLEX_UNIT_DIP);
            return loading;
        }
        @Override public int getViewTypeCount() { return 3; }
        @Override public long getItemId(int position) { return position >= 0 && position < ids.size() ? ids.get(position) : -1; }
        @Override public boolean hasStableIds() { return true; }
    }
}
