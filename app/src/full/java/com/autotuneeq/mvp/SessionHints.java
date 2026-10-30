package com.autotuneeq.mvp;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure-Java bookkeeping for optional player OPEN_AUDIO_EFFECT_CONTROL_SESSION events.
 * A hint is only a claim by a broadcast sender: it is never trusted until the caller attaches it,
 * and it is kept (bounded, expiring, retried with back-off) so that an early OPEN event or a
 * transient attach failure does not permanently lose the session.
 */
public final class SessionHints {
    public interface Attacher { boolean attach(int session, String pkg); }
    public static final int MAX_HINTS = 16;
    public static final long TTL_MS = 10L * 60_000L;
    public static final long BASE_BACKOFF_MS = 2_500L;
    public static final int MAX_ATTEMPTS = 6;

    private static final class Hint {
        final String pkg; final long created; int attempts; long nextTry;
        Hint(String p, long t) { pkg = p; created = t; nextTry = t; }
    }
    private final Map<Integer, Hint> hints = new LinkedHashMap<>();

    public synchronized boolean offer(int session, String pkg, long now) {
        prune(now);
        if (session <= 0 || pkg == null || pkg.isEmpty() || pkg.length()>255) return false;
        Hint existing=hints.get(session);
        if(existing!=null) return existing.pkg.equals(pkg);
        if (hints.size() >= MAX_HINTS && !hints.containsKey(session)) return false;
        hints.put(session, new Hint(pkg, now));
        return true;
    }
    public synchronized void close(int session, String pkg) {
        Hint h = hints.get(session);
        if (h != null && h.pkg.equals(pkg)) hints.remove(session);
    }
    /** Attach hints that belong to the currently playing player; keep failed ones for a bounded retry. */
    public synchronized void tryAttach(String playerPkg, long now, Attacher attacher) {
        if (playerPkg == null || playerPkg.isEmpty()) return;
        Iterator<Map.Entry<Integer, Hint>> it = hints.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Hint> e = it.next();
            Hint h = e.getValue();
            if (now - h.created > TTL_MS) { it.remove(); continue; }
            if (!playerPkg.equals(h.pkg) || now < h.nextTry) continue;
            if (attacher.attach(e.getKey(), h.pkg)) { it.remove(); continue; }
            h.attempts++;
            if (h.attempts >= MAX_ATTEMPTS) it.remove();
            else h.nextTry = now + (BASE_BACKOFF_MS << (h.attempts - 1));
        }
    }
    public synchronized void prune(long now) {
        Iterator<Hint> it = hints.values().iterator();
        while (it.hasNext()) if (now - it.next().created > TTL_MS) it.remove();
    }
    public synchronized void clear() { hints.clear(); }
    public synchronized int size() { return hints.size(); }
}
