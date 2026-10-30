package com.autotuneeq.mvp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Bounded persistent offline cache. Manual profiles have no expiry. No raw audio. */
public final class ProfileStore {
    private static final int MAX_PROFILES = 200;
    private final SharedPreferences prefs, legacy;
    public ProfileStore(Context context) {
        prefs=context.getSharedPreferences("profiles_v2", Context.MODE_PRIVATE);
        legacy=context.getSharedPreferences("profiles_v1", Context.MODE_PRIVATE);
        if(!prefs.contains("endpoint")) {
            String old=legacy.getString("endpoint", "");
            prefs.edit().putString("endpoint",CloudClient.validEndpoint(old)?old:"").apply();
        }
    }
    private static String key(String title, String artist) { return "profile:" + TrackIdentity.key(title, artist); }
    public Profile get(String title, String artist) {
        String k = key(title, artist);
        try {
            String json = prefs.getString(k, null);
            if (json == null) {
                // Profiles saved by 0.3.3 / the first 0.3.4 revision sit under the old key format: migrate on first use.
                String lk = "profile:" + TrackIdentity.legacyKey(title, artist);
                String lj = lk.equals(k) ? null : prefs.getString(lk, null);
                if (lj != null) {
                    Profile moved = Profile.fromJson(new JSONObject(lj));
                    boolean wasPinned = prefs.getBoolean("pin:" + lk, false);
                    if (store(title, artist, moved, wasPinned))
                        prefs.edit().remove(lk).remove("time:" + lk).remove("pin:" + lk).apply();
                    return moved;
                }
                // Migrate ordinary v1 identities lazily; ambiguous delimiter identities fail closed.
                if(title.contains("|") || artist.contains("|") ||
                        !TrackIdentity.canonical(artist).equals(artist.trim().toLowerCase(java.util.Locale.ROOT))) return null;
                String oldKey="profile:"+TrackIdentity.canonical(artist)+"|"+TrackIdentity.canonical(title);
                String old=legacy.getString(oldKey,null);
                if(old==null) return null;
                Profile p=Profile.fromJson(new JSONObject(old));
                if(put(title,artist,p)) legacy.edit().remove(oldKey).apply();
                return p;
            }
            return Profile.fromJson(new JSONObject(json));
        } catch (Exception ignored) { prefs.edit().remove(k).remove("time:"+k).remove("pin:"+k).apply(); return null; }
    }
    /** Cached/online profile: evicted before anything the user saved by hand. */
    public boolean put(String title, String artist, Profile p) { return store(title, artist, p, false); }
    /** Profile the user saved locally: pinned, evicted only if the cache holds nothing else. */
    public boolean putManual(String title, String artist, Profile p) { return store(title, artist, p, true); }
    private boolean store(String title, String artist, Profile p, boolean manual) {
        try {
            String k = key(title, artist);
            java.util.Map<String, Long> times = new java.util.HashMap<>();
            java.util.List<String> pinned = new ArrayList<>();
            for (String candidate : prefs.getAll().keySet()) {
                if (!candidate.startsWith("profile:") || candidate.equals(k)) continue;
                times.put(candidate, prefs.getLong("time:" + candidate, 0));
                if (prefs.getBoolean("pin:" + candidate, false)) pinned.add(candidate);
            }
            SharedPreferences.Editor e = prefs.edit();
            for (String victim : CachePolicy.victims(times, pinned, MAX_PROFILES))
                e.remove(victim).remove("time:" + victim).remove("pin:" + victim);
            e.putString(k, p.toJson().toString()).putLong("time:" + k, System.currentTimeMillis());
            if (manual) e.putBoolean("pin:" + k, true); else e.remove("pin:" + k);
            e.apply();
            if (!manual) forgetMiss(title, artist);
            return true; // accepted in memory; apply() persists asynchronously
        } catch (Exception ignored) { return false; }
    }
    // ---- negative lookup cache (server answered 404) -------------------------------------
    private MissCache misses;
    private synchronized MissCache missCache() {
        if (misses == null) misses = MissCache.parse(prefs.getString("misses_v1", ""));
        return misses;
    }
    public boolean recentMiss(String title, String artist) {
        return missCache().isMiss(TrackIdentity.key(title, artist), System.currentTimeMillis());
    }
    public void recordMiss(String title, String artist) {
        MissCache c = missCache(); c.record(TrackIdentity.key(title, artist), System.currentTimeMillis());
        prefs.edit().putString("misses_v1", c.serialize()).apply();
    }
    private void forgetMiss(String title, String artist) {
        MissCache c = missCache(); c.forget(TrackIdentity.key(title, artist));
        prefs.edit().putString("misses_v1", c.serialize()).apply();
    }
    public String endpoint() { return prefs.getString("endpoint", ""); }
    public void setEndpoint(String url) {
        String value=url.trim();
        if (!value.isEmpty() && !CloudClient.validEndpoint(value)) throw new IllegalArgumentException("HTTPS origin required");
        prefs.edit().putString("endpoint",value).apply();
    }
}
