package com.autotuneeq.mvp;

/** Values are public PlaybackState constants. NONE/STOPPED/ERROR never retain effects. */
public final class PlaybackPolicy {
    private PlaybackPolicy() { }
    public static boolean retainsSession(int state) {
        return state==2 || state==3 || state==4 || state==5 || state==6
                || state==8 || state==9 || state==10 || state==11;
    }
}
