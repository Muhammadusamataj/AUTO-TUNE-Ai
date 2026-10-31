package com.autotuneeq.mvp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Export/import of the user's own profiles as plain JSON text (clipboard or a file the user picks through the system
 * file picker; no storage permission). Import is a trust boundary: strict size/shape limits, finite in-range numbers,
 * control characters rejected, provenance forced to "imported". Nothing here touches the network.
 */
public final class ProfileExchange {
    private ProfileExchange() { }
    public static final String FORMAT = "autotuneeq-profiles";
    public static final int VERSION = 1, MAX_BYTES = 256 * 1024, MAX_ENTRIES = 200, MAX_TEXT = 200;

    /** One importable item. title/artist are both empty for a named custom preset (name is then in {@code title}). */
    public static final class Entry {
        public final String kind, title, artist; public final double[] db;
        Entry(String kind, String title, String artist, double[] db) { this.kind = kind; this.title = title; this.artist = artist; this.db = db; }
        public Profile profile() { return new Profile(db.clone(), "imported"); }
    }
    public static final class Result {
        public final List<Entry> accepted = new ArrayList<>(); public final List<String> rejected = new ArrayList<>();
        public boolean fatal; public String fatalReason = "";
        public String summary() { return fatal ? "Import refused: " + fatalReason : accepted.size() + " imported, " + rejected.size() + " skipped"; }
    }

    public static String export(List<Entry> entries) {
        try {
            JSONArray list = new JSONArray();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("kind", e.kind).put("title", e.title).put("artist", e.artist);
                JSONArray bands = new JSONArray(); for (double v : e.db) bands.put(Math.round(v * 100.0) / 100.0);
                o.put("bands_db", bands); list.put(o);
            }
            return new JSONObject().put("format", FORMAT).put("version", VERSION).put("profiles", list).toString();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    public static Entry song(String title, String artist, Profile p) { return new Entry("song", title, artist, p.db.clone()); }
    public static Entry preset(String name, Profile p) { return new Entry("preset", name, "", p.db.clone()); }

    public static Result parse(String text) {
        Result r = new Result();
        if (text == null || text.isEmpty()) return fail(r, "empty");
        if (text.length() > MAX_BYTES) return fail(r, "file larger than " + MAX_BYTES / 1024 + " KiB");
        JSONObject root;
        try { root = new JSONObject(text); } catch (JSONException | RuntimeException | StackOverflowError e) { return fail(r, "not valid JSON"); }
        if (!FORMAT.equals(root.optString("format"))) return fail(r, "not an AutoTune EQ profile file");
        if (root.optInt("version", -1) != VERSION) return fail(r, "unsupported version");
        JSONArray list = root.optJSONArray("profiles");
        if (list == null) return fail(r, "no profile list");
        if (list.length() > MAX_ENTRIES) return fail(r, "more than " + MAX_ENTRIES + " profiles");
        Map<String, Entry> byKey = new LinkedHashMap<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            String why = o == null ? "not an object" : check(o);
            if (why != null) { r.rejected.add("#" + (i + 1) + ": " + why); continue; }
            String kind = o.optString("kind"), title = o.optString("title").trim(), artist = o.optString("artist").trim();
            JSONArray b = o.optJSONArray("bands_db"); double[] db = new double[5];
            for (int k = 0; k < 5; k++) db[k] = b.optDouble(k);
            byKey.put(kind + "|" + TrackIdentity.key(title, artist), new Entry(kind, title, artist, db));   // later duplicate wins
        }
        r.accepted.addAll(byKey.values());
        return r;
    }
    private static Result fail(Result r, String why) { r.fatal = true; r.fatalReason = why; return r; }

    private static String check(JSONObject o) {
        String kind = o.optString("kind");
        if (!kind.equals("song") && !kind.equals("preset")) return "unknown kind";
        String title = o.optString("title"), artist = o.optString("artist");
        if (title.trim().isEmpty() || title.length() > MAX_TEXT || artist.length() > MAX_TEXT) return "title/artist missing or too long";
        if (kind.equals("preset") && !artist.isEmpty()) return "preset must not have an artist";
        if (hasControl(title) || hasControl(artist)) return "control characters";
        JSONArray b = o.optJSONArray("bands_db");
        if (b == null || b.length() != 5) return "needs exactly 5 band values";
        for (int k = 0; k < 5; k++) {
            Object v = b.opt(k);
            if (!(v instanceof Number)) return "band values must be numbers";
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d) || d < -Profile.MAX_DB || d > Profile.MAX_DB) return "band value outside +/-" + Profile.MAX_DB + " dB";
        }
        return null;
    }
    private static boolean hasControl(String s) {
        for (int i = 0; i < s.length(); i++) { char c = s.charAt(i); if (c < 0x20 || c == 0x7f) return true; }
        return false;
    }
}
