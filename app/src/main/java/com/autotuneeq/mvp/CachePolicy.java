package com.autotuneeq.mvp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Eviction choice for the offline profile cache: locally saved (pinned) profiles are evicted last. */
public final class CachePolicy {
    private CachePolicy() { }
    /**
     * @param times   write time per existing key, excluding the key about to be inserted
     * @param pinned  keys the user saved manually
     * @param maxAfterInsert capacity of the cache once the new key has been inserted
     * @return keys to remove: oldest unpinned first, then oldest pinned only if still over capacity
     */
    public static List<String> victims(Map<String, Long> times, Collection<String> pinned, int maxAfterInsert) {
        List<String> out = new ArrayList<>();
        int excess = times.size() + 1 - maxAfterInsert;
        if (excess <= 0) return out;
        Set<String> pin = new java.util.HashSet<>(pinned);
        List<String> unpinned = new ArrayList<>(), pinnedKeys = new ArrayList<>();
        for (String k : times.keySet()) (pin.contains(k) ? pinnedKeys : unpinned).add(k);
        java.util.Comparator<String> byAge = (a, b) -> Long.compare(times.get(a), times.get(b));
        unpinned.sort(byAge); pinnedKeys.sort(byAge);
        for (String k : unpinned) { if (excess-- <= 0) return out; out.add(k); }
        for (String k : pinnedKeys) { if (excess-- <= 0) return out; out.add(k); }
        return out;
    }
}
