package com.autotuneeq.mvp;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small persistent user choices. Only choices the user made explicitly are stored. The legacy session-0 global
 * experiment is deliberately NOT persistable: it always starts OFF.
 */
public final class AppSettings {
    private final SharedPreferences prefs;
    public AppSettings(Context c) { prefs = c.getSharedPreferences("settings_v1", Context.MODE_PRIVATE); }
    /** User explicitly armed the per-app EQ experiment; stays armed across service restarts until disabled. */
    public boolean perAppArmed() { return prefs.getBoolean("per_app_armed", false); }
    public void setPerAppArmed(boolean on) { prefs.edit().putBoolean("per_app_armed", on).apply(); }
}
