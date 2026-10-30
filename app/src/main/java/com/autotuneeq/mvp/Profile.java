package com.autotuneeq.mvp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/** 5 fixed-frequency points (dB): saved recommendations, not guaranteed optimum. */
public final class Profile {
    public static final int[] CENTERS_HZ = {80, 250, 1000, 4000, 10000};
    public static final double MAX_DB = 3.0;
    public final double[] db;
    public final String source;

    public Profile(double[] values, String source) {
        if (values == null || values.length != CENTERS_HZ.length) throw new IllegalArgumentException("5 bands required");
        this.db = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            double v = Double.isFinite(values[i]) ? values[i] : 0;
            this.db[i] = Math.max(-MAX_DB, Math.min(MAX_DB, v));
        }
        this.source = source == null ? "unknown" : source;
    }

    public static Profile flat() { return new Profile(new double[5], "safe neutral"); }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        JSONArray bands = new JSONArray();
        for (double v : db) bands.put(v);
        obj.put("bands_db", bands);
        obj.put("source", source);
        return obj;
    }

    public static Profile fromJson(JSONObject obj) throws JSONException {
        JSONArray array = obj.getJSONArray("bands_db");
        if (array.length() != 5) throw new JSONException("Expected exactly five bands");
        double[] v = new double[5];
        for (int i = 0; i < 5; i++) {
            Object raw = array.get(i);
            if (!(raw instanceof Number)) throw new JSONException("Numeric bands required");
            v[i] = ((Number) raw).doubleValue();
            if (!Double.isFinite(v[i]) || Math.abs(v[i]) > MAX_DB)
                throw new JSONException("Invalid profile gain");
        }
        if (obj.optString("source", "cloud").length() > 200)
            throw new JSONException("Source too long");
        return new Profile(v, obj.optString("source", "cloud"));
    }

    public String describe() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) b.append("  ·  ");
            b.append(CENTERS_HZ[i] >= 1000 ? (CENTERS_HZ[i] / 1000) + "k" : CENTERS_HZ[i]);
            b.append(": ").append(String.format(Locale.US, "%+.1f", db[i])).append("dB");
        }
        return b.toString();
    }
}
