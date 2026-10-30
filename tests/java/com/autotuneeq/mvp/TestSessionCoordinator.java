package com.autotuneeq.mvp;

import android.media.audiofx.Equalizer;
import java.util.*;

/** Lifecycle regression harness uses fake native effects. No real player/acoustic claims. */
public final class TestSessionCoordinator {
    static void check(boolean b,String message) { if(!b) throw new AssertionError(message); }
    static Set<String> live(String...pkgs) { return new HashSet<>(Arrays.asList(pkgs)); }
    public static void main(String[] args) {
        EqController eq=new EqController(); SessionCoordinator c=new SessionCoordinator(eq);
        Profile p=new Profile(new double[]{3,0,0,-3,0},"A");
        check(c.open(7,"a",0),"early OPEN accepted");
        c.reconcile("",live(),p,100); check(eq.sessionCount()==0 && c.pending()==1,"early OPEN survives no media yet");
        c.reconcile("a",live("a"),p,200); Equalizer a=Equalizer.last;
        check(eq.hasSession(7),"attach once playing");
        c.reconcile("",live("a"),Profile.flat(),300);
        check(eq.hasSession(7) && !a.released && a.levels[1]==-300,"pause retains effect/profile");
        check(PlaybackPolicy.retainsSession(6) && PlaybackPolicy.retainsSession(2),"buffering/pause retained");
        check(!PlaybackPolicy.retainsSession(1) && !PlaybackPolicy.retainsSession(7),"stopped/error not retained");
        c.reconcile("a",live("a"),p,400); check(eq.hasSession(7) && Equalizer.last==a,"resume without OPEN uses existing effect");
        c.reconcile("a",live("a"),Profile.flat(),500); check(a.levels[1]==0,"same-player track switch applies neutral profile");
        c.open(8,"b",600); c.reconcile("b",live("a","b"),p,700);
        check(!eq.hasSession(7) && eq.hasSession(8) && a.released,"switch bypasses previous player");
        c.reconcile("a",live("a","b"),p,800);
        check(eq.hasSession(7) && !eq.hasSession(8),"switch back reattaches known live session without OPEN");
        check(!c.open(7,"forged",900),"conflicting package claim rejected");
        c.close(7,"forged"); check(eq.hasSession(7),"wrong-package CLOSE ignored");
        c.close(7,"a"); c.reconcile("a",live("a","b"),p,1000);
        check(!eq.hasSession(7),"CLOSE cancels retained hint and never resurrects");
        c.reconcile("",live(),p,1100); check(eq.sessionCount()==0 && c.known()==0,"destroy/stop cleans known sessions");
        c.open(9,"a",1200); c.reconcile("a",live("a"),p,1300); c.reconcile("",null,p,1400);
        check(eq.sessionCount()==0 && c.known()==0 && c.pending()==0,"access loss clears even early hints");
        Equalizer.denyControl=true;
        c.open(10,"a",2000); c.reconcile("a",live("a"),p,2000);
        Equalizer failed=Equalizer.last;
        c.open(10,"a",2100); c.reconcile("a",live("a"),p,2100);
        check(Equalizer.last==failed,"duplicate OPEN cannot reset backoff");
        Equalizer.denyControl=false; c.reconcile("a",live("a"),p,4500);
        check(eq.hasSession(10),"transient attach denial retried after backoff");
        // --- 0.3.4 rev: spoofed-claim exhaustion ---
        {
            EqController e2=new EqController(); SessionCoordinator f=new SessionCoordinator(e2);
            check(f.open(1,"x",0) && f.open(2,"x",0) && f.open(3,"x",0),"one package may hold three claims");
            check(!f.open(4,"x",0),"fourth claim from the same package rejected");
            for(int i=0;i<13;i++) f.open(100+i,"fake"+i,10);
            check(f.known()==16 && !f.open(500,"legit",20),"table full of unverified claims blocks a new one (bounded, not permanent)");
            f.reconcile("",live(),p,SessionCoordinator.UNOBSERVED_TTL_MS+1000);
            check(f.known()==0,"claims from packages that never showed a media session expire after 90 s");
            check(f.open(500,"legit",SessionCoordinator.UNOBSERVED_TTL_MS+2000),"legit OPEN accepted again after expiry");
            f.reconcile("legit",live("legit"),p,SessionCoordinator.UNOBSERVED_TTL_MS+3000);
            check(e2.hasSession(500),"legit player with a live media session attaches");
            f.reconcile("other",live("legit","other"),p,SessionCoordinator.UNOBSERVED_TTL_MS+4000);
            f.reconcile("other",live("legit","other"),p,SessionCoordinator.UNOBSERVED_TTL_MS+4000+SessionCoordinator.UNOBSERVED_TTL_MS+5000);
            check(f.known()==1,"claim of an observed (live-session) player survives beyond the 90 s limit");
            f.clear();
        }
        c.clear();
        System.out.println("PASS: early OPEN, pause/resume/buffering, neutral track switch, player switch/back, CLOSE, access/stop cleanup, retry and conflicts (simulated hardware)");
    }
}
