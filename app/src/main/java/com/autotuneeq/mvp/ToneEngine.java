package com.autotuneeq.mvp;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

/** Activity-scoped own-tone audio worker. No permissions/capture/third-party sessions. */
public final class ToneEngine {
    public interface Listener { void status(String text); }
    private final HandlerThread thread=new HandlerThread("autotune-own-audio");
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Handler worker;
    private final TonePlayer tone=new TonePlayer();
    private final EqController eq=new EqController();
    private final Listener listener;
    private volatile int generation;
    private volatile boolean closed, playing;
    private volatile Profile pending=Profile.flat();
    public ToneEngine(Listener l) { listener=l; thread.start(); worker=new Handler(thread.getLooper()); }
    private void report(int token,String text) {
        ui.post(() -> { if(!closed && generation==token) listener.status(text); });
    }
    public void play(Profile p) {
        if(closed) return;
        pending=p; final int token=++generation;
        worker.post(() -> {
            if(closed || token!=generation) return;
            try {
                eq.releaseOwned(); int id=tone.start();
                if(closed || token!=generation) { tone.stop(); return; }
                String attached=eq.attachOwned(id); String result=eq.applyOwned(p);
                if(closed || token!=generation) { eq.releaseOwned(); tone.stop(); return; }
                playing=true; report(token,attached+"\n"+result);
            } catch(RuntimeException e) {
                playing=false; eq.releaseOwned(); tone.stop(); report(token,"Tone unavailable: "+e.getClass().getSimpleName());
            }
        });
    }
    private final Runnable apply=() -> {
        int token=this.generation;
        if(this.playing && !this.closed) report(token,this.eq.applyOwned(this.pending));
    };
    public void setProfile(Profile p) {
        if(closed) return; pending=p; worker.removeCallbacks(apply); worker.postDelayed(apply,80);
    }
    public void stop() {
        if(closed) return; final int token=++generation; playing=false; worker.removeCallbacks(apply);
        worker.post(() -> { playing=false; eq.releaseOwned(); tone.stop(); report(token,"Own-session EQ stopped"); });
    }
    public void close() {
        if(closed) return; closed=true; generation++; playing=false; worker.removeCallbacks(apply);
        ui.removeCallbacksAndMessages(null);
        worker.post(() -> { playing=false; eq.releaseOwned(); tone.stop(); thread.quitSafely(); });
    }
}
