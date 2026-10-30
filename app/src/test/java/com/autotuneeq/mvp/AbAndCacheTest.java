package com.autotuneeq.mvp;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Pure-Java logic only: listening-test plan/scoring, negative cache and cache eviction. No audio, no device. */
public final class AbAndCacheTest {
    @Test public void planIsBalancedAndDeterministic() {
        List<AbSession.Trial> a=AbSession.plan(42,5,4,1), b=AbSession.plan(42,5,4,1);
        assertEquals(25,a.size());
        for(int i=0;i<a.size();i++) { assertEquals(a.get(i).band,b.get(i).band); assertEquals(a.get(i).onFirst,b.get(i).onFirst); assertEquals(a.get(i).nullTrial,b.get(i).nullTrial); }
        int[] real=new int[5], nulls=new int[5], onFirst=new int[5];
        for(AbSession.Trial t:a) { if(t.nullTrial) nulls[t.band]++; else { real[t.band]++; if(t.onFirst) onFirst[t.band]++; } }
        for(int i=0;i<5;i++) { assertEquals(4,real[i]); assertEquals(1,nulls[i]); assertEquals(2,onFirst[i]); } // 4 real trials: exactly 2 ON-first / 2 ON-second
        boolean differs=false; List<AbSession.Trial> c=AbSession.plan(7,5,4,1);
        for(int i=0;i<a.size();i++) if(a.get(i).band!=c.get(i).band) differs=true;
        assertTrue(differs);
    }
    private static AbSession.Answer perfect(AbSession.Trial t) {
        if(t.nullTrial) return AbSession.Answer.SAME;
        return t.onFirst?AbSession.Answer.FIRST_QUIETER:AbSession.Answer.SECOND_QUIETER;
    }
    @Test public void perfectListenerHearsEveryBandAndNoFalseAlarms() {
        AbSession s=new AbSession(1,5,4,1);
        while(!s.done()) s.answer(perfect(s.current()));
        AbSession.Summary sm=s.summary(5);
        for(AbSession.BandResult r:sm.bands) { assertEquals(4,r.real); assertEquals(4,r.hits); }
        assertEquals(0,sm.falseAlarms); assertEquals(5,sm.nullTrials);
        assertTrue(sm.text.contains("AUDIBLE")); assertFalse(sm.text.contains("WARNING"));
    }
    @Test public void listenerWhoHearsNothingIsReportedNotHeardAndSameIsAMissOnRealTrials() {
        AbSession s=new AbSession(2,5,4,1);
        while(!s.done()) s.answer(AbSession.Answer.SAME);
        AbSession.Summary sm=s.summary(5);
        for(AbSession.BandResult r:sm.bands) assertEquals(0,r.hits);
        assertEquals(0,sm.falseAlarms); assertTrue(sm.text.contains("NOT HEARD"));
    }
    @Test public void alwaysFirstAnswerIsFlaggedByNullTrials() {
        AbSession s=new AbSession(3,5,4,1);
        while(!s.done()) s.answer(AbSession.Answer.FIRST_QUIETER);
        AbSession.Summary sm=s.summary(5);
        assertEquals(5,sm.falseAlarms); assertTrue(sm.text.contains("WARNING"));
        int hits=0; for(AbSession.BandResult r:sm.bands) hits+=r.hits;
        assertEquals(10,hits); // exactly half of the real trials per band have the cut first: a side-biased listener scores 2/4 (INCONCLUSIVE), and null trials flag the run
        assertFalse(sm.text.contains("AUDIBLE"));
    }
    @Test public void correctnessTable() {
        List<AbSession.Trial> t=AbSession.plan(5,1,2,1); AbSession.Trial real=null, nul=null;
        for(AbSession.Trial x:t) { if(x.nullTrial) nul=x; else if(real==null) real=x; }
        assertTrue(AbSession.correct(nul,AbSession.Answer.SAME)); assertFalse(AbSession.correct(nul,AbSession.Answer.FIRST_QUIETER));
        assertFalse(AbSession.correct(real,AbSession.Answer.SAME)); assertTrue(AbSession.correct(real,perfect(real)));
    }
    @Test public void testToneIsQuietAndCapped() {
        for(int hz=20;hz<=20000;hz+=7) { double a=AbSession.amplitudeFor(hz); assertTrue(a>0 && a<=0.12 && a<=AbTonePlayer.MAX_AMPLITUDE); }
        assertTrue(AbSession.amplitudeFor(1000)<=0.03+1e-9);
    }
    @Test public void missCacheExpiresPersistsAndIsBounded() {
        MissCache c=new MissCache(); c.record("k",1000);
        assertTrue(c.isMiss("k",1000+MissCache.TTL_MS)); assertFalse(c.isMiss("k",1001+MissCache.TTL_MS));
        c.record("a",5); MissCache r=MissCache.parse(c.serialize()); assertTrue(r.isMiss("a",6));
        MissCache big=new MissCache(); for(int i=0;i<MissCache.MAX_ENTRIES+50;i++) big.record("k"+i,i);
        assertEquals(MissCache.MAX_ENTRIES,big.size()); assertFalse(big.isMiss("k0",100)); assertTrue(big.isMiss("k"+(MissCache.MAX_ENTRIES+49),300));
        assertEquals(0,MissCache.parse("garbage\n\tx\nabc\tdef\n").size());
        big.forget("k60"); assertFalse(big.isMiss("k60",100));
        assertFalse(new MissCache().isMiss("x",Long.MAX_VALUE/2));
    }
    @Test public void evictionRemovesOldestUnpinnedBeforeAnyManualProfile() {
        Map<String,Long> t=new LinkedHashMap<>(); t.put("m1",1L); t.put("c1",2L); t.put("c2",3L); t.put("m2",4L);
        List<String> v=CachePolicy.victims(t,Arrays.asList("m1","m2"),4);   // capacity 4 after insert -> remove 1
        assertEquals(Arrays.asList("c1"),v);
        v=CachePolicy.victims(t,Arrays.asList("m1","m2"),2);                // remove 3: both cloud, then oldest manual
        assertEquals(Arrays.asList("c1","c2","m1"),v);
        assertTrue(CachePolicy.victims(t,Collections.<String>emptyList(),10).isEmpty());
        Map<String,Long> allPinned=new HashMap<>(); allPinned.put("a",9L); allPinned.put("b",3L);
        assertEquals(Arrays.asList("b"),CachePolicy.victims(allPinned,Arrays.asList("a","b"),2));
    }
}
