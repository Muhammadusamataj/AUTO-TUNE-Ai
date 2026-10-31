package com.autotuneeq.mvp;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.media.audiofx.AudioEffect;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

/** Foreground, explicitly started diagnostic monitoring. No raw audio is saved or uploaded. */
public final class MonitorService extends Service {
    public static final String ACTION_START = "com.autotuneeq.mvp.START";
    public static final String ACTION_STOP = "com.autotuneeq.mvp.STOP";
    public static final String ACTION_GLOBAL_ON = "com.autotuneeq.mvp.GLOBAL_ON";
    public static final String ACTION_GLOBAL_OFF = "com.autotuneeq.mvp.GLOBAL_OFF";
    public static final String ACTION_RELOAD_PROFILE = "com.autotuneeq.mvp.RELOAD_PROFILE";
    public static final String ACTION_SESSIONS_ON = "com.autotuneeq.mvp.SESSIONS_ON";
    public static final String ACTION_SESSIONS_OFF = "com.autotuneeq.mvp.SESSIONS_OFF";
    public static final String ACTION_RETRY_FFT = "com.autotuneeq.mvp.RETRY_FFT";
    private static final String CHANNEL = "autotune_monitor";
    public static volatile boolean perAppMode = false;
    public static volatile String sessionStatus = "External EQ unsupported/experimental · disabled";
    public static volatile String fftStatus = "FFT unavailable · stopped";
    public static volatile String globalStatus="Legacy EQ disabled";
    public static volatile boolean uiVisible=false;
    /** Human-readable recommendation for the current song (basis, confidence, reasons); empty when no song. */
    public static volatile String recommendationText="";
    /** What was suggested for the current song, for preference learning: genre class + band values (null if none). */
    public static volatile String suggestedClass=null; public static volatile double[] suggestedDb=null; public static volatile String suggestedSongKey="";
    public static volatile String playerSupportText="";
    private final PlayerSupport support=new PlayerSupport();
    private boolean fftRunning, lastUiVisible;
    private double[] lastGlobal;
    /** Process-wide, in-memory, free of song titles/artists (see DiagLog). */
    public static final DiagLog diag=new DiagLog();
    private final PollGate gate=new PollGate();
    private final MissCache cloudErrors=new MissCache(2*60*1000L);   // network failures: retry no sooner than 2 min
    private AppSettings settings;
    private boolean foregroundStarted, lastNoAccess, accessKnown;
    private String lastPlayer="", lastSongRef="";
    private long playerSince;
    private long profileGeneration;
    private int analysisGeneration;
    private SessionCoordinator sessions;
    private long openWindow;
    private int openCount;
    private boolean receiverRegistered = false;
    private final java.util.concurrent.atomic.AtomicInteger pendingEvents=new java.util.concurrent.atomic.AtomicInteger();
    private volatile long sessionGeneration;
    private final BroadcastReceiver sessionReceiver=new BroadcastReceiver() {
        @Override public void onReceive(Context context,Intent intent) {
            if(destroyed || !perAppMode || !running || intent==null) return;
            try {
                final String action=intent.getAction();
                if(!AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(action)
                        && !AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) return;
                final String pkg=intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
                final int id=intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,-1);
                if(id<=0 || pkg==null || pkg.isEmpty() || pkg.length()>255 || pkg.equals(getPackageName())) return;
                if(intent.hasExtra(AudioEffect.EXTRA_CONTENT_TYPE)
                        && intent.getIntExtra(AudioEffect.EXTRA_CONTENT_TYPE,-1)!=AudioEffect.CONTENT_TYPE_MUSIC) return;
                final int sender=Build.VERSION.SDK_INT>=34?getSentFromUid():-1;
                final long generation=sessionGeneration;
                if(pendingEvents.incrementAndGet()>32) { pendingEvents.decrementAndGet(); return; }
                // No audio-effect or media-session IPC in onReceive. Bounded worker queue only.
                if(!handler.post(() -> {
                    try {
                        if(destroyed || !perAppMode || generation!=sessionGeneration) return;
                        if(sender>=0 && !SessionEventPolicy.senderMatches(true,pkg,getPackageManager().getPackagesForUid(sender))) return;
                        if(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(action)) {
                            sessions.close(id,pkg); sessionStatus=sessions.status(); diag.event("session","CLOSE "+pkg); return;
                        }
                        support.sawOpen(pkg);
                        long now=SystemClock.elapsedRealtime();
                        if(now-openWindow>=10000) { openWindow=now; openCount=0; }
                        if(++openCount>32 || !sessions.open(id,pkg,now)) { diag.event("session","OPEN rejected "+pkg+" (limit/conflict/cap)"); return; }
                        diag.event("session","OPEN accepted "+pkg);
                        mediaChanged.run();
                    } catch(RuntimeException ignored) { /* Reject unresolvable sender/invalid event. */ }
                    finally { pendingEvents.decrementAndGet(); }
                })) pendingEvents.decrementAndGet();
            } catch(RuntimeException ignored) { /* Untrusted/malformed extras. */ }
        }
    };
    private void registerSessionReceiver() {
        if(receiverRegistered) return;
        IntentFilter filter=new IntentFilter(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        filter.addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        try {
            androidx.core.content.ContextCompat.registerReceiver(this,sessionReceiver,filter,
                    androidx.core.content.ContextCompat.RECEIVER_EXPORTED);
            receiverRegistered=true;
        } catch(RuntimeException e) { sessionStatus="Broadcast receiver unavailable: "+e.getClass().getSimpleName(); }
    }
    private void applyEnabledEffects(Profile profile) {
        if(eq.isGlobalRunning()) {
            // Rewrite hardware bands only when the profile actually changed (not on every poll/callback).
            if(lastGlobal==null || !java.util.Arrays.equals(lastGlobal,profile.db)) { globalStatus=eq.applyGlobal(profile); lastGlobal=profile.db.clone(); }
        } else lastGlobal=null;
        if(perAppMode) { sessions.applyCurrent(state.player,profile); sessionStatus=sessions.status(); }
    }

    private HandlerThread audioThread;
    private Handler handler;
    private volatile boolean destroyed;
    private SongMonitor songMonitor;
    private OutputAnalyzer analyzer;
    private ProfileStore store;
    private CloudClient cloud;
    private EqController eq;
    private String currentKey = "";
    public static volatile State state = State.idle();
    private volatile boolean running = false;

    public static final class State {
        public final String title, artist, player, status;
        public final Profile profile;
        public final float[] levels;
        public final boolean active, global;
        public State(String title, String artist, String player, String status, Profile profile,
                     float[] levels, boolean active, boolean global) {
            this.title=title; this.artist=artist; this.player=player; this.status=status;
            this.profile=profile; this.levels=levels.clone(); this.active=active; this.global=global;
        }
        public static State idle() { return new State("No song detected", "", "", "Monitoring stopped",
                Profile.flat(), new float[5], false, false); }
    }
    @Override public void onCreate() {
        super.onCreate();
        store=new ProfileStore(this);
        settings=new AppSettings(this);
        songMonitor=new SongMonitor(this);
        analyzer=new OutputAnalyzer();
        cloud=new CloudClient();
        eq=new EqController();
        sessions=new SessionCoordinator(eq);
        audioThread=new HandlerThread("autotune-monitor-audio");
        audioThread.start();
        handler=new Handler(audioThread.getLooper());
        {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "AutoTune audio monitoring", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Foreground song metadata and low-resolution spectrum monitoring");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    private Notification notice(String message) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        // Song titles are private: the lock-screen version carries no media information.
        Notification publicVersion=new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("AutoTune EQ AI")
                .setContentText("Monitoring active")
                .build();
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("AutoTune EQ AI · Monitoring")
                .setContentText(message)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .setContentIntent(pi)
                .build();
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_STOP.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        // Meet the foreground deadline on the main thread; audio/session IPC stays on the worker.
        if(!foregroundStarted) {
            try { startForeground(1071,notice("Checking media playback")); foregroundStarted=true; }
            catch(RuntimeException e) { diag.event("service","startForeground refused: "+e.getClass().getSimpleName()); stopSelf(); return START_NOT_STICKY; }
        }
        handler.post(() -> { if(!destroyed) handleCommand(action); });
        return START_STICKY;
    }
    private void handleCommand(String action) {
        if (!running) {
            running=true;
            state = new State("No song detected", "", "", "Searching for playing media...", Profile.flat(),
                    new float[5], true, false);
            registerSessionReceiver();
            songMonitor.start(handler,mediaChanged);
            diag.event("service","monitor started api="+Build.VERSION.SDK_INT);
            if(settings.perAppArmed() && !eq.isGlobalRunning()) {
                sessionGeneration++; perAppMode=true;
                sessionStatus="Per-app EQ armed (remembered from your last choice); restart playback if no session event arrives";
                diag.event("settings","per-app EQ restored from saved choice");
            }
            schedulePoll(0);
        }
        if (ACTION_SESSIONS_ON.equals(action)) {
            eq.stopGlobalExperiment();
            sessionGeneration++;
            perAppMode=true; settings.setPerAppArmed(true);
            diag.event("settings","per-app EQ armed by user");
            sessionStatus="Per-app EQ armed: play/pause or restart the player to emit session events";
            mediaChanged.run();
            replaceState(sessionStatus,null,eq.isGlobalRunning());
        } else if (ACTION_SESSIONS_OFF.equals(action)) {
            sessionGeneration++;
            perAppMode=false; settings.setPerAppArmed(false);
            diag.event("settings","per-app EQ disabled by user");
            sessions.clear(); support.reset();
            eq.releaseExternalAll();
            sessionStatus="Per-app EQ disabled";
            replaceState(sessionStatus,null,eq.isGlobalRunning());
        } else if (ACTION_RETRY_FFT.equals(action)) {
            if(uiVisible) beginAnalysis();
        } else if (ACTION_GLOBAL_ON.equals(action)) {
            sessionGeneration++;
            perAppMode=false; sessions.clear(); eq.releaseExternalAll();
            sessionStatus="External EQ disabled during legacy experiment";
            String message=eq.startGlobalExperiment(state.profile);
            lastGlobal=eq.isGlobalRunning()?state.profile.db.clone():null;
            globalStatus=message;
            replaceState(message, null, eq.isGlobalRunning());
        } else if (ACTION_GLOBAL_OFF.equals(action)) {
            eq.stopGlobalExperiment();
            globalStatus="Legacy EQ disabled";
            replaceState("Global EQ experiment switched off", null, false);
        } else if (ACTION_RELOAD_PROFILE.equals(action)) {
            profileGeneration++;
            cloud.cancel();
            State old=state;
            Profile p=store.get(old.title,old.artist);
            if (p!=null) {
                replaceState("Saved profile refreshed",p,eq.isGlobalRunning());
                applyEnabledEffects(p);
            }
        }
    }

    private void beginAnalysis() {
        final int generation=++analysisGeneration;
        analyzer.stop();
        fftStatus="FFT unavailable";
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fftStatus="FFT unavailable · audio permission required"; return;
        }
        boolean ready=analyzer.start(0, new OutputAnalyzer.Listener() {
            @Override public void onResult(BandAnalyzer.Result r) {
                // Visualizer callback can run on a worker thread; serialize state on main thread.
                handler.post(() -> {
                    if(!running || destroyed || generation!=analysisGeneration) return;
                    fftStatus="FFT available · diagnostic only";
                    State old=state;
                    state=new State(old.title,old.artist,old.player,old.status,old.profile,
                            r.levels,old.active,eq.isGlobalRunning());
                });
            }
            @Override public void onError(String error) {
                handler.post(() -> { if(running && !destroyed && generation==analysisGeneration) fftStatus="FFT unavailable · "+error; });
            }
        });
        fftRunning=ready;
        fftStatus=ready?"FFT attached · waiting for frames":"FFT unavailable · route unsupported";
    }
    private void stopAnalysis() {
        analysisGeneration++; analyzer.stop(); fftRunning=false;
        fftStatus="FFT paused · visualization only runs while the app screen is visible";
    }
    /** Visualizer capture costs CPU/battery and is display-only: run it only while the UI is on screen. */
    private void gateFft() {
        boolean visible=uiVisible;
        if(visible==lastUiVisible) return;
        lastUiVisible=visible;
        if(visible) beginAnalysis(); else stopAnalysis();
    }

    private void schedulePoll(long delay) {
        handler.removeCallbacks(poll); gate.scheduled(SystemClock.uptimeMillis(),delay); handler.postDelayed(poll,delay);
    }
    /** Media callbacks can arrive many times per second: coalesce them into one extra poll per 250 ms. */
    private final Runnable mediaChanged = () -> {
        if(running && !destroyed) { long d=gate.requestSoon(SystemClock.uptimeMillis()); if(d>=0) schedulePoll(d); }
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running || destroyed) return;
            try { pollOnce(); }
            catch (RuntimeException e) { diag.event("poll","error "+e.getClass().getSimpleName()); }   // never let one failure stop monitoring
            finally { if (running && !destroyed) schedulePoll(2500); }
        }
    };
    private void pollOnce() {
        gateFft();
        SongMonitor.Snapshot snapshot=songMonitor.snapshot();
        SongMonitor.Song song=snapshot.song;
        if(!accessKnown || snapshot.noAccess!=lastNoAccess) {
            accessKnown=true; lastNoAccess=snapshot.noAccess;
            diag.event("access",snapshot.noAccess?"notification access NOT granted":"notification access granted");
            if(snapshot.noAccess) {
                state=new State("No song detected","","","Notification access not granted · song detection and per-app EQ are off (grant it in Android settings)",
                        Profile.flat(),state.levels,true,eq.isGlobalRunning());
                currentKey="";
            }
        }
        String nowPlayer=snapshot.playingPackage==null?"":snapshot.playingPackage;
        if(!nowPlayer.equals(lastPlayer)) { lastPlayer=nowPlayer; playerSince=SystemClock.elapsedRealtime(); diag.event("player",nowPlayer.isEmpty()?"none playing":"playing "+nowPlayer); }
        if (song != null) {
            String key=song.playbackKey();
            if (!key.equals(currentKey)) {
                currentKey=key;
                lastSongRef=diag.songRef(song.key());
                diag.event("song",lastSongRef+" player="+song.player);
                final long request=++profileGeneration;
                cloud.cancel();
                // Do not leave effects on a previous player's playback session.
                // Coordinator bypasses prior player effects and retains live session claims.
                Profile cached=store.get(song.title, song.artist);
                if (cached != null) {
                    state=new State(song.title,song.artist,song.player,"Profile found · saved offline",cached,state.levels,true,eq.isGlobalRunning());
                    applyEnabledEffects(cached);
                } else {
                    Recommender.Query query=new Recommender.Query(); query.title=song.title; query.artist=song.artist; query.genre=song.genre;
                    final Recommender.Recommendation rec=Recommender.recommend(query,settings.preferences());
                    final boolean autoApplied=settings.autoApplyGenre() && rec.basis==Recommender.Basis.GENRE_PRESET && rec.confidence>=0.4;
                    suggestedClass=rec.genreClass; suggestedDb=rec.profile.db.clone(); suggestedSongKey=song.key();
                    recommendationText="Suggestion: "+(rec.basis==Recommender.Basis.GENRE_PRESET?rec.preset.label+" preset":"neutral")+" · confidence "+rec.confidenceLabel()
                            +(rec.basis==Recommender.Basis.GENRE_PRESET?(autoApplied?" · applied automatically":" · NOT applied (auto-apply is off)"):"")+"\n"+rec.explanation();
                    diag.event("recommend",lastSongRef+" basis="+rec.basis+" confidence="+rec.confidenceLabel()+" applied="+autoApplied);
                    final Profile startProfile=autoApplied?rec.profile:Profile.flat();
                    state=new State(song.title,song.artist,song.player,autoApplied?"Genre suggestion applied ("+rec.preset.label+") · lookup pending":"Profile not found · neutral; FFT is diagnostic only",
                            startProfile,state.levels,true,eq.isGlobalRunning());
                    applyEnabledEffects(startProfile);
                    String endpoint=store.endpoint();
                    final String errKey=song.key();
                    if (!endpoint.isEmpty() && (store.recentMiss(song.title,song.artist) || cloudErrors.isMiss(errKey,SystemClock.elapsedRealtime()))) {
                        diag.event("lookup",lastSongRef+" skipped (recent miss/error cached)");
                        state=new State(song.title,song.artist,song.player,"Profile not found · server lookup skipped (recent miss or network error cached)",
                                Profile.flat(),state.levels,true,eq.isGlobalRunning());
                    } else if (!endpoint.isEmpty()) cloud.fetch(endpoint,song.title,song.artist,(profile,message)->handler.post(()->{
                        if (destroyed || !key.equals(currentKey) || request!=profileGeneration || !running || !endpoint.equals(store.endpoint())) return;
                        Profile local=store.get(song.title,song.artist);
                        if(local!=null) {
                            replaceState("Profile found · local edit retained",local,eq.isGlobalRunning());
                            applyEnabledEffects(local);
                            return;
                        }
                        diag.event("lookup",lastSongRef+(profile!=null?" found online":CloudClient.NOT_FOUND_MESSAGE.equals(message)?" not found":" failed"));
                        if (profile != null) {
                            store.put(song.title,song.artist,profile);
                            state=new State(song.title,song.artist,song.player,message,profile,state.levels,true,eq.isGlobalRunning());
                            applyEnabledEffects(profile);
                        } else {
                            if(CloudClient.NOT_FOUND_MESSAGE.equals(message)) store.recordMiss(song.title,song.artist);
                            else cloudErrors.record(errKey,SystemClock.elapsedRealtime());
                            replaceState(message+(autoApplied?" · genre suggestion kept":" · safe neutral fallback"),autoApplied?rec.profile:null,eq.isGlobalRunning());
                        }
                    }));
                }
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1071,notice(song.title));
            }
            applyEnabledEffects(state.profile);
        } else if (!currentKey.isEmpty()) {
            currentKey="";
            profileGeneration++;
            cloud.cancel();
            // Paused/buffering is not a CLOSE: keep per-session effects; most players do not re-send OPEN on resume.
            sessionStatus="Playback paused/buffering; per-app EQ kept while the player's media session is alive";
            // Clear stale song profile so it cannot apply to the next unknown source.
            state=new State("No playing song detected", "", "", "Track metadata unavailable or playback stopped",
                    Profile.flat(),state.levels,true,eq.isGlobalRunning());
            if(eq.isGlobalRunning()) eq.applyGlobal(Profile.flat());
        }
        if(perAppMode) {
            sessions.reconcile(snapshot.playingPackage,snapshot.retained,
                    song==null?Profile.flat():state.profile,SystemClock.elapsedRealtime());
            sessionStatus=sessions.status();
            playerSupportText=support.describe(snapshot.playingPackage,SystemClock.elapsedRealtime()-playerSince);
        } else playerSupportText="";
    }

    private void replaceState(String status, Profile p, boolean global) {
        State old=state;
        state=new State(old.title,old.artist,old.player,status,p==null?old.profile:p,old.levels,old.active,global);
    }
    @Override public void onDestroy() {
        diag.event("service","monitor destroyed");
        destroyed=true; perAppMode=false; sessionGeneration++;
        handler.removeCallbacksAndMessages(null);
        // Never wait for native audio IPC on the main thread. Cleanup is serialized after in-flight work.
        handler.post(() -> {
            running=false; songMonitor.stop(); analysisGeneration++; analyzer.stop();
            if(receiverRegistered) {
                try { unregisterReceiver(sessionReceiver); } catch(RuntimeException ignored) { }
                receiverRegistered=false;
            }
            sessions.clear(); eq.stopGlobalExperiment(); cloud.shutdown();
            fftStatus="FFT unavailable · stopped"; sessionStatus="Per-app EQ disabled";
            globalStatus="Legacy EQ disabled"; state=State.idle(); audioThread.quitSafely();
        });
        state=State.idle(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
