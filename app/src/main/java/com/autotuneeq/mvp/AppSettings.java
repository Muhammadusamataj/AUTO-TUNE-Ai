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

    /** Selected preset id (see Presets.fromId) and the user's CUSTOM values; restored on start. Never throws. */
    public Presets selectedPreset() { return Presets.fromId(prefs.getString("preset", "FLAT")); }
    public void setSelectedPreset(Presets p) { prefs.edit().putString("preset", p.name()).apply(); }
    public double[] customValues() {
        double[] v = new double[5];
        String raw = prefs.getString("custom_db", "");
        String[] parts = raw.split(",");
        if (parts.length != 5) return v;
        try { for (int i = 0; i < 5; i++) { double d = Double.parseDouble(parts[i]); v[i] = Double.isFinite(d) ? Math.max(-Profile.MAX_DB, Math.min(Profile.MAX_DB, d)) : 0; } }
        catch (NumberFormatException e) { return new double[5]; }
        return v;
    }
    public void setCustomValues(double[] v) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 5; i++) { if (i > 0) b.append(','); b.append(String.format(java.util.Locale.US, "%.2f", i < v.length ? v[i] : 0)); }
        prefs.edit().putString("custom_db", b.toString()).apply();
    }
    /** Genre suggestions are only applied automatically when the user switched this on (default off). */
    public boolean autoApplyGenre() { return prefs.getBoolean("auto_genre", false); }
    public void setAutoApplyGenre(boolean on) { prefs.edit().putBoolean("auto_genre", on).apply(); }
    public PreferenceModel preferences() { return PreferenceModel.fromJson(prefs.getString("pref_model", "")); }
    public void setPreferences(PreferenceModel m) { prefs.edit().putString("pref_model", m.toJson()).apply(); }
}
