package com.autotuneeq.mvp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** No external UI dependency. Layout adapts to older 1280x720 car panels. */
public final class MainActivity extends Activity {
    private static final int BG=0xff101722, PANEL=0xff1c2938, WHITE=0xfff1f7ff, MUTED=0xffb2c3d5, MINT=0xff53d8ae;
    private final Handler h = new Handler(Looper.getMainLooper());
    private TextView songText, sourceText, statusText, profileText, apiText, toneStatus;
    private SpectrumView spectrum;
    private EditText endpointInput;
    private final SeekBar[] manual = new SeekBar[5];
    private final TextView[] manualLabels = new TextView[5];
    private ToneEngine tone;
    private ProfileStore store;
    private AppSettings settings;
    private TextView suggestionText;
    private Button autoGenreButton;
    private String shownKey="";
    private String lastStatus="";
    private int dp(float val) { return (int)(val * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store=new ProfileStore(this); settings=new AppSettings(this);
        tone=new ToneEngine(message -> { if(toneStatus!=null) toneStatus.setText(message); });
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildUI();
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String label, int sp, int color, boolean bold) {
        TextView t=new TextView(this); t.setText(label); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private LinearLayout stack() {
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private void add(LinearLayout dest, View v, int bottom) {
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin=dp(bottom); dest.addView(v, lp);
    }
    private LinearLayout card(LinearLayout root) {
        LinearLayout c=stack(); c.setPadding(dp(18),dp(16),dp(18),dp(16)); c.setBackground(bg(PANEL,18)); add(root,c,13); return c;
    }
    private Button button(String title, boolean primary) {
        Button b=new Button(this); b.setAllCaps(false); b.setText(title); b.setTextSize(14);
        b.setTextColor(primary?0xff092019:WHITE); b.setBackground(bg(primary?MINT:0xff324558,11));
        b.setPadding(dp(10),dp(8),dp(10),dp(8)); b.setMinHeight(dp(46)); return b;
    }
    private void buildUI() {
        ScrollView sc = new ScrollView(this); sc.setFillViewport(true); sc.setBackgroundColor(BG);
        LinearLayout root=stack(); root.setPadding(dp(18),dp(18),dp(18),dp(30)); sc.addView(root); setContentView(sc);
        sc.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets;
        });
        add(root,text("AutoTune EQ AI Full",28,WHITE,true),3);
        add(root,text("Hybrid song profiles + live FFT · Android 8.0+",13,MUTED,false),15);
        apiText=text("Device API "+Build.VERSION.SDK_INT+"  |  Android label "+Build.VERSION.RELEASE,12,MINT,false);
        add(root,apiText,15);

        LinearLayout now=card(root);
        add(now,text("DETECTED SONG",12,MINT,true),9);
        songText=text("No song detected",20,WHITE,true); add(now,songText,3);
        sourceText=text("Enable notification access to detect songs",12,MUTED,false); add(now,sourceText,7);
        statusText=text("Monitoring stopped",13,MUTED,false); add(now,statusText,0);

        LinearLayout analysis=card(root);
        add(analysis,text("LIVE SPECTRUM · LOW RESOLUTION",12,MINT,true),6);
        spectrum=new SpectrumView(this); add(analysis,spectrum,8);
        add(analysis,text("Not a studio-quality FFT. Some apps and devices block output visualization.",12,MUTED,false),10);
        add(analysis,text("CURATED / SAVED EQ PROFILE",12,MINT,true),7);
        profileText=text(Profile.flat().describe(),13,WHITE,false); add(analysis,profileText,0);

        LinearLayout control=card(root);
        add(control,text("PERMISSIONS & MONITOR",14,WHITE,true),7);
        Button access=button("1 · Open notification listener settings",false);
        access.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Allow song metadata detection?")
                .setMessage("Android notification access is a sensitive permission. AutoTune EQ uses it to read playing song title, artist and player through media sessions. It does not read notification messages, OTPs or account details. If you save an optional HTTPS server, song title and artist are sent to that server for profile lookup. No raw audio is uploaded. On Android 13 and newer, an app installed from a file or browser may need App info > menu > Allow restricted settings before the notification-access switch can be turned on. You can revoke access in Android settings; without it, manual profiles and the app's own test tone remain available.")
                .setNegativeButton("Cancel", (d,w) -> {})
                .setNeutralButton("Open app info", (d,w) -> openAppInfo())
                .setPositiveButton("Open system settings", (d,w) -> openNotificationSettings()).show());
        add(control,access,7);
        Button audioPermission=button("2 · Allow visualization permission",false);
        audioPermission.setOnClickListener(v -> requestAudioPermission());
        add(control,audioPermission,7);
        Button start=button("Start background song monitoring",true);
        start.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 12);
            }
            action(MonitorService.ACTION_START);
            toast("Background monitor requested. Check notification and status.");
        });
        add(control,start,7);
        Button stop=button("Stop monitoring",false);
        stop.setOnClickListener(v -> action(MonitorService.ACTION_STOP));
        add(control,stop,5);
        add(control,text("A persistent notification means monitoring is active. It works only when Android allows the source metadata/audio route.",12,MUTED,false),0);

        LinearLayout player=card(root);
        add(player,text("SAFE TEST · THIS APP'S OWN AUDIO",14,WHITE,true),7);
        add(player,text("Play a quiet original 3-tone sound to verify this device supports session-based EQ. Start at low volume.",12,MUTED,false),8);
        toneStatus=text("Own-session EQ stopped · audible effect unverified",12,MUTED,false);
        add(player,toneStatus,7);
        Button play=button("Play test tone + apply slider EQ",true);
        play.setOnClickListener(v -> tone.play(new Profile(manualValues(),"manual test")));
        add(player,play,7);
        Button stopTone=button("Stop test tone",false);
        stopTone.setOnClickListener(v -> { tone.stop(); toneStatus.setText("Own-session EQ stopped"); });
        add(player,stopTone,0);

        LinearLayout edit=card(root);
        add(edit,text("FIVE-BAND EQ · MANUAL FINE-TUNE",14,WHITE,true),6);
        add(edit,text("Relative shape: -3 to +3 dB. Applied response shifts down to cut-only (up to 6 dB attenuation).",12,MUTED,false),7);
        for (int i=0;i<5;i++) {
            final int index=i;
            manualLabels[i]=text("",12,WHITE,false); add(edit,manualLabels[i],1);
            SeekBar seek=new SeekBar(this); seek.setMax(60); seek.setProgress(30);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar,int progress,boolean user) {
                    double db=(progress-30)/10.0;
                    manualLabels[index].setText(Profile.CENTERS_HZ[index]+" Hz     "+String.format(Locale.US,"%+.1f dB",db));
                    if (user) tone.setProfile(new Profile(manualValues(),"manual test"));
                }
                @Override public void onStartTrackingTouch(SeekBar b) { }
                @Override public void onStopTrackingTouch(SeekBar b) { }
            });
            manual[i]=seek; add(edit,seek,8);
            manualLabels[i].setText(Profile.CENTERS_HZ[i]+" Hz     +0.0 dB");
        }
        Button fromAnalysis=button("Copy saved / online profile to sliders",false);
        fromAnalysis.setOnClickListener(v -> setSliders(MonitorService.state.profile));
        add(edit,fromAnalysis,7);
        Button save=button("Save these settings for current song",true);
        save.setOnClickListener(v -> {
            MonitorService.State s=MonitorService.state;
            if (s.artist.isEmpty() && (s.title.equals("No song detected") || s.title.equals("No playing song detected"))) {
                toast("Detect a playing song first"); return;
            }
            double[] chosen=manualValues();
            boolean ok=store.putManual(s.title,s.artist,new Profile(chosen,"saved custom EQ"));
            if (ok) {
                // Preference learning: only from an explicit save, and only if a genre suggestion was shown for this very song.
                String cls=MonitorService.suggestedClass; double[] suggested=MonitorService.suggestedDb;
                if (cls!=null && suggested!=null && TrackIdentity.key(s.title,s.artist).equals(MonitorService.suggestedSongKey)) {
                    PreferenceModel m=settings.preferences(); m.learn(cls,suggested,chosen); settings.setPreferences(m);
                }
                action(MonitorService.ACTION_RELOAD_PROFILE); toast("Saved for: "+s.title);
            }
            else toast("Could not save profile");
        });
        add(edit,save,0);

        LinearLayout online=card(root);
        add(online,text("OPTIONAL ONLINE PROFILE SERVER",14,WHITE,true),7);
        add(online,text("Your own HTTPS server only. Empty URL = fully local. Sends title + artist on a new song, never raw audio.",12,MUTED,false),8);
        endpointInput=new EditText(this); endpointInput.setSingleLine(true);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setTextColor(WHITE); endpointInput.setHintTextColor(MUTED);
        endpointInput.setHint("https://your-server.example"); endpointInput.setText(store.endpoint());
        endpointInput.setTextSize(14);
        add(online,endpointInput,8);
        Button setServer=button("Save server URL",false);
        setServer.setOnClickListener(v -> {
            String url=endpointInput.getText().toString().trim();
            if (!url.isEmpty() && !CloudClient.validEndpoint(url)) { toast("Use an HTTPS origin only (no path, credentials, query); or clear for offline"); return; }
            store.setEndpoint(url); toast(url.isEmpty()?"Cloud disabled":"Server saved. New songs will be looked up.");
        });
        add(online,setServer,0);

        LinearLayout experimental=card(root);
        add(experimental,text("EXPERIMENTAL · PER-APP SESSION EQ",14,0xffffd18c,true),6);
        add(experimental,text("Off by default. Only attaches when an app emits an audio session event AND matches the actively playing media package. Not universal. Test parked at low volume.",12,MUTED,false),8);
        Button sessionsOn=button("Enable per-app EQ experiment",false);
        sessionsOn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Try per-app audio EQ")
                .setMessage("Some music players provide session events; others do not. Session broadcasts may be spoofed when Android does not supply sender identity. Enable only for a trusted cooperative player. The EQ will use cut-only gains. This choice is remembered and stays on until you press Disable (the legacy global test always starts off). You may need to restart playback. Try while parked at low volume.")
                .setNegativeButton("Cancel",(d,w)->{})
                .setPositiveButton("Enable",(d,w)->action(MonitorService.ACTION_SESSIONS_ON)).show());
        add(experimental,sessionsOn,7);
        Button sessionsOff=button("Disable per-app EQ experiment",false);
        sessionsOff.setOnClickListener(v -> action(MonitorService.ACTION_SESSIONS_OFF));
        add(experimental,sessionsOff,9);
        add(experimental,text("EXPERIMENTAL · LEGACY GLOBAL EQ",14,0xffffd18c,true),6);
        add(experimental,text("Session 0 global EQ is deprecated. It may not work or may affect audio quality. It is OFF by default. Try only while parked at low volume. Settings will be restored when disabled if Android allows.",12,MUTED,false),8);
        Button globalOn=button("Test global EQ (requires confirmation)",false);
        globalOn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Experimental global audio effect")
                .setMessage("This may change other apps' playback on some devices, or do nothing. Car must be parked and volume low. You can stop immediately. Continue?")
                .setNegativeButton("Cancel",(d,w)->{})
                .setPositiveButton("I understand · Test",(d,w)->{
                    action(MonitorService.ACTION_GLOBAL_ON);
                    toast("Check displayed status and listen for changes");
                }).show());
        add(experimental,globalOn,7);
        Button globalOff=button("STOP global EQ experiment",false);
        globalOff.setOnClickListener(v -> action(MonitorService.ACTION_GLOBAL_OFF));
        add(experimental,globalOff,0);
        LinearLayout suggestion=card(root);
        add(suggestion,text("AUTOTUNE SUGGESTION (rule-based, not a trained model)",14,WHITE,true),6);
        suggestionText=text("No song yet.",12,MUTED,false); add(suggestion,suggestionText,8);
        autoGenreButton=button("",false);
        autoGenreButton.setOnClickListener(v -> { settings.setAutoApplyGenre(!settings.autoApplyGenre()); showAutoGenre(); });
        add(suggestion,autoGenreButton,6); showAutoGenre();
        Button notForMe=button("Not for me (halve what I taught it)",false);
        notForMe.setOnClickListener(v -> {
            String cls=MonitorService.suggestedClass;
            if(cls==null) { toast("No genre suggestion to rate."); return; }
            PreferenceModel m=settings.preferences(); m.thumbsDown(cls); settings.setPreferences(m); toast("Learned offsets for "+cls+" halved.");
        });
        add(suggestion,notForMe,6);
        Button resetLearning=button("Reset everything it learned",false);
        resetLearning.setOnClickListener(v -> { PreferenceModel m=settings.preferences(); m.reset(); settings.setPreferences(m); toast("Preference learning reset."); });
        add(suggestion,resetLearning,0);
        LinearLayout diagnostics=card(root);
        add(diagnostics,text("DIAGNOSTICS",14,WHITE,true),6);
        add(diagnostics,text("Events from the monitor service (permissions, players, sessions, lookup outcomes). Kept in memory only; it never contains song titles or artists (songs appear as salted references).",12,MUTED,false),8);
        Button copyLog=button("Copy diagnostic log",false);
        copyLog.setOnClickListener(v -> {
            ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
            if(cm!=null) { cm.setPrimaryClip(ClipData.newPlainText("AutoTune diagnostics",MonitorService.diag.dump())); toast("Diagnostic log copied"); }
        });
        add(diagnostics,copyLog,0);
        add(root,text("Candidate 0.3.4 · FFT = visualization only · Per-app/legacy EQ experimental · No hardware verification",11,MUTED,false),0);
    }

    private void openAppInfo() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:"+getPackageName()))); }
        catch (Exception e) { toast("App info screen unavailable on this device."); }
    }
    private void openNotificationSettings() {
        try { startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }
        catch (Exception e) {
            toast("Notification access screen unavailable. Check device settings manually.");
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) { }
        }
    }
    private void requestAudioPermission() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 11);
        else { action(MonitorService.ACTION_RETRY_FFT); toast("Permission already granted; visualization restarted"); }
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==11 && grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED) {
            action(MonitorService.ACTION_RETRY_FFT);
        }
    }
    private void action(String which) {
        Intent intent=new Intent(this,MonitorService.class); intent.setAction(which);
        try {
            if (which.equals(MonitorService.ACTION_STOP)) { stopService(intent); return; }
            startForegroundService(intent);
        } catch(Exception e) { toast("Service could not start: "+e.getClass().getSimpleName()); }
    }
    private double[] manualValues() {
        double[] values=new double[5];
        for(int i=0;i<5;i++) values[i]=manual[i]==null?0:(manual[i].getProgress()-30)/10.0;
        return values;
    }
    private void setSliders(Profile p) {
        for (int i=0;i<5;i++) if (manual[i]!=null) manual[i].setProgress(30+(int)Math.round(p.db[i]*10));
    }
    private void showAutoGenre() {
        autoGenreButton.setText("Apply genre suggestions automatically: "+(settings.autoApplyGenre()?"ON":"OFF"));
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            MonitorService.State s=MonitorService.state;
            songText.setText(s.title);
            sourceText.setText((s.artist.isEmpty()?"":s.artist+" · ")+(s.player.isEmpty()?"No active media session":s.player));
            String status=s.status+(s.global?" · GLOBAL EQ EXPERIMENT ON":"")
                    +"\n"+MonitorService.fftStatus+"\n"+MonitorService.sessionStatus
                    +(MonitorService.playerSupportText.isEmpty()?"":"\n"+MonitorService.playerSupportText)
                    +(s.global?"\n"+MonitorService.globalStatus:"");
            if(!status.equals(lastStatus)) { statusText.setText(status); lastStatus=status; }
            profileText.setText(s.profile.describe()+"\nSource: "+s.profile.source);
            suggestionText.setText(MonitorService.recommendationText.isEmpty()?"No song yet.":MonitorService.recommendationText);
            spectrum.setLevels(s.levels);
            String key=s.artist+"|"+s.title;
            if(!key.equals(shownKey)) {
                shownKey=key;
                setSliders(s.profile);
            }
            h.postDelayed(this, 800);
        }
    };
    @Override protected void onResume() { super.onResume(); MonitorService.uiVisible=true; h.post(refresh); }
    @Override protected void onPause() { MonitorService.uiVisible=false; h.removeCallbacks(refresh); tone.stop(); toneStatus.setText("Own-session EQ stopped"); super.onPause(); }
    @Override protected void onDestroy() {
        tone.close(); super.onDestroy();
    }
}
