package com.autotuneeq.mvp;

import java.util.HashSet;
import java.util.Set;

/**
 * Honest per-player status for per-app EQ. Evidence beats assumption: a player is "cooperative" only after
 * it actually sent an audio-session OPEN event to this app in this run. A short list of players that
 * third-party EQ documentation reports as NOT publishing such events is labelled unsupported until an
 * event is seen. Nothing here claims audible EQ.
 */
public final class PlayerSupport {
    public enum Status { SESSION_EVENT_SEEN, REPORTED_NO_SESSION_EVENTS, UNVERIFIED }
    /** Reported by third-party equalizer documentation; not independently tested by this project. */
    private static final Set<String> REPORTED_NO_EVENTS = new HashSet<>(java.util.Arrays.asList(
            "com.google.android.youtube"));
    private final Set<String> seen = new HashSet<>();
    public synchronized void sawOpen(String pkg) { if (pkg != null && !pkg.isEmpty() && seen.size() < 64) seen.add(pkg); }
    public synchronized void reset() { seen.clear(); }
    public synchronized Status status(String pkg) {
        if (pkg == null || pkg.isEmpty()) return Status.UNVERIFIED;
        if (seen.contains(pkg)) return Status.SESSION_EVENT_SEEN;
        return REPORTED_NO_EVENTS.contains(pkg) ? Status.REPORTED_NO_SESSION_EVENTS : Status.UNVERIFIED;
    }
    /** After this long without any session event from a playing, unverified player, say so plainly. */
    public static final long NO_EVENT_AFTER_MS = 20_000L;
    public String describe(String pkg, long playingMs) {
        if (status(pkg) == Status.UNVERIFIED && pkg != null && !pkg.isEmpty() && playingMs >= NO_EVENT_AFTER_MS)
            return "Player support: no audio-session event after 20 s of playback; per-app EQ is NOT active for this player (restart playback once after arming, otherwise treat it as unsupported)";
        return describe(pkg);
    }
    public String describe(String pkg) {
        switch (status(pkg)) {
            case SESSION_EVENT_SEEN: return "Player support: cooperative (session event received); audible EQ still unverified";
            case REPORTED_NO_SESSION_EVENTS: return "Player support: UNSUPPORTED for per-app EQ (reported not to publish audio-session events; none seen here)";
            default: return "Player support: unverified (no audio-session event seen from this player yet)";
        }
    }
}
