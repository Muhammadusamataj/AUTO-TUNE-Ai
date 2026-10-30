package android.media.audiofx;

/** Deterministic fake for controller lifecycle tests, never included in an APK. */
public final class Equalizer {
    public static Equalizer last;
    public static boolean denyControl, failBandOnce, failEnable, ignoreBandWrites;
    public boolean released, enabled=true, control=true;
    public final short[] levels={100,200};
    public Equalizer(int priority,int session) { last=this; control=!denyControl; }
    public boolean hasControl() { return control; }
    public boolean getEnabled() { return enabled; }
    public int setEnabled(boolean value) {
        if(value && failEnable) return -1;
        enabled=value; return 0;
    }
    public short getNumberOfBands() { return 2; }
    public short getBandLevel(short b) { return levels[b]; }
    public short[] getBandLevelRange() { return new short[]{-300,300}; }
    public int getCenterFreq(short b) { return b==0?80000:4000000; }
    public void setBandLevel(short b,short value) {
        if(failBandOnce && b==1) { failBandOnce=false; throw new IllegalStateException("simulated lost route"); }
        if(ignoreBandWrites) return;
        levels[b]=value;
    }
    public void release() { released=true; }
}
