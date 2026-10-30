package com.autotuneeq.mvp;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;

public final class CoreRegressionTest {
    @Test public void logInterpolationAndHeadroom() {
        Profile p=new Profile(new double[]{3,1,0,-3,2},"test");
        assertEquals(2,EqMath.interpolate(p,Math.sqrt(80*250)),1e-9);
        assertEquals(-1,EqMath.cutOnlyDb(p,Math.sqrt(80*250)),1e-9);
        assertEquals(-600,EqMath.deviceMillibels(p,4000,(short)-1500,(short)1500));
        assertEquals(-200,EqMath.deviceMillibels(p,4000,(short)-200,(short)1500));
        for(int hz=20;hz<=20000;hz++) {
            short value=EqMath.deviceMillibels(p,hz,(short)-300,(short)900);
            assertTrue(value<=0 && value>=-300);
        }
        assertEquals(3,EqMath.interpolate(p,1),0);
        assertEquals(2,EqMath.interpolate(p,24000),0);
        assertEquals(0,EqMath.interpolate(p,Double.NaN),0);
    }
    @Test public void flatAndAllCutsPreserveHeadroom() {
        assertEquals(0,EqMath.cutOnlyDb(Profile.flat(),80),0);
        Profile p=new Profile(new double[]{-3,-2,-1,-2,-3},"test");
        assertEquals(-1,EqMath.cutOnlyDb(p,1000),0);
    }
    @Test(expected=IllegalArgumentException.class) public void positiveOnlyHardwareRejected() {
        EqMath.deviceMillibels(Profile.flat(),80,(short)100,(short)300);
    }
    @Test(expected=IllegalArgumentException.class) public void reversedHardwareRejected() {
        EqMath.deviceMillibels(Profile.flat(),80,(short)300,(short)-300);
    }
    @Test public void recordingIdentityIsConservativeAndUnambiguous() {
        assertEquals(TrackIdentity.key("Song (Official Video)"," Artist "),TrackIdentity.key("song","artist"));
        assertNotEquals(TrackIdentity.key("Song (Remix)","A"),TrackIdentity.key("Song","A"));
        assertNotEquals(TrackIdentity.key("b|c","a"),TrackIdentity.key("c","a|b"));
        assertNotEquals(TrackIdentity.key("Song (Official Video]","A"),TrackIdentity.key("Song","A"));
        assertEquals(TrackIdentity.key("Cafe\u0301\u00a0Song","A"),TrackIdentity.key("Café Song","A"));
    }
    @Test public void hostileProfilesRejectedWithRealJson() throws Exception {
        for(String bands:new String[]{"[0,0,0,0]","[0,0,0,0,99]","[0,0,0,0,\"1\"]","[0,0,0,0,true]","[0,0,0,0,null]","[0,0,0,0,1e999]"}) {
            try { Profile.fromJson(new JSONObject("{\"bands_db\":"+bands+"}")); fail(bands); }
            catch(org.json.JSONException expected) { }
        }
        Profile p=new Profile(new double[]{3,1,0,-1,-3},"curated");
        assertArrayEquals(p.db,Profile.fromJson(new JSONObject(p.toJson().toString())).db,0);
    }
    @Test public void endpointCannotSmuggleQueryOrCredentials() {
        assertTrue(CloudClient.validEndpoint("https://example.org:8443/"));
        for(String s:new String[]{"http://example.org","https://user:secret@example.org","https://example.org?token=x","https://example.org/#fragment","https://example.org/path","https://example.org:0","https://"})
            assertFalse(s,CloudClient.validEndpoint(s));
    }
    @Test public void spectrumNeverGeneratesEqEvenForExtremeInput() {
        byte[] fft=new byte[1024]; java.util.Arrays.fill(fft,(byte)127);
        BandAnalyzer a=new BandAnalyzer();
        for(int frame=0;frame<20;frame++) {
            BandAnalyzer.Result result=a.analyze(fft,44100000);
            assertArrayEquals(new double[5],result.suggestion.db,0);
            for(float level:result.levels) assertTrue(Float.isFinite(level) && level>=0 && level<=1);
        }
        assertNull(a.analyze(new byte[16],44100000));
        assertNull(a.analyze(fft,0));
    }
}
