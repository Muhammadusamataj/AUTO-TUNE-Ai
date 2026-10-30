package com.autotuneeq.mvp;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Explicit metadata-only HTTPS origin; no redirects, write key or audio upload. */
public final class CloudClient {
    public static final String NOT_FOUND_MESSAGE="Profile not found in online database";
    public interface Callback { void complete(Profile profile,String message); }
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile HttpURLConnection active;
    private Future<?> pending;
    public static boolean validEndpoint(String base) {
        try {
            URI u=new URI(base);
            return base.length()<=2048 && "https".equals(u.getScheme()) && u.getHost()!=null
                    && u.getRawUserInfo()==null && u.getRawQuery()==null && u.getRawFragment()==null
                    && (u.getRawPath().isEmpty() || "/".equals(u.getRawPath()))
                    && (u.getPort()==-1 || u.getPort()>0 && u.getPort()<=65535);
        } catch(Exception e) { return false; }
    }
    public synchronized void cancel() {
        if(pending!=null) pending.cancel(true);
        io.getQueue().clear();
        HttpURLConnection c=active; if(c!=null) c.disconnect();
    }
    public synchronized void fetch(String base,String title,String artist,Callback callback) {
        cancel();
        if(!validEndpoint(base) || title.trim().isEmpty() || artist.trim().isEmpty() || title.length()>200 || artist.length()>200) {
            callback.complete(null,"Profile not found: valid HTTPS origin, title and artist required"); return;
        }
        if(io.isShutdown()) return;
        pending=io.submit(() -> {
            HttpURLConnection c=null;
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
            try {
                URL url=new URL(base.replaceAll("/+$", "")+"/v1/profile?title="+
                        URLEncoder.encode(title,"UTF-8")+"&artist="+URLEncoder.encode(artist,"UTF-8"));
                c=(HttpURLConnection)url.openConnection(); active=c;
                c.setInstanceFollowRedirects(false); c.setConnectTimeout(4500); c.setReadTimeout(4500);
                c.setRequestProperty("Accept","application/json");
                int status=c.getResponseCode();
                if(status==404) { callback.complete(null,NOT_FOUND_MESSAGE); return; }
                if(status!=200) throw new java.io.IOException("HTTP "+status);
                String type=c.getContentType();
                if(type==null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("application/json"))
                    throw new java.io.IOException("JSON content type required");
                if(c.getContentLengthLong()>16384) throw new java.io.IOException("Response too large");
                try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[1024]; int n;
                    while(true) {
                        long remaining=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
                        if(remaining<=0) throw new java.io.IOException("Lookup timed out");
                        c.setReadTimeout((int)Math.min(4500,Math.max(1,remaining)));
                        n=in.read(buffer); if(n==-1) break;
                        if(Thread.currentThread().isInterrupted() || System.nanoTime()>deadline)
                            throw new java.io.IOException("Lookup cancelled or timed out");
                        if(out.size()+n>16384) throw new java.io.IOException("Response too large");
                        out.write(buffer,0,n);
                    }
                    JSONObject json=new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
                    if(!(json.get("title") instanceof String) || !(json.get("artist") instanceof String))
                        throw new java.io.IOException("String recording identity required");
                    if(!TrackIdentity.key(title,artist).equals(TrackIdentity.key(
                            json.optString("title",""),json.optString("artist",""))))
                        throw new java.io.IOException("Different recording returned");
                    Profile p=Profile.fromJson(json);
                    if(!Thread.currentThread().isInterrupted()) callback.complete(p,"Profile found online");
                }
            } catch(Exception e) {
                if(!Thread.currentThread().isInterrupted()) callback.complete(null,"Cloud unavailable: "+e.getClass().getSimpleName());
            } finally { if(c!=null) c.disconnect(); if(active==c) active=null; }
        });
    }
    public synchronized void shutdown() { cancel(); io.shutdownNow(); }
}
