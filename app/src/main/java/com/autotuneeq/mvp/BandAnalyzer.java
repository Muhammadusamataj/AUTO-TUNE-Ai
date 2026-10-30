package com.autotuneeq.mvp;

/** Low-resolution output spectrum visualization only. Never infer corrective EQ from it. */
public final class BandAnalyzer {
    private static final int[] EDGES={30,140,500,2000,6500,20000};
    private final double[] smoothed=new double[5];
    private boolean initialized=false;
    private int frames=0;
    public synchronized Result analyze(byte[] fft,int sampleRateMilliHz) {
        if(fft==null || fft.length<64 || sampleRateMilliHz<=0) return null;
        int n=fft.length;
        double fs=sampleRateMilliHz/1000.0;
        double[] sums=new double[5];
        int[] counts=new int[5];
        for(int k=1;k<n/2;k++) {
            double hz=k*fs/n;
            int b=-1;
            for(int i=0;i<5;i++) if(hz>=EDGES[i]&&hz<EDGES[i+1]) {b=i;break;}
            if(b<0) continue;
            int re=fft[2*k]; int im=fft[2*k+1];
            sums[b]+=Math.log1p(Math.hypot(re,im));
            counts[b]++;
        }
        float[] levels=new float[5];
        for(int b=0;b<5;b++) {
            double value=counts[b]>0?sums[b]/counts[b]:0;
            smoothed[b]=initialized?0.87*smoothed[b]+0.13*value:value;
            levels[b]=(float)Math.max(0,Math.min(1,smoothed[b]/3.8));
        }
        initialized=true;
        frames++;
        return new Result(levels,Profile.flat(),frames>=10);
    }
    public static final class Result {
        public final float[] levels;
        /** Neutral on purpose: spectrum has no objective song mastering target. */
        public final Profile suggestion;
        public final boolean confident;
        public Result(float[] l,Profile p,boolean c) {levels=l;suggestion=p;confident=c;}
    }
}
