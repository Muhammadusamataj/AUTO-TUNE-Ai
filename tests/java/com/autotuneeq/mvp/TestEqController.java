package com.autotuneeq.mvp;
import android.media.audiofx.Equalizer;

/** Fault-injection regressions, not a hardware audio test. */
public final class TestEqController {
    private static void check(boolean ok,String text) { if(!ok) throw new AssertionError(text); }
    public static void main(String[] args) {
        EqController c=new EqController();
        c.attachExternal(7,"player",new Profile(new double[]{3,0,0,-3,0},"test"));
        Equalizer first=Equalizer.last;
        check(c.sessionCount()==1 && first.levels[0]<=0 && first.levels[1]<=0,"safe attach");
        c.attachExternal(7,"spoof",Profile.flat());
        check(Equalizer.last==first && !first.released,"conflicting association must not replace effect");
        c.releaseExternalIfPackage(7,"spoof");
        check(c.sessionCount()==1,"wrong-package close ignored");
        c.releaseExternalIfPackage(7,"player");
        check(first.released && first.enabled && first.levels[0]==100 && first.levels[1]==200,"restore original settings");
        Equalizer.failBandOnce=true;
        c.attachExternal(8,"player",Profile.flat());
        check(c.sessionCount()==0 && Equalizer.last.released && Equalizer.last.levels[0]==100,"partial apply rolls back and releases");
        Equalizer.failEnable=true;
        c.attachOwned(9);
        check(Equalizer.last.released,"failed enable releases owned candidate");
        Equalizer.failEnable=false; Equalizer.denyControl=true;
        c.attachExternal(10,"player",Profile.flat());
        check(c.sessionCount()==0 && Equalizer.last.released,"denied control releases candidate");
        Equalizer.denyControl=false;
        c.attachExternal(11,"player",Profile.flat());
        Equalizer.last.control=false;
        String result=c.applyForPackage("player",Profile.flat());
        check(!result.contains("EQ applied") && c.sessionCount()==0 && Equalizer.last.released,"control loss never reported as apply");
        // --- 0.3.4: raw single-band hardware test hooks used by the blind A/B check ---
        EqController t=new EqController();
        check(t.ownedBandFreqsHz()==null && !t.ownedBypass() && !t.ownedTestCut(0,600).ok,"no effect attached -> hooks fail closed");
        t.attachOwned(21); Equalizer own=Equalizer.last;
        int[] hz=t.ownedBandFreqsHz(); check(hz!=null && hz.length==2 && hz[0]==80 && hz[1]==4000,"band frequencies in Hz");
        EqController.TestCut cut=t.ownedTestCut(1,600);
        check(cut.ok && cut.requested[0]==0 && cut.requested[1]==-300 && own.levels[1]==-300 && own.enabled,"cut limited to device attenuation range, other bands 0, read-back ok");
        check(!t.ownedTestCut(2,100).ok && !t.ownedTestCut(-1,100).ok && !t.ownedTestCut(0,-100).ok,"bad band / negative (boost) request rejected");
        check(t.ownedTestCut(0,0).ok && own.levels[0]==0 && own.levels[1]==0,"null trial: 0 dB everywhere");
        check(t.ownedBypass() && !own.enabled,"bypass disables without releasing");
        Equalizer.ignoreBandWrites=true; own.levels[1]=0;
        EqController.TestCut ignored=t.ownedTestCut(1,200);
        check(!ignored.ok && ignored.message.contains("MISMATCH"),"engine ignoring writes is reported by read-back");
        Equalizer.ignoreBandWrites=false; own.control=false;
        check(!t.ownedTestCut(1,200).ok,"lost control reported"); own.control=true;
        t.releaseOwned(); check(own.released && t.ownedBandFreqsHz()==null,"owned effect released");
        // --- 0.3.4 rev: never overwrite settings changed by someone else after our apply ---
        EqController g=new EqController();
        g.attachExternal(31,"player",new Profile(new double[]{3,0,0,-3,0},"test")); Equalizer ext=Equalizer.last;
        ext.levels[0]=55;                                   // the player/another EQ app changes a band after us
        g.releaseExternal(31);
        check(ext.released && ext.levels[0]==55,"externally modified bands are left alone on release");
        g.attachExternal(32,"player",Profile.flat()); Equalizer own2=Equalizer.last;
        g.releaseExternal(32);
        check(own2.released && own2.levels[0]==100 && own2.levels[1]==200,"unchanged bands still restored to the original snapshot");
        System.out.println("PASS: simulated EQ restore, association checks, apply/enable failures, denied/lost control cleanup (hardware untested)");
    }
}
