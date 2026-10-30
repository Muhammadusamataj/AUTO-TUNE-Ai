package com.autotuneeq.mvp;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded, expiring "server said not found" cache so an unknown song is not re-sent to the
 * profile server on every pause/resume. Pure Java; persisted by ProfileStore as plain text.
 */
public final class MissCache {
    public static final long TTL_MS = 6L * 60L * 60L * 1000L;
    public static final int MAX_ENTRIES = 200;
    private final LinkedHashMap<String, Long> misses = new LinkedHashMap<>();
    private final long ttl;
    public MissCache() { this(TTL_MS); }
    public MissCache(long ttlMs) { ttl = ttlMs; }

    public synchronized boolean isMiss(String key, long now) {
        Long at = misses.get(key);
        if (at == null) return false;
        if (now - at > ttl || now < at) { misses.remove(key); return false; }
        return true;
    }
    public synchronized void record(String key, long now) {
        if (key == null || key.isEmpty() || key.length() > 600) return;
        misses.remove(key); misses.put(key, now);
        Iterator<Map.Entry<String, Long>> it = misses.entrySet().iterator();
        while (misses.size() > MAX_ENTRIES && it.hasNext()) { it.next(); it.remove(); }
    }
    public synchronized void forget(String key) { misses.remove(key); }
    public synchronized int size() { return misses.size(); }

    /** One entry per line: epochMillis TAB key (keys never contain control characters after normalisation). */
    public synchronized String serialize() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Long> e : misses.entrySet())
            b.append(e.getValue()).append('\t').append(e.getKey().replace('\n', ' ').replace('\t', ' ')).append('\n');
        return b.toString();
    }
    public static MissCache parse(String text) {
        MissCache c = new MissCache();
        if (text == null) return c;
        for (String line : text.split("\n")) {
            int tab = line.indexOf('\t');
            if (tab <= 0) continue;
            try { c.misses.put(line.substring(tab + 1), Long.parseLong(line.substring(0, tab))); }
            catch (NumberFormatException ignored) { }
            if (c.misses.size() >= MAX_ENTRIES) break;
        }
        return c;
    }
}
