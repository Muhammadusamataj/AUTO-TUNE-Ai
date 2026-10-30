package com.autotuneeq.mvp;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;

/**
 * In-memory diagnostic ring buffer (never written to disk, never uploaded). It must not contain song titles or
 * artists: callers pass {@link #songRef} (a per-run salted hash prefix) instead. Free text is length-limited
 * and stripped of control characters.
 */
public final class DiagLog {
    public static final int MAX_LINES = 200, MAX_DETAIL = 160;
    private final ArrayDeque<String> lines = new ArrayDeque<>();
    private final byte[] salt = new byte[16];
    private final long start;
    public DiagLog() { new SecureRandom().nextBytes(salt); start = System.nanoTime(); }

    /** Opaque, per-run reference for a song; identical for the same song within one run, different across runs. */
    public String songRef(String identityKey) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256"); d.update(salt);
            byte[] h = d.digest((identityKey == null ? "" : identityKey).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder("song#");
            for (int i = 0; i < 3; i++) b.append(String.format("%02x", h[i] & 0xff));
            return b.toString();
        } catch (Exception e) { return "song#?"; }
    }
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length() && b.length() < MAX_DETAIL; i++) { char c = s.charAt(i); b.append(c < 0x20 || c == 0x7f ? ' ' : c); }
        return b.toString();
    }
    public synchronized void event(String category, String detail) {
        double sec = (System.nanoTime() - start) / 1e9;
        lines.addLast(String.format(java.util.Locale.US, "+%.1fs %s %s", sec, clean(category), clean(detail)));
        while (lines.size() > MAX_LINES) lines.removeFirst();
    }
    public synchronized String dump() {
        StringBuilder b = new StringBuilder("AutoTune EQ diagnostics (no song titles/artists; salted song refs only)\n");
        for (String l : lines) b.append(l).append('\n');
        return b.toString();
    }
    public synchronized int size() { return lines.size(); }
    public synchronized void clear() { lines.clear(); }
}
