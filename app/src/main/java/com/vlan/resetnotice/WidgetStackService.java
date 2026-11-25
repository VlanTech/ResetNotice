package com.vlan.resetnotice;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;
import java.util.ArrayList;
import java.util.List;

/** Only the system widget host is permitted to bind this collection service. */
public final class WidgetStackService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Cards(getApplicationContext());
    }
    private static final class Cards implements RemoteViewsFactory {
        private final Context context;
        private List<Integer> ids = new ArrayList<>();
        Cards(Context context) { this.context = context; }
        @Override public void onCreate() { }
        @Override public void onDataSetChanged() {
            if (!BootState.unlocked(context)) { ids = new ArrayList<>(); return; }
            ids = AllAccountsWidgetProvider.orderedAccounts(context);
        }
        @Override public void onDestroy() { ids = new ArrayList<>(); }
        @Override public int getCount() { return ids.size(); }
        @Override public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= ids.size()) return null;
            return QuotaWidgetProvider.renderStackItem(context, ids.get(position));
        }
        @Override public RemoteViews getLoadingView() { return null; }
        @Override public int getViewTypeCount() { return 3; }
        @Override public long getItemId(int position) { return position >= 0 && position < ids.size() ? ids.get(position) : -1; }
        @Override public boolean hasStableIds() { return true; }
    }
}
