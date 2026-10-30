package com.autotuneeq.mvp;

public class TestBandAnalyzer {
    public static void main(String[] args) throws Exception {
        Profile p=new Profile(new double[]{-40, -2, 0, 2, 100},"test");
        if(p.db[0]!=-3 || p.db[4]!=3) throw new AssertionError("gain clipping failed");
        if(Profile.fromJson(p.toJson()).db[4]!=3) throw new AssertionError("profile JSON roundtrip failed");
        BandAnalyzer a=new BandAnalyzer();
        byte[] fft=new byte[1024];
        for(int i=2;i<fft.length;i+=2) fft[i]=(byte)(i%10==0?80:25);
        BandAnalyzer.Result result=null;
        for(int t=0;t<16;t++) result=a.analyze(fft, 44100000);
        if(result==null || !result.confident) throw new AssertionError("spectrum detect failed");
        if(result.levels.length!=5 || result.suggestion.db.length!=5) throw new AssertionError("band count failed");
        for(double d: result.suggestion.db) if(d < -3 || d > 3) throw new AssertionError("unsafe gain");
        if(a.analyze(null,44100000)!=null) throw new AssertionError("null FFT should fail safely");
        System.out.println("PASS: profile clamp, JSON roundtrip, FFT summary, safe gain limits, invalid input");
    }
}
