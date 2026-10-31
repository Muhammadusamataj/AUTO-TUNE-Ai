package com.autotuneeq.mvp;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Build;
import android.provider.Settings;
import java.util.ArrayList;
import java.util.List;

/** Public media-session API only; callbacks plus service polling handle listener reconnects. */
public final class SongMonitor {
    public static final class Song {
        public final String title, artist, player;
        /** Genre text from the player's own metadata when it provides one (often empty); used locally only, never sent anywhere. */
        public final String genre;
        public Song(String t,String a,String p) { this(t,a,p,""); }
        public Song(String t,String a,String p,String g) { title=t; artist=a; player=p; genre=g==null?"":g; }
        public String key() { return TrackIdentity.key(title,artist); }
        public String playbackKey() { return key()+"|"+player; }
    }
    private final Context context;
    private final MediaSessionManager manager;
    private final ComponentName component;
    private final List<MediaController> observed = new ArrayList<>();
    private Handler handler;
    private Runnable changed;
    private boolean registered;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onMetadataChanged(MediaMetadata m) { signal(); }
        @Override public void onPlaybackStateChanged(PlaybackState s) { signal(); }
        @Override public void onSessionDestroyed() { signal(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = list -> {
        observe(list); signal();
    };
    public SongMonitor(Context c) {
        context=c; manager=(MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);
        component=new ComponentName(c,MediaNotificationListener.class);
    }
    public boolean hasAccess() {
        try {
            if(Build.VERSION.SDK_INT>=27) {
                NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
                return nm != null && nm.isNotificationListenerAccessGranted(component);
            }
            // Android 8.0 / API 26 lacks NotificationManager's API 27 access query.
            // This is an ordinary read of our own user's settings, not a privileged grant.
            return containsComponent(Settings.Secure.getString(context.getContentResolver(),
                    "enabled_notification_listeners"),component);
        } catch (RuntimeException e) { return false; }
    }
    static boolean containsComponent(String enabled,ComponentName expected) {
        if(enabled==null || expected==null) return false;
        for(String value:enabled.split(":"))
            if(expected.equals(ComponentName.unflattenFromString(value))) return true;
        return false;
    }
    public void start(Handler h,Runnable listener) { handler=h; changed=listener; refreshRegistration(); }
    private void signal() { if(handler!=null && changed!=null) handler.post(changed); }
    private void observe(List<MediaController> list) {
        for(MediaController c:observed) try { c.unregisterCallback(callback); } catch(RuntimeException ignored) { }
        observed.clear();
        if(list==null || handler==null) return;
        for(MediaController c:list) try { c.registerCallback(callback,handler); observed.add(c); } catch(RuntimeException ignored) { }
    }
    private void refreshRegistration() {
        if(manager==null) return;
        if(!hasAccess()) { detach(); return; }
        if(!registered && handler!=null) try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged,component,handler);
            registered=true; observe(manager.getActiveSessions(component));
        } catch(RuntimeException ignored) { detach(); }
    }
    private void detach() {
        observe(null);
        if(registered) try { manager.removeOnActiveSessionsChangedListener(sessionsChanged); } catch(RuntimeException ignored) { }
        registered=false;
    }
    public void stop() { detach(); handler=null; changed=null; }
    public static final class Snapshot {
        public final Song song;
        public final String playingPackage;
        public final java.util.Set<String> retained;
        /** True when notification access is missing (song detection and per-app EQ cannot work). */
        public final boolean noAccess;
        Snapshot(Song s,String p,java.util.Set<String> r) { this(s,p,r,false); }
        Snapshot(Song s,String p,java.util.Set<String> r,boolean denied) { song=s; playingPackage=p; retained=r; noAccess=denied; }
    }
    public Snapshot snapshot() {
        refreshRegistration();
        if(manager==null || !hasAccess()) return new Snapshot(null,"",null,true);
        try {
            Song selected=null; String player=""; long latest=Long.MIN_VALUE;
            java.util.Set<String> retained=new java.util.HashSet<>();
            for(MediaController c:manager.getActiveSessions(component)) {
                PlaybackState s=c.getPlaybackState();
                if(s==null) continue;
                if(PlaybackPolicy.retainsSession(s.getState())) retained.add(c.getPackageName());
                if(s.getState()!=PlaybackState.STATE_PLAYING || s.getLastPositionUpdateTime()<latest) continue;
                latest=s.getLastPositionUpdateTime(); player=c.getPackageName(); selected=null;
                MediaMetadata m=c.getMetadata(); if(m==null) continue;
                String title=m.getString(MediaMetadata.METADATA_KEY_TITLE);
                if(title==null || title.trim().isEmpty()) title=m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                if(title==null || title.trim().isEmpty()) continue;
                String artist=m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if(artist==null || artist.trim().isEmpty()) artist=m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                String genre=m.getString(MediaMetadata.METADATA_KEY_GENRE);
                if(genre!=null && genre.length()>80) genre=genre.substring(0,80);
                selected=new Song(title.trim(),artist==null?"":artist.trim(),player,genre==null?"":genre.trim());
            }
            return new Snapshot(selected,player,retained);
        } catch(RuntimeException e) { detach(); return new Snapshot(null,"",null); }
    }
}
