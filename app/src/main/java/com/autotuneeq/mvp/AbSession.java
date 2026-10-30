package com.autotuneeq.mvp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Blind A/B listening plan and scoring for the own-tone EQ check. Pure Java.
 * Each trial presents the same sine tone twice (A then B). On a real trial one presentation has the
 * effect ON with a cut on one hardware band and the other presentation has the effect bypassed; on a
 * null trial both presentations use identical processing. The listener never sees which is which.
 */
public final class AbSession {
    public enum Answer { FIRST_QUIETER, SECOND_QUIETER, SAME }
    public static final class Trial {
        public final int band; public final boolean nullTrial, onFirst;
        Trial(int band, boolean nullTrial, boolean onFirst) { this.band = band; this.nullTrial = nullTrial; this.onFirst = onFirst; }
    }
    public static final class BandResult { public int real, hits; public boolean measured; }
    public static final class Summary {
        public final BandResult[] bands; public final int nullTrials, falseAlarms;
        public final String text;
        Summary(BandResult[] b, int n, int f, String t) { bands = b; nullTrials = n; falseAlarms = f; text = t; }
    }
    /** Cut applied on real trials (millibel magnitude). */
    public static final int CUT_MB = 600;
    /** Even count keeps ON-first/ON-second balanced per band, so a side-biased guesser cannot look 'audible'. */
    public static final int REAL_PER_BAND = 4, NULL_PER_BAND = 1;

    private final List<Trial> trials; private final Answer[] answers; private int next;
    private final long seed;

    public AbSession(long seed, int bands, int realPerBand, int nullPerBand) {
        this.seed = seed;
        trials = plan(seed, bands, realPerBand, nullPerBand);
        answers = new Answer[trials.size()];
    }
    public static List<Trial> plan(long seed, int bands, int realPerBand, int nullPerBand) {
        List<Trial> list = new ArrayList<>();
        for (int b = 0; b < bands; b++) {
            for (int i = 0; i < realPerBand; i++) list.add(new Trial(b, false, i % 2 == 0));
            for (int i = 0; i < nullPerBand; i++) list.add(new Trial(b, true, i % 2 == 0));
        }
        java.util.Collections.shuffle(list, new Random(seed));
        return list;
    }
    public long seed() { return seed; }
    public int size() { return trials.size(); }
    public int index() { return next; }
    public boolean done() { return next >= trials.size(); }
    public Trial current() { return done() ? null : trials.get(next); }
    public void answer(Answer a) { if (!done()) answers[next++] = a; }

    public static boolean correct(Trial t, Answer a) {
        if (t.nullTrial) return a == Answer.SAME;
        if (a == Answer.SAME) return false;
        return (a == Answer.FIRST_QUIETER) == t.onFirst;
    }

    /** Roughly equal-loudness sine amplitude (full scale = 1.0), capped. Quiet by construction. */
    public static double amplitudeFor(double hz) {
        final double base = 0.03;            // about -30 dBFS at 1-4 kHz
        double weightDb;                     // crude inverse of A-weighting, limited to +12 dB / -3 dB
        if (hz < 100) weightDb = 12; else if (hz < 400) weightDb = 6; else if (hz < 6000) weightDb = 0; else weightDb = 3;
        double amp = base * Math.pow(10, weightDb / 20.0);
        return Math.min(amp, 0.12);
    }

    public Summary summary(int bandCount) { return summary(new int[bandCount]); }
    /** @param bandHz device band centre frequencies (0 = unknown) used only for labels */
    public Summary summary(int[] bandHz) {
        int bandCount = bandHz.length;
        BandResult[] r = new BandResult[bandCount];
        for (int i = 0; i < bandCount; i++) r[i] = new BandResult();
        int nulls = 0, fa = 0, answered = Math.min(next, trials.size());
        for (int i = 0; i < answered; i++) {
            Trial t = trials.get(i); Answer a = answers[i];
            if (t.band < 0 || t.band >= bandCount) continue;
            if (t.nullTrial) { nulls++; if (a != Answer.SAME) fa++; }
            else { r[t.band].real++; r[t.band].measured = true; if (correct(t, a)) r[t.band].hits++; }
        }
        StringBuilder s = new StringBuilder();
        s.append("Blind A/B result (seed ").append(seed).append(", ").append(answered).append("/").append(trials.size()).append(" trials)\n");
        for (int i = 0; i < bandCount; i++) {
            BandResult b = r[i];
            String verdict = !b.measured ? "not tested" : b.hits == b.real ? "AUDIBLE" : b.hits == 0 ? "NOT HEARD" : "INCONCLUSIVE";
            s.append("  band ").append(i).append(bandHz[i] > 0 ? " (" + bandHz[i] + " Hz)" : "").append(": ").append(b.hits).append("/").append(b.real).append(" ").append(verdict).append('\n');
        }
        s.append("  null trials: ").append(fa).append(" false alarms of ").append(nulls).append('\n');
        if (nulls > 0 && fa * 5 >= nulls * 3) s.append("  WARNING: many false alarms; this run is unreliable (noise, bias or unstable level).\n");
        s.append("  Guessing all real trials of one band correctly has probability (1/3)^n; treat 2 trials as indicative only.\n");
        return new Summary(r, nulls, fa, s.toString());
    }
}
