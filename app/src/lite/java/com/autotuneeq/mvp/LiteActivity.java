package com.autotuneeq.mvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;

/** Explicitly limited flavor: manual identities/profiles and own-tone EQ; no media/capture service. */
public final class LiteActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private EditText title,artist,endpoint;
    private TextView status;
    private final SeekBar[] sliders=new SeekBar[5];
    private ProfileStore store;
    private CloudClient cloud;
    private ToneEngine tone;
    private int request;
    private boolean closed;
    // Blind A/B listening check (own tone only)
    private AbRig rig; private AbSession ab; private int[] bandHz; private boolean restoring;
    private TextView abStatus; private Button abFirst,abSecond,abSame,abRestore,abCopy,abStop;
    private final StringBuilder report=new StringBuilder();
    @Override public void onCreate(Bundle b) {
        super.onCreate(b); store=new ProfileStore(this); cloud=new CloudClient();
        tone=new ToneEngine(text -> status.setText(text));
        ScrollView scroll=new ScrollView(this); LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,24); scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        label(root,"AutoTune EQ AI Lite · 0.3.4 candidate",24);
        label(root,"Android 8.0+ · Manual profiles and this app's own quiet test tone. No automatic song detection, notification access, audio capture, external-player EQ or background monitor.",16);
        title=input(root,"Song title (enter manually)",""); artist=input(root,"Artist (enter manually)","");
        status=label(root,"Own-session EQ stopped · audible effect unverified",16);
        for(int i=0;i<5;i++) {
            final int index=i; TextView level=label(root,Profile.CENTERS_HZ[i]+" Hz: 0.0 dB",16);
            SeekBar slider=new SeekBar(this); slider.setMax(60); slider.setProgress(30); sliders[i]=slider;
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar s,int p,boolean user) {
                    level.setText(Profile.CENTERS_HZ[index]+" Hz: "+((p-30)/10.0)+" dB");
                    if(user) { request++; cloud.cancel(); tone.setProfile(profile()); }
                }
                public void onStartTrackingTouch(SeekBar s) { }
                public void onStopTrackingTouch(SeekBar s) { }
            }); root.addView(slider);
        }
        label(root,"Sliders are relative -3…+3 dB; hardware response shifts down to cut-only (up to 6 dB attenuation). No mastering AI or universal player control.",16);
        button(root,"Play quiet own test tone + slider EQ",() -> tone.play(profile()));
        button(root,"Stop test tone",() -> tone.stop());
        button(root,"Save manual profile offline",() -> {
            if(!identity()) return; request++; cloud.cancel();
            status.setText(store.putManual(title(),artist(),profile())?"Profile saved offline":"Could not save profile");
        });
        button(root,"Load offline profile",() -> {
            if(!identity()) return; request++; cloud.cancel();
            Profile p=store.get(title(),artist());
            if(p==null) status.setText("Profile not found offline"); else load(p,"Profile found offline");
        });
        label(root,"BLIND A/B LISTENING CHECK · own tone only",18);
        label(root,"Verifies, by ear, that the effect on this app's own audio session really changes the level of one frequency band. Use LOW volume; headphones recommended (phone speakers cannot reproduce very low or very high bands). Other audio must be paused. Each trial plays the same quiet tone twice; one may have the EQ cut applied. Identical 'null' trials are mixed in to expose guessing.",14);
        abStatus=label(root,"A/B check not started",16);
        button(root,"Start blind A/B check",this::confirmAb);
        abFirst=button(root,"1st sound was quieter",() -> answer(AbSession.Answer.FIRST_QUIETER));
        abSecond=button(root,"2nd sound was quieter",() -> answer(AbSession.Answer.SECOND_QUIETER));
        abSame=button(root,"No difference",() -> answer(AbSession.Answer.SAME));
        abRestore=button(root,"Restoration check (effect released while tone plays)",this::restoreCheck);
        abCopy=button(root,"Copy diagnostic report",this::copyReport);
        abStop=button(root,"Stop A/B check",this::stopAb);
        showAnswers(false); abRestore.setVisibility(android.view.View.GONE); abCopy.setVisibility(android.view.View.GONE);
        endpoint=input(root,"Optional HTTPS server origin",store.endpoint());
        label(root,"Optional lookup sends only the manually entered title and artist to your configured HTTPS server when you press Lookup. No raw audio is captured or uploaded. Empty server = offline.",16);
        button(root,"Save server / metadata-sharing choice",() -> {
            String value=endpoint.getText().toString().trim();
            if(!value.isEmpty() && !CloudClient.validEndpoint(value)) { status.setText("Use an HTTPS origin without credentials, path, query or fragment"); return; }
            if(value.isEmpty()) { store.setEndpoint(""); request++; cloud.cancel(); status.setText("Online lookup disabled"); return; }
            new AlertDialog.Builder(this).setTitle("Allow manual metadata lookup?")
                    .setMessage("When you press Lookup, your entered song title and artist will be sent to "+value+". No audio is uploaded. Clear the URL to disable online lookup.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Save server",(d,w) -> { store.setEndpoint(value); request++; cloud.cancel(); status.setText("Server saved; lookup remains manual"); }).show();
        });
        button(root,"Lookup entered song profile",this::lookup);
    }
    private TextView label(LinearLayout root,String value,int size) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setPadding(0,12,0,12); root.addView(t); return t;
    }
    private EditText input(LinearLayout root,String hint,String value) {
        EditText t=new EditText(this); t.setSingleLine(); t.setHint(hint); t.setText(value); root.addView(t); return t;
    }
    private Button button(LinearLayout root,String text,Runnable task) {
        Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setMinHeight(56); b.setOnClickListener(v -> task.run()); root.addView(b);
        return b;
    }
    private String title() { return title.getText().toString().trim(); }
    private String artist() { return artist.getText().toString().trim(); }
    private boolean identity() {
        if(title().isEmpty() || artist().isEmpty()) { status.setText("Enter song title and artist first"); return false; } return true;
    }
    private Profile profile() {
        double[] values=new double[5]; for(int i=0;i<5;i++) values[i]=(sliders[i].getProgress()-30)/10.0;
        return new Profile(values,"saved manual Lite profile");
    }
    private void load(Profile p,String text) {
        for(int i=0;i<5;i++) sliders[i].setProgress(30+(int)Math.round(p.db[i]*10));
        tone.setProfile(p); status.setText(text+" · "+p.describe());
    }
    private void lookup() {
        if(!identity()) return;
        final String t=title(),a=artist(),server=store.endpoint(); final int token=++request;
        status.setText("Looking up entered metadata…");
        cloud.fetch(server,t,a,(p,message) -> ui.post(() -> {
            if(closed || token!=request || !t.equals(title()) || !a.equals(artist()) || !server.equals(store.endpoint())) return;
            if(p==null) status.setText(message); else { store.put(t,a,p); load(p,message); }
        }));
    }
    // ---- Blind A/B listening check -------------------------------------------------
    private void showAnswers(boolean on) {
        int v=on?android.view.View.VISIBLE:android.view.View.GONE;
        abFirst.setVisibility(v); abSecond.setVisibility(v); abSame.setVisibility(v);
    }
    private void note(String line) { if(report.length()<24000) report.append(line).append('\n'); }
    private void confirmAb() {
        new AlertDialog.Builder(this).setTitle("Start blind A/B check?")
                .setMessage("Set the phone volume LOW before you start (the tones are about -30 dBFS and hard-capped at -18 dBFS). Pause other audio. The check plays about 25 short tone pairs (roughly 3 minutes). Nothing is recorded or uploaded.")
                .setNegativeButton("Cancel",null).setPositiveButton("Start",(d,w) -> beginAb()).show();
    }
    private void beginAb() {
        stopAb(); tone.stop(); report.setLength(0); restoring=false;
        note("AutoTune EQ AI Lite 0.3.4-candidate blind A/B check · API "+android.os.Build.VERSION.SDK_INT+" · "+android.os.Build.MANUFACTURER+" "+android.os.Build.MODEL);
        abStatus.setText("Preparing own audio session…");
        rig=new AbRig(new AbRig.Listener() {
            public void log(String line) { note(line); }
            public void ready(int[] hz) {
                bandHz=hz; ab=new AbSession(System.nanoTime(),hz.length,AbSession.REAL_PER_BAND,AbSession.NULL_PER_BAND);
                note("plan seed "+ab.seed()+" trials "+ab.size()); nextTrial();
            }
            public void presented() {
                if(restoring) { askRestoration(); return; }
                abStatus.setText("Trial "+(ab.index()+1)+" of "+ab.size()+": was the 1st or the 2nd sound quieter?"); showAnswers(true);
            }
            public void failed(String reason) { note("FAILED: "+reason); abStatus.setText("A/B unavailable: "+reason); showAnswers(false); abCopy.setVisibility(android.view.View.VISIBLE); }
        });
        rig.open();
    }
    private void nextTrial() {
        showAnswers(false);
        if(ab.done()) { finishAb(); return; }
        abStatus.setText("Trial "+(ab.index()+1)+" of "+ab.size()+": listen to sound 1, then sound 2…");
        rig.present(ab.current());
    }
    private void answer(AbSession.Answer a) {
        if(ab==null || ab.done() || restoring) return;
        AbSession.Trial t=ab.current();
        note("trial "+(ab.index()+1)+" band "+t.band+" ("+bandHz[t.band]+" Hz) "+(t.nullTrial?"NULL":"real onFirst="+t.onFirst)+" answer="+a+" correct="+AbSession.correct(t,a));
        ab.answer(a); nextTrial();
    }
    private void finishAb() {
        AbSession.Summary sm=ab.summary(bandHz);
        note(sm.text); abStatus.setText(sm.text);
        abRestore.setVisibility(android.view.View.VISIBLE); abCopy.setVisibility(android.view.View.VISIBLE);
        new AlertDialog.Builder(this).setTitle("Did the tones sound clean?")
                .setMessage("Any crackle, buzz, pumping or distortion on the test tones (EQ on or off)?")
                .setNegativeButton("Clean",(d,w) -> note("listener: tones clean"))
                .setPositiveButton("Distortion heard",(d,w) -> note("listener: DISTORTION REPORTED")).show();
    }
    private void restoreCheck() {
        if(rig==null || bandHz==null) return;
        restoring=true; showAnswers(false); abStatus.setText("Restoration check: three tones. Compare the 3rd with the 1st.");
        rig.restorationProbe(Math.min(2,bandHz.length-1));
    }
    private void askRestoration() {
        restoring=false;
        new AlertDialog.Builder(this).setTitle("Restoration check")
                .setMessage("After the effect was released, did the 3rd tone sound the same loudness as the 1st (reference) tone?")
                .setNegativeButton("Different",(d,w) -> { note("restoration: NOT restored (listener)"); abStatus.setText("Restoration: audible difference reported"); })
                .setPositiveButton("Same",(d,w) -> { note("restoration: same level (listener)"); abStatus.setText("Restoration: same level reported"); }).show();
    }
    private void copyReport() {
        ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(cm!=null) { cm.setPrimaryClip(ClipData.newPlainText("AutoTune A/B report",report.toString())); abStatus.setText("Report copied to clipboard"); }
    }
    private void stopAb() {
        if(rig!=null) { rig.close(); rig=null; }
        ab=null; restoring=false;
        if(abFirst!=null) showAnswers(false);
    }
    @Override protected void onPause() { stopAb(); request++; cloud.cancel(); tone.stop(); super.onPause(); }
    @Override protected void onDestroy() { stopAb(); closed=true; request++; ui.removeCallbacksAndMessages(null); cloud.shutdown(); tone.close(); super.onDestroy(); }
}
