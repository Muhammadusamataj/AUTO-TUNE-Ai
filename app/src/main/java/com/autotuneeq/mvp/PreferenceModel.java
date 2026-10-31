package com.autotuneeq.mvp;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Local, transparent preference learning. For every genre class it keeps a small per-band offset (dB) that is added to
 * the built-in preset and moves toward what the user actually chose. This is a bounded running average, NOT a trained
 * model: no data leaves the device, nothing is shared between users, and the whole state is five numbers per class.
 */
public final class PreferenceModel {
    public static final double LEARNING_RATE = 0.30, MAX_OFFSET_DB = 1.5;
    private static final int MAX_CLASSES = 16, MAX_JSON = 8192;
    private final Map<String, double[]> offsets = new HashMap<>();
    private final Map<String, Integer> events = new HashMap<>();

    public synchronized double[] offset(String genreClass) {
        double[] o = offsets.get(genreClass);
        return o == null ? new double[5] : o.clone();
    }
    public synchronized int feedbackCount(String genreClass) {
        Integer n = events.get(genreClass); return n == null ? 0 : n;
    }
    /** The user ended up with {@code chosen} after being offered {@code suggested}: move the offset a step toward it. */
    public synchronized void learn(String genreClass, double[] suggested, double[] chosen) {
        if (genreClass == null || suggested == null || chosen == null || suggested.length != 5 || chosen.length != 5) return;
        if (!offsets.containsKey(genreClass) && offsets.size() >= MAX_CLASSES) return;
        double[] o = offsets.containsKey(genreClass) ? offsets.get(genreClass) : new double[5];
        for (int i = 0; i < 5; i++) {
            if (!Double.isFinite(suggested[i]) || !Double.isFinite(chosen[i])) return;
            o[i] = clamp(o[i] + LEARNING_RATE * (chosen[i] - suggested[i]));
        }
        offsets.put(genreClass, o); events.put(genreClass, feedbackCount(genreClass) + 1);
    }
    /** "Not for me": halve what has been learned for this class (back toward the plain preset). */
    public synchronized void thumbsDown(String genreClass) {
        double[] o = offsets.get(genreClass);
        if (o != null) for (int i = 0; i < 5; i++) o[i] /= 2;
        events.put(genreClass, feedbackCount(genreClass) + 1);
    }
    public synchronized void reset() { offsets.clear(); events.clear(); }
    private static double clamp(double v) { return Math.max(-MAX_OFFSET_DB, Math.min(MAX_OFFSET_DB, v)); }

    public synchronized String toJson() {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, double[]> e : offsets.entrySet()) {
                JSONArray a = new JSONArray(); for (double v : e.getValue()) a.put(Math.round(v * 1000.0) / 1000.0);
                root.put(e.getKey(), new JSONObject().put("offset", a).put("n", feedbackCount(e.getKey())));
            }
            return root.toString();
        } catch (Exception e) { return "{}"; }
    }
    /** Hostile or corrupt text yields an empty model, never an exception. */
    public static PreferenceModel fromJson(String text) {
        PreferenceModel m = new PreferenceModel();
        if (text == null || text.length() > MAX_JSON) return m;
        try {
            JSONObject root = new JSONObject(text);
            java.util.Iterator<String> it = root.keys();
            while (it.hasNext() && m.offsets.size() < MAX_CLASSES) {
                String key = it.next();
                if (!Recommender.isKnownClass(key)) continue;
                JSONObject o = root.optJSONObject(key); JSONArray a = o == null ? null : o.optJSONArray("offset");
                if (a == null || a.length() != 5) continue;
                double[] v = new double[5]; boolean ok = true;
                for (int i = 0; i < 5; i++) { v[i] = a.optDouble(i, Double.NaN); if (!Double.isFinite(v[i])) ok = false; v[i] = clamp(v[i]); }
                if (ok) { m.offsets.put(key, v); m.events.put(key, Math.max(0, Math.min(10000, o.optInt("n", 0)))); }
            }
        } catch (Exception | StackOverflowError ignored) { return new PreferenceModel(); }
        return m;
    }
}
