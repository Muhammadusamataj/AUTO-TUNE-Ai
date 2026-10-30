package com.autotuneeq.mvp;

/** Pure-Java, testable 5-point EQ interpolation and safe cut-only gain mapping.
 * Not a limiter: playback still requires device listening tests at low volume.
 */
public final class EqMath {
    private EqMath() { }

    public static double interpolate(Profile p, double hz) {
        if (p == null || !Double.isFinite(hz) || hz <= 0) return 0;
        int[] centers = Profile.CENTERS_HZ;
        if (hz <= centers[0]) return p.db[0];
        if (hz >= centers[centers.length - 1]) return p.db[p.db.length - 1];
        for (int i = 0; i < centers.length - 1; i++) {
            if (hz <= centers[i + 1]) {
                double t = (Math.log(hz) - Math.log(centers[i])) /
                        (Math.log(centers[i + 1]) - Math.log(centers[i]));
                return p.db[i] * (1 - t) + p.db[i + 1] * t;
            }
        }
        return 0;
    }

    /** Shift whole response down by the maximum positive gain. Never output a positive gain. */
    public static double cutOnlyDb(Profile p, double hz) {
        if (p == null) return 0;
        double peak = 0;
        for (double db : p.db) peak = Math.max(peak, db);
        return Math.min(0, interpolate(p, hz) - peak);
    }

    public static short deviceMillibels(Profile p, double hz, short deviceMinMb, short deviceMaxMb) {
        if (deviceMinMb > deviceMaxMb) throw new IllegalArgumentException("bad device range");
        int mb = (int) Math.round(cutOnlyDb(p, hz) * 100.0);
        // Never ask a real device for positive gain: cap positive advertised ranges at zero.
        int allowedMax = Math.min(0, deviceMaxMb);
        if (deviceMinMb > allowedMax) throw new IllegalArgumentException("no cut-only range");
        return (short) Math.max(deviceMinMb, Math.min(allowedMax, mb));
    }
}
