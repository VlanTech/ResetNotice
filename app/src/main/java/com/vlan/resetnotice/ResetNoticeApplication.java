package com.vlan.resetnotice;

import android.app.Application;
import android.content.res.Configuration;

/** Refresh existing widgets when a live process receives theme/locale changes. */
public final class ResetNoticeApplication extends Application {
    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        QuotaWidgetProvider.updateAll(this);
    }
}
