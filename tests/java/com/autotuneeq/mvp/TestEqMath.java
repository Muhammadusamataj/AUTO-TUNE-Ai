package com.autotuneeq.mvp;

public final class TestEqMath {
    public static void main(String[] args) {
        Profile p = new Profile(new double[]{3,1,0,-1,2}, "unit");
        int[] points = {40,80,120,250,500,1000,2000,4000,8000,10000,16000};
        for (int hz : points) {
            double gain = EqMath.cutOnlyDb(p,hz);
            if (gain > 0.000001 || gain < -6.000001)
                throw new AssertionError("cut-only bound failed at " + hz + " Hz: " + gain);
            short mb=EqMath.deviceMillibels(p,hz,(short)-1500,(short)1500);
            if(mb > 0 || mb < -1500) throw new AssertionError("device gain out of range");
        }
        if (EqMath.deviceMillibels(p,80,(short)-1500,(short)1500) != 0)
            throw new AssertionError("peak should be zero dB after shifting");
        if (EqMath.deviceMillibels(p,4000,(short)-1500,(short)1500) != -400)
            throw new AssertionError("-1 vs +3 difference should be -4 dB");
        Profile flat = Profile.flat();
        for (int hz : points) if (EqMath.deviceMillibels(flat,hz,(short)-600,(short)600)!=0)
            throw new AssertionError("flat must stay neutral");
        if (!TrackIdentity.key("Song (Official Video)","Artist")
                    .equals(TrackIdentity.key("song"," artist ")))
            throw new AssertionError("promotional suffix should normalize");
        if (TrackIdentity.key("Song (Remix)","Artist")
                    .equals(TrackIdentity.key("song","Artist")))
            throw new AssertionError("remixes must remain distinct");
        System.out.println("PASS: cut-only gain, hardware limits, interpolation, identity normalization");
    }
}
