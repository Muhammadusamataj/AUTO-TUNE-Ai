package com.autotuneeq.mvp;

/** Known sender UID is enforceable; unknown sender remains explicitly untrusted on older APIs. */
public final class SessionEventPolicy {
    private SessionEventPolicy() { }
    public static boolean senderMatches(boolean known,String claimed,String[] uidPackages) {
        if(!known) return true;
        if(claimed==null || uidPackages==null) return false;
        for(String p:uidPackages) if(claimed.equals(p)) return true;
        return false;
    }
}
