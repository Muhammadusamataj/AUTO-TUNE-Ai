package com.autotuneeq.mvp;

import android.media.audiofx.Visualizer;
import android.os.SystemClock;

/** Output mix capture is diagnostic only; hardware and player may block it. */
public final class OutputAnalyzer {
    public interface Listener {
        void onResult(BandAnalyzer.Result result);
        void onError(String message);
    }
    private Visualizer visualizer;
    private volatile long previousFrame = 0;
    private BandAnalyzer analyzer = new BandAnalyzer();
    private volatile int generation;

    public synchronized boolean start(int session, final Listener listener) {
        stop();
        final int token=generation;
        final BandAnalyzer currentAnalyzer=new BandAnalyzer();
        analyzer=currentAnalyzer; previousFrame=0;
        Visualizer candidate = null;
        try {
            candidate = new Visualizer(session);
            int[] range = Visualizer.getCaptureSizeRange();
            int chosen = Math.min(1024, range[1]);
            while (chosen > range[0] && (chosen & (chosen - 1)) != 0) chosen--;
            if (chosen < range[0]) chosen = range[0];
            if(candidate.setCaptureSize(chosen)!=Visualizer.SUCCESS) throw new IllegalStateException("Capture size rejected");
            int rate = Math.min(10000, Visualizer.getMaxCaptureRate());
            if(rate<=0) throw new IllegalStateException("Invalid capture rate");
            int response = candidate.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] wave, int rate) { }
                @Override public void onFftDataCapture(Visualizer v, byte[] fft, int rate) {
                    if(token!=generation) return;
                    long now = SystemClock.elapsedRealtime();
                    if (now - previousFrame < 250) return;
                    previousFrame = now;
                    BandAnalyzer.Result r = currentAnalyzer.analyze(fft, rate);
                    if (r != null) listener.onResult(r);
                }
            }, rate, false, true);
            if (response != Visualizer.SUCCESS) {
                candidate.release();
                listener.onError("FFT listener error code: " + response);
                return false;
            }
            if(candidate.setEnabled(true)!=Visualizer.SUCCESS) throw new IllegalStateException("Capture enable rejected");
            visualizer = candidate;
            return true;
        } catch (RuntimeException | LinkageError e) {
            if (candidate != null) {
                try { candidate.release(); } catch (Exception ignored) { }
            }
            listener.onError("Visualizer not supported on this audio route: " + e.getClass().getSimpleName());
            return false;
        }
    }

    public synchronized void stop() {
        generation++;
        if (visualizer == null) return;
        try { visualizer.setEnabled(false); } catch (Exception ignored) { }
        try { visualizer.release(); } catch (Exception ignored) { }
        visualizer = null;
    }
}
