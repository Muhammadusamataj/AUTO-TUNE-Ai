package com.autotuneeq.mvp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single quiet sine with a smoothed on/off gate (about 8 ms ramps, no clicks) for the blind A/B check.
 * Amplitude is hard-capped; this app's own AudioTrack only; never reads or copies other audio.
 */
public final class AbTonePlayer {
    public static final double MAX_AMPLITUDE = 0.12;   // about -18 dBFS
    private static final int RATE = 44100;
    private AudioTrack track; private Thread writer; private AtomicBoolean run = new AtomicBoolean(false);
    private volatile double freq = 1000, amp = 0.03; private volatile boolean gate;

    public void setTone(double hz, double amplitude) {
        freq = Math.max(20, Math.min(20000, hz)); amp = Math.max(0, Math.min(MAX_AMPLITUDE, amplitude));
    }
    public void setGate(boolean on) { gate = on; }

    public synchronized int start() {
        stop();
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IllegalStateException("AudioTrack unsupported");
        track = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, Math.max(8192, min * 2), AudioTrack.MODE_STREAM);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) { track.release(); track = null; throw new IllegalStateException("AudioTrack not initialized"); }
        final int session = track.getAudioSessionId();
        final AudioTrack out = track; final AtomicBoolean flag = new AtomicBoolean(true); run = flag; gate = false;
        writer = new Thread(() -> {
            try {
                out.play();
                short[] block = new short[512]; double phase = 0, g = 0;
                final double k = 1 - Math.exp(-1.0 / (0.008 * RATE));
                while (flag.get()) {
                    double step = 2 * Math.PI * freq / RATE, a = amp, target = gate ? 1 : 0;
                    for (int i = 0; i < block.length; i++) {
                        g += (target - g) * k; phase += step; if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
                        block[i] = (short) Math.round(Math.sin(phase) * a * g * 32767);
                    }
                    int off = 0;
                    while (flag.get() && off < block.length) {
                        int w = out.write(block, off, block.length - off, AudioTrack.WRITE_NON_BLOCKING);
                        if (w < 0) { flag.set(false); break; }
                        if (w == 0) Thread.sleep(4); else off += w;
                    }
                }
            } catch (Exception ignored) { }
        }, "autotune-ab-tone");
        writer.start();
        return session;
    }

    public synchronized void stop() {
        run.set(false); gate = false;
        if (track != null) { try { track.pause(); track.flush(); track.stop(); } catch (Exception ignored) { } }
        Thread old = writer; writer = null;
        if (old != null && old != Thread.currentThread()) { try { old.join(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        if (track != null) { try { track.release(); } catch (Exception ignored) { } track = null; }
    }
}
