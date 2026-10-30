package com.autotuneeq.mvp;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

/**
 * Runs the blind A/B presentation sequence on this app's own AudioTrack + Equalizer.
 * All audio-effect and AudioTrack work happens on a private worker thread. Level matching is by construction:
 * both presentations use the identical tone, amplitude and duration; only the effect state differs.
 */
public final class AbRig {
    public interface Listener {
        void log(String line);            // diagnostic line (UI thread)
        void ready(int[] bandHz);         // effect attached and band frequencies known
        void presented();                 // both presentations of a trial finished: ask the listener
        void failed(String reason);
    }
    private static final long TONE_MS = 1200, GAP_MS = 450, SETTLE_MS = 250;
    private final HandlerThread thread = new HandlerThread("autotune-ab-rig");
    private final Handler worker, ui = new Handler(Looper.getMainLooper());
    private final AbTonePlayer player = new AbTonePlayer();
    private final EqController eq = new EqController();
    private final Listener listener;
    private volatile int token; private volatile boolean closed;
    private int session; private int[] hz;

    public AbRig(Listener l) { listener = l; thread.start(); worker = new Handler(thread.getLooper()); }
    private void say(String s) { ui.post(() -> { if (!closed) listener.log(s); }); }
    private void fail(String s) { ui.post(() -> { if (!closed) listener.failed(s); }); }

    public void open() {
        final int t = ++token;
        worker.post(() -> {
            if (closed || t != token) return;
            try {
                session = player.start();
                String attached = eq.attachOwned(session);
                hz = eq.ownedBandFreqsHz();
                say("attach: " + attached);
                if (hz == null || hz.length == 0) { player.stop(); fail("No usable EQ bands on this device/route"); return; }
                StringBuilder b = new StringBuilder("device bands (Hz):");
                for (int f : hz) b.append(' ').append(f);
                say(b.toString());
                final int[] copy = hz.clone();
                ui.post(() -> { if (!closed) listener.ready(copy); });
            } catch (RuntimeException e) { player.stop(); fail("Setup failed: " + e.getClass().getSimpleName()); }
        });
    }
    private void apply(int band, boolean effectOn, boolean nullTrial, String label) {
        if (effectOn) {
            EqController.TestCut r = eq.ownedTestCut(band, nullTrial ? 0 : AbSession.CUT_MB);
            int got = 0; for (short v : r.requested) got = Math.max(got, -v);
            String limited = (!nullTrial && r.ok && got < AbSession.CUT_MB) ? " [CUT LIMITED to " + got / 100.0 + " dB by device range]" : "";
            say(label + ": EQ ON band " + band + (nullTrial ? " (0 dB null)" : " (-" + AbSession.CUT_MB / 100 + " dB)") + limited + " · " + r.message
                    + " req=" + java.util.Arrays.toString(r.requested) + " got=" + java.util.Arrays.toString(r.readback));
        } else say(label + ": EQ BYPASS " + (eq.ownedBypass() ? "ok" : "FAILED"));
    }
    /** Present A then B for one trial; calls listener.presented() afterwards. */
    public void present(AbSession.Trial trial) {
        final int t = ++token;
        if (hz == null || trial.band >= hz.length) { fail("Band unavailable"); return; }
        pendingTrial = trial;
        final boolean firstOn = trial.nullTrial || trial.onFirst;
        worker.post(() -> {
            if (closed || t != token) return;
            player.setTone(hz[trial.band], AbSession.amplitudeFor(hz[trial.band]));
            apply(trial.band, firstOn, trial.nullTrial, "A");
            worker.postDelayed(() -> step(t, true, null), SETTLE_MS);
        });
    }
    private volatile AbSession.Trial pendingTrial;
    private void step(int t, boolean firstPart, Object unused) {
        if (closed || t != token) return;
        final AbSession.Trial trial = pendingTrial;
        if (firstPart) {
            player.setGate(true);
            worker.postDelayed(() -> {
                if (closed || t != token) return;
                player.setGate(false);
                worker.postDelayed(() -> {
                    if (closed || t != token) return;
                    boolean secondOn = trial.nullTrial || !trial.onFirst;
                    apply(trial.band, secondOn, trial.nullTrial, "B");
                    worker.postDelayed(() -> step(t, false, null), SETTLE_MS);
                }, GAP_MS);
            }, TONE_MS);
        } else {
            player.setGate(true);
            worker.postDelayed(() -> {
                if (closed || t != token) return;
                player.setGate(false);
                ui.post(() -> { if (!closed && t == token) listener.presented(); });
            }, TONE_MS);
        }
    }
    /**
     * Restoration probe: continuous tone. 1) bypass  2) cut on band  3) effect RELEASED while the tone keeps playing.
     * Step 3 must sound like step 1. Re-attaches the effect afterwards so further trials can run.
     */
    public void restorationProbe(int band) {
        final int t = ++token;
        worker.post(() -> {
            if (closed || t != token || hz == null || band >= hz.length) return;
            player.setTone(hz[band], AbSession.amplitudeFor(hz[band]));
            eq.ownedBypass(); player.setGate(true); say("restore: 1/3 bypass (reference)");
            worker.postDelayed(() -> {
                if (closed || t != token) return;
                player.setGate(false);
                worker.postDelayed(() -> {
                    if (closed || t != token) return;
                    apply(band, true, false, "restore 2/3"); player.setGate(true);
                    worker.postDelayed(() -> {
                        if (closed || t != token) return;
                        player.setGate(false);
                        worker.postDelayed(() -> {
                            if (closed || t != token) return;
                            eq.releaseOwned(); say("restore 3/3: effect RELEASED, tone continues");
                            player.setGate(true);
                            worker.postDelayed(() -> {
                                if (closed || t != token) return;
                                player.setGate(false);
                                say("restore: re-attach -> " + eq.attachOwned(session));
                                ui.post(() -> { if (!closed && t == token) listener.presented(); });
                            }, TONE_MS);
                        }, GAP_MS);
                    }, TONE_MS);
                }, GAP_MS);
            }, TONE_MS);
        });
    }
    public void cancel() { token++; worker.post(() -> player.setGate(false)); }
    public void close() {
        if (closed) return; closed = true; token++; ui.removeCallbacksAndMessages(null);
        worker.removeCallbacksAndMessages(null);
        worker.post(() -> { eq.releaseOwned(); player.stop(); thread.quitSafely(); });
    }
}
