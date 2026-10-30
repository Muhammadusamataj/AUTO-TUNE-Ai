package com.autotuneeq.mvp;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import java.util.concurrent.atomic.AtomicBoolean;

/** Original test tone: only this app's audio, never copies streamed songs. */
public final class TonePlayer {
    private AudioTrack track;
    private Thread writer;
    private AtomicBoolean playing = new AtomicBoolean(false);

    public synchronized int start() {
        stop();
        final int sampleRate = 44100;
        int min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (min <= 0) throw new IllegalStateException("AudioTrack initialization unsupported");
        int buffer = Math.max(8192, min * 2);
        track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, buffer, AudioTrack.MODE_STREAM);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release(); track=null; throw new IllegalStateException("AudioTrack not initialized");
        }
        int session = track.getAudioSessionId();
        final AtomicBoolean run=new AtomicBoolean(true);
        playing=run;
        final AudioTrack output = track;
        writer = new Thread(() -> {
            try {
                output.play();
                short[] block = new short[1024];
                long position = 0;
                while (run.get()) {
                    for (int i=0; i<block.length; i++) {
                        double time = (position++) / (double)sampleRate;
                        // Quiet chord / bass test signal to validate session-based EQ.
                        double sample = 0.12*Math.sin(2*Math.PI*110*time)
                                + 0.06*Math.sin(2*Math.PI*440*time)
                                + 0.03*Math.sin(2*Math.PI*1760*time);
                        block[i] = (short)(sample*32767);
                    }
                    int offset=0;
                    while(run.get() && offset<block.length) {
                        int written=output.write(block,offset,block.length-offset,AudioTrack.WRITE_NON_BLOCKING);
                        if(written<0) { run.set(false); break; }
                        if(written==0) Thread.sleep(5);
                        else offset+=written;
                    }
                }
            } catch (Exception ignored) { }
        }, "autotune-tone-player");
        writer.start();
        return session;
    }

    public synchronized void stop() {
        playing.set(false);
        if (track != null) {
            try { track.pause(); track.flush(); track.stop(); } catch (Exception ignored) { }

        }
        Thread oldWriter = writer;
        writer=null;
        if (oldWriter != null && oldWriter != Thread.currentThread()) {
            try { oldWriter.join(400); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if(track!=null) {
            try { track.release(); } catch(Exception ignored) { }
            track=null;
        }
    }
}
