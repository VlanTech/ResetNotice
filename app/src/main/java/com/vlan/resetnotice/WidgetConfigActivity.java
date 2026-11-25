package com.vlan.resetnotice;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import java.util.List;

/** Also supports launchers which only allow widgets from the desktop picker. */
public final class WidgetConfigActivity extends Activity {
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppPreferences.wrap(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        if (!QuotaWidgetProvider.validWidget(this, id)) { finish(); return; }
        List<ReminderStore.Profile> profiles = ReminderStore.profiles(this);
        if (profiles.isEmpty()) {
            Toast.makeText(this, R.string.widget_no_accounts, Toast.LENGTH_LONG).show();
            finish(); return;
        }
        CharSequence[] names = new CharSequence[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) names[i] = profiles.get(i).label();
        new AlertDialog.Builder(this).setTitle(R.string.widget_choose)
                .setItems(names, (dialog, index) -> {
                    if (ReminderStore.profile(this, profiles.get(index).id) == null ||
                            !WidgetBindingStore.bind(this, id, profiles.get(index).id)) { finish(); return; }
                    QuotaWidgetProvider.updateOne(this, AppWidgetManager.getInstance(this), id);
                    Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                    setResult(RESULT_OK, result); finish();
                }).setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish()).show();
    }
}
