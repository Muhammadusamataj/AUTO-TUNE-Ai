package com.autotuneeq.mvp;

import android.media.audiofx.Equalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Opt-in effects only. Control and submitted settings never prove audible processing. */
public final class EqController {
    private Equalizer owned;
    private SessionEffect global;
    private final Map<Integer,SessionEffect> sessions=new HashMap<>();
    private static final class SessionEffect {
        final Equalizer eq;
        final String packageName;
        final short[] levels;
        final boolean enabled;
        /** Levels this app last wrote; used to avoid overwriting settings changed by someone else afterwards. */
        short[] applied;
        SessionEffect(Equalizer effect,String pkg) {
            eq=effect; packageName=pkg; enabled=eq.getEnabled();
            short n=eq.getNumberOfBands();
            if(n<=0) throw new IllegalStateException("No EQ bands");
            levels=new short[n];
            for(short b=0;b<n;b++) levels[b]=eq.getBandLevel(b);
        }
    }
    private static final class Applied {
        final boolean success;
        final String message;
        final short[] levels;
        Applied(boolean ok,String text) { this(ok,text,null); }
        Applied(boolean ok,String text,short[] lv) { success=ok; message=text; levels=lv; }
    }
    private static void release(Equalizer eq) {
        if(eq!=null) try { eq.release(); } catch(RuntimeException ignored) { }
    }
    private static void restore(SessionEffect s) {
        if(s==null) return;
        try {
            if(s.eq.hasControl()) {
                // If another app (or the player) changed the bands after we applied ours, do not overwrite
                // its settings with an older snapshot: just let go of the effect.
                boolean untouched=true;
                if(s.applied!=null) {
                    short[] now=new short[s.applied.length];
                    for(short b=0;b<now.length;b++) now[b]=s.eq.getBandLevel(b);
                    untouched=java.util.Arrays.equals(now,s.applied);
                }
                if(untouched) {
                    // Restore levels while bypassed to avoid transient positive boosts.
                    s.eq.setEnabled(false);
                    for(short b=0;b<s.levels.length;b++) s.eq.setBandLevel(b,s.levels[b]);
                    s.eq.setEnabled(s.enabled);
                }
            }
        } catch(RuntimeException ignored) { /* Best effort if device/session disappeared. */ }
        release(s.eq);
    }
    public synchronized String attachOwned(int session) {
        releaseOwned();
        if(session<=0) return "Invalid owned audio session";
        Equalizer candidate=null;
        try {
            candidate=new Equalizer(0,session);
            if(!candidate.hasControl()) { release(candidate); return "Effect attached · control not granted"; }
            // Prepare neutral cut-only settings before enabling this app's effect.
            Applied result=apply(candidate,Profile.flat(),true);
            if(!result.success) { release(candidate); return result.message; }
            owned=candidate;
            return "Effect attached · control granted · audible effect unverified";
        } catch(RuntimeException e) { release(candidate); return "Own EQ unavailable: "+e.getClass().getSimpleName(); }
    }
    public synchronized String applyOwned(Profile p) {
        Applied result=apply(owned,p,false);
        if(!result.success) releaseOwned();
        return result.message;
    }
    public synchronized String attachExternal(int session,String pkg,Profile p) {
        if(session<=0 || pkg==null || pkg.isEmpty()) return "Invalid external session";
        SessionEffect existing=sessions.get(session);
        if(existing!=null) {
            if(existing.packageName.equals(pkg)) {
                Applied result=apply(existing.eq,p,false);
                if(result.success) existing.applied=result.levels; else releaseExternal(session);
                return result.message;
            }
            // A conflicting package claim never replaces a live association.
            return "Conflicting package/session claim rejected";
        }
        if(sessions.size()>=8) return "External session limit reached";
        Equalizer candidate=null;
        SessionEffect saved=null;
        try {
            candidate=new Equalizer(0,session);
            if(!candidate.hasControl()) { release(candidate); return "Effect attached · control not granted"; }
            saved=new SessionEffect(candidate,pkg);
            Applied result=apply(candidate,p,true);
            if(!result.success) { restore(saved); return result.message; }
            saved.applied=result.levels;
            sessions.put(session,saved);
            return result.message;
        } catch(RuntimeException e) {
            if(saved!=null) restore(saved); else release(candidate);
            return "External EQ unavailable: "+e.getClass().getSimpleName();
        }
    }
    public synchronized void releaseExternal(int session) { restore(sessions.remove(session)); }
    public synchronized void releaseExternalIfPackage(int session,String pkg) {
        SessionEffect s=sessions.get(session);
        if(s!=null && s.packageName.equals(pkg)) releaseExternal(session);
    }
    public synchronized void releaseExternalAll() {
        for(Integer id:new ArrayList<>(sessions.keySet())) releaseExternal(id);
    }
    public synchronized void releaseExceptPackage(String pkg) {
        for(Integer id:new ArrayList<>(sessions.keySet()))
            if(!sessions.get(id).packageName.equals(pkg)) releaseExternal(id);
    }
    public synchronized int sessionCount() { return sessions.size(); }
    public synchronized boolean hasSession(int session) { return sessions.containsKey(session); }
    /** Release effects whose player no longer has any live MediaSession (stale/destroyed player). */
    public synchronized void releaseSessionsNotIn(java.util.Set<String> livePackages) {
        for(Integer id:new ArrayList<>(sessions.keySet()))
            if(!livePackages.contains(sessions.get(id).packageName)) releaseExternal(id);
    }
    public synchronized int releaseUncontrolledSessions() {
        int lost=0;
        for(Integer id:new ArrayList<>(sessions.keySet())) {
            SessionEffect s=sessions.get(id);
            try { if(s.eq.hasControl() && s.eq.getEnabled()) continue; } catch(RuntimeException ignored) { }
            releaseExternal(id); lost++;
        }
        return lost;
    }
    public synchronized String applyForPackage(String pkg,Profile p) {
        String message="No attached effect for current player";
        for(Integer id:new ArrayList<>(sessions.keySet())) {
            SessionEffect s=sessions.get(id);
            if(s.packageName.equals(pkg)) {
                Applied result=apply(s.eq,p,false); message=result.message;
                if(result.success) s.applied=result.levels; else releaseExternal(id);
            }
        }
        return message;
    }
    public synchronized boolean isGlobalRunning() { return global!=null; }
    public synchronized String startGlobalExperiment(Profile p) {
        stopGlobalExperiment();
        Equalizer candidate=null;
        try {
            candidate=new Equalizer(0,0);
            if(!candidate.hasControl()) { release(candidate); return "Legacy effect attached · control not granted"; }
            global=new SessionEffect(candidate,"legacy-session-zero");
            Applied result=apply(candidate,p,true);
            if(!result.success) stopGlobalExperiment(); else global.applied=result.levels;
            return "Legacy experiment · "+result.message;
        } catch(RuntimeException e) {
            if(global!=null) stopGlobalExperiment(); else release(candidate);
            return "Legacy EQ unavailable: "+e.getClass().getSimpleName();
        }
    }
    public synchronized String applyGlobal(Profile p) {
        Applied result=apply(global==null?null:global.eq,p,false);
        if(!result.success) stopGlobalExperiment(); else global.applied=result.levels;
        return result.message;
    }
    private static Applied apply(Equalizer eq,Profile p,boolean activate) {
        if(eq==null) return new Applied(false,"No attached EQ · recommendation only");
        try {
            if(!eq.hasControl()) return new Applied(false,"Effect attached · control not granted or lost");
            if(!activate && !eq.getEnabled()) return new Applied(false,"Effect disabled by audio system");
            short[] range=eq.getBandLevelRange();
            if(range==null || range.length<2 || range[0]>range[1] || range[0]>0)
                return new Applied(false,"Safe attenuation unsupported by this EQ");
            short n=eq.getNumberOfBands();
            if(n<=0) return new Applied(false,"EQ reports no frequency bands");
            // Compute all values before touching hardware. Clamp to actual device range.
            short[] levels=new short[n];
            for(short b=0;b<n;b++) {
                double hz=eq.getCenterFreq(b)/1000.0;
                if(hz<=0) throw new IllegalStateException("Invalid band frequency");
                levels[b]=EqMath.deviceMillibels(p,hz,range[0],range[1]);
            }
            if(activate && eq.setEnabled(false)!=0) throw new IllegalStateException("Bypass failed");
            for(short b=0;b<n;b++) eq.setBandLevel(b,levels[b]);
            if(activate && eq.setEnabled(true)!=0) throw new IllegalStateException("Enable failed");
            if(!eq.hasControl() || !eq.getEnabled()) return new Applied(false,"Effect control lost during apply");
            return new Applied(true,"Effect attached · control granted · EQ applied (not independently verified), "+n+" cut-only bands",levels);
        } catch(RuntimeException e) { return new Applied(false,"EQ apply failed: "+e.getClass().getSimpleName()); }
    }

    /** Result of a raw single-band hardware test. Read-back proves the effect engine accepted the values, not audibility. */
    public static final class TestCut {
        public final boolean ok; public final String message; public final short[] requested, readback;
        TestCut(boolean ok, String message, short[] requested, short[] readback) {
            this.ok = ok; this.message = message; this.requested = requested; this.readback = readback;
        }
        static TestCut fail(String m) { return new TestCut(false, m, new short[0], new short[0]); }
    }
    /** Band centre frequencies (Hz) of this app's own-session effect, or null when none is attached. */
    public synchronized int[] ownedBandFreqsHz() {
        if(owned==null) return null;
        try {
            short n=owned.getNumberOfBands(); int[] hz=new int[n];
            for(short b=0;b<n;b++) hz[b]=owned.getCenterFreq(b)/1000;
            return hz;
        } catch(RuntimeException e) { return null; }
    }
    /** Cut exactly one hardware band (attenuation only, never positive gain); all other bands 0 dB. */
    public synchronized TestCut ownedTestCut(int band,int cutMb) {
        Equalizer eq=owned;
        if(eq==null) return TestCut.fail("No own-session effect attached");
        try {
            if(!eq.hasControl()) return TestCut.fail("Effect control not held");
            short[] range=eq.getBandLevelRange(); short n=eq.getNumberOfBands();
            if(range==null || range.length<2 || range[0]>0 || n<=0 || band<0 || band>=n || cutMb<0)
                return TestCut.fail("Band or range unsupported");
            int cut=Math.min(cutMb,-range[0]);
            short[] req=new short[n];
            for(short b=0;b<n;b++) req[b]=(short)(b==band?-cut:0);
            if(eq.setEnabled(false)!=0) return TestCut.fail("Bypass before apply failed");
            for(short b=0;b<n;b++) eq.setBandLevel(b,req[b]);
            if(eq.setEnabled(true)!=0) return TestCut.fail("Enable failed");
            short[] rb=new short[n];
            for(short b=0;b<n;b++) rb[b]=eq.getBandLevel(b);
            boolean same=java.util.Arrays.equals(req,rb);
            boolean live=eq.getEnabled() && eq.hasControl();
            return new TestCut(same && live,
                    (same?"read-back matches":"READ-BACK MISMATCH")+(live?"":" · effect not enabled/controlled"),req,rb);
        } catch(RuntimeException e) { return TestCut.fail("Test cut failed: "+e.getClass().getSimpleName()); }
    }
    /** Disable (bypass) the own-session effect without releasing it. */
    public synchronized boolean ownedBypass() {
        if(owned==null) return false;
        try { return owned.hasControl() && owned.setEnabled(false)==0 && !owned.getEnabled(); }
        catch(RuntimeException e) { return false; }
    }
    public synchronized void releaseOwned() {
        if(owned!=null) try { owned.setEnabled(false); } catch(RuntimeException ignored) { }
        release(owned); owned=null;
    }
    public synchronized void stopGlobalExperiment() { restore(global); global=null; }
}
