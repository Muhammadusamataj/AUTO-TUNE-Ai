package com.autotuneeq.mvp;

import java.util.*;

/** Worker-thread-owned session lifecycle. Hints are claims, not authenticated session discovery. */
public final class SessionCoordinator {
    private final EqController eq;
    private final SessionHints hints=new SessionHints();
    private final Map<Integer,String> claims=new LinkedHashMap<>();
    private final Map<Integer,Long> created=new HashMap<>();
    private final Set<String> observed=new HashSet<>();
    private final Set<Integer> attached=new HashSet<>();
    private String playing="", lastPlayer="";
    private Profile lastProfile;
    private String status="External EQ experimental · no attached sessions";
    /** A package may hold only a few sessions, and claims from packages that never showed a live media session expire quickly. */
    public static final int MAX_PER_PACKAGE = 3;
    public static final long UNOBSERVED_TTL_MS = 90_000L;
    public SessionCoordinator(EqController controller) { eq=controller; }
    public boolean open(int id,String pkg,long now) {
        expire(now);
        if(id<=0 || pkg==null || pkg.isEmpty() || pkg.length()>255) return false;
        if(claims.containsKey(id)) return claims.get(id).equals(pkg); // no reset/overwrite
        int same=0; for(String owner:claims.values()) if(owner.equals(pkg)) same++;
        if(same>=MAX_PER_PACKAGE) return false;                           // one sender cannot fill the table
        if(claims.size()>=SessionHints.MAX_HINTS || !hints.offer(id,pkg,now)) return false;
        claims.put(id,pkg); created.put(id,now); return true;
    }
    public void close(int id,String pkg) {
        if(!pkg.equals(claims.get(id))) return;
        eq.releaseExternalIfPackage(id,pkg); hints.close(id,pkg);
        claims.remove(id); created.remove(id); attached.remove(id);
        status="Session CLOSE processed · attached: "+eq.sessionCount();
    }
    private void expire(long now) {
        hints.prune(now);
        for(Integer id:new ArrayList<>(claims.keySet())) {
            String pkg=claims.get(id);
            long limit=observed.contains(pkg)?SessionHints.TTL_MS:UNOBSERVED_TTL_MS;
            if(!attached.contains(id) && now-created.get(id)>limit) close(id,pkg);
        }
    }
    /** retained includes paused/buffering/playing, excludes STOPPED/ERROR/destroyed players. */
    public void reconcile(String current,Set<String> retained,Profile profile,long now) {
        if(retained==null) { clear(); status="Notification access unavailable · effects released"; return; }
        Set<String> wasObserved=new HashSet<>(observed);
        observed.addAll(retained);
        expire(now);
        for(Integer id:new ArrayList<>(claims.keySet())) {
            String pkg=claims.get(id);
            if(wasObserved.contains(pkg) && !retained.contains(pkg)) { close(id,pkg); continue; }
            if(attached.contains(id) && !eq.hasSession(id)) {
                attached.remove(id); created.put(id,now); hints.offer(id,pkg,now);
            }
            // Pause retains the effect. Switching active players bypasses the previous one,
            // while retaining its known live session for resume without another OPEN event.
            if(!current.isEmpty() && !current.equals(pkg) && attached.remove(id)) {
                eq.releaseExternalIfPackage(id,pkg); created.put(id,now); hints.offer(id,pkg,now);
            }
        }
        // Keep this bookkeeping bounded; only packages for retained claims matter.
        observed.retainAll(new HashSet<>(claims.values()));
        playing=current;
        if(current.isEmpty()) { lastPlayer=""; status="Paused/buffering · retained effects: "+eq.sessionCount(); return; }
        hints.tryAttach(current,now,(id,pkg)-> {
            if(eq.sessionCount()>=8) return false;
            status=eq.attachExternal(id,pkg,profile);
            if(eq.hasSession(id)) { attached.add(id); return true; }
            return false;
        });
        applyCurrent(current,profile);
    }
    public void applyCurrent(String pkg,Profile profile) {
        if(pkg.isEmpty() || !pkg.equals(playing)) return;
        boolean changed=!pkg.equals(lastPlayer) || lastProfile==null || !Arrays.equals(lastProfile.db,profile.db);
        if(changed && eq.sessionCount()>0) status=eq.applyForPackage(pkg,profile);
        // Verify control without rewriting every band each poll.
        if(eq.releaseUncontrolledSessions()>0) status="Control lost · external effect released";
        lastPlayer=pkg; lastProfile=profile;
    }
    public String status() { return status+" · attached: "+eq.sessionCount(); }
    public int pending() { return hints.size(); }
    public int known() { return claims.size(); }
    public void clear() {
        hints.clear(); claims.clear(); created.clear(); attached.clear(); observed.clear();
        playing=""; lastPlayer=""; lastProfile=null; eq.releaseExternalAll();
        status="External EQ disabled · no attached sessions";
    }
}
