package com.autotuneeq.mvp;

/** Regression tests for H2 (early OPEN kept) and H3 (failed attach retried with back-off). Pure Java; not a device test. */
public final class TestSessionHints {
    private static void check(boolean ok, String text) { if (!ok) throw new AssertionError(text); }
    public static void main(String[] args) {
        // H2: OPEN arrives before playback is active -> hint is retained and attached once the player plays.
        SessionHints h = new SessionHints();
        check(h.offer(7, "com.spotify.music", 1_000), "offer accepted");
        final int[] calls = {0};
        h.tryAttach("", 1_100, (s, p) -> { calls[0]++; return true; });
        h.tryAttach("com.other.player", 1_200, (s, p) -> { calls[0]++; return true; });
        check(calls[0] == 0 && h.size() == 1, "no attach for other/unknown player; hint retained");
        h.tryAttach("com.spotify.music", 5_000, (s, p) -> { calls[0]++; return true; });
        check(calls[0] == 1 && h.size() == 0, "attached once player is active, hint consumed on success");

        // H3: failed attach is retried with exponential back-off, then dropped after MAX_ATTEMPTS.
        h = new SessionHints(); calls[0] = 0;
        h.offer(9, "p", 0);
        SessionHints.Attacher fail = (s, p) -> { calls[0]++; return false; };
        h.tryAttach("p", 0, fail);                          // attempt 1 fails -> next at +2500
        check(calls[0] == 1 && h.size() == 1, "hint kept after failure");
        h.tryAttach("p", 1_000, fail);
        check(calls[0] == 1, "back-off respected");
        h.tryAttach("p", 2_500, fail);                      // attempt 2 -> next at +5000
        h.tryAttach("p", 7_500, fail);                      // attempt 3
        h.tryAttach("p", 17_500, fail);                     // attempt 4
        h.tryAttach("p", 37_500, fail);                     // attempt 5
        h.tryAttach("p", 77_500, fail);                     // attempt 6 -> dropped
        check(calls[0] == 6 && h.size() == 0, "dropped after max attempts");

        // Success after a transient failure.
        h = new SessionHints(); calls[0] = 0;
        h.offer(3, "p", 0);
        h.tryAttach("p", 0, (s, p) -> { calls[0]++; return false; });
        h.tryAttach("p", 2_500, (s, p) -> { calls[0]++; return true; });
        check(calls[0] == 2 && h.size() == 0, "retry succeeds and clears hint");

        // CLOSE only removes a matching package; spoofed package is ignored.
        h = new SessionHints(); h.offer(4, "real", 0);
        h.close(4, "spoof"); check(h.size() == 1, "wrong-package close ignored");
        h.close(4, "real"); check(h.size() == 0, "matching close removes");

        // Bounds: capacity and TTL.
        h = new SessionHints();
        for (int i = 1; i <= SessionHints.MAX_HINTS; i++) check(h.offer(i, "p", 0), "fill");
        check(!h.offer(99, "p", 1), "capacity enforced");
        check(h.offer(99, "p", SessionHints.TTL_MS + 5), "expired hints pruned making room");
        check(!h.offer(-1, "p", 0) && !h.offer(5, "", 0) && !h.offer(5, null, 0), "invalid offers rejected");
        System.out.println("PASS: early OPEN retained, retry/back-off, bounded drop, spoofed CLOSE ignored, capacity/TTL (hardware untested)");
    }
}
