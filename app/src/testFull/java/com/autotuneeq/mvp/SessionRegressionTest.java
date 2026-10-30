package com.autotuneeq.mvp;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SessionRegressionTest {
    @Test public void duplicateOpenCannotResetBackoff() {
        SessionHints h=new SessionHints(); int[] calls={0};
        h.offer(1,"p",0); h.tryAttach("p",0,(id,p)->{calls[0]++;return false;});
        assertTrue(h.offer(1,"p",1000));
        h.tryAttach("p",1000,(id,p)->{calls[0]++;return false;}); assertEquals(1,calls[0]);
        h.tryAttach("p",2500,(id,p)->{calls[0]++;return true;}); assertEquals(2,calls[0]);
    }
    @Test public void conflictingPackageCannotReplaceEarlyOpen() {
        SessionHints h=new SessionHints(); h.offer(2,"real",0);
        assertFalse(h.offer(2,"spoof",1));
        h.tryAttach("spoof",10,(id,p)->{fail("forged claim reached effect");return true;});
        h.tryAttach("real",20,(id,p)->true); assertEquals(0,h.size());
    }
    @Test public void duplicateOpenCannotExtendTtl() {
        SessionHints h=new SessionHints(); h.offer(2,"p",0); h.offer(2,"p",SessionHints.TTL_MS-1);
        h.prune(SessionHints.TTL_MS+1); assertEquals(0,h.size());
    }
    @Test public void retryBudgetIsFiniteEvenWithDuplicateEvents() {
        SessionHints h=new SessionHints(); int[] calls={0}; h.offer(1,"p",0); long now=0;
        for(int n=0;n<SessionHints.MAX_ATTEMPTS;n++) {
            h.offer(1,"p",now); h.tryAttach("p",now,(id,p)->{calls[0]++;return false;});
            now+=SessionHints.BASE_BACKOFF_MS<<n;
        }
        assertEquals(SessionHints.MAX_ATTEMPTS,calls[0]); assertEquals(0,h.size());
    }
    @Test public void closeBeforePlayingPreventsDelayedAttach() {
        SessionHints h=new SessionHints(); h.offer(1,"p",0); h.close(1,"other"); assertEquals(1,h.size());
        h.close(1,"p"); h.tryAttach("p",2500,(id,p)->{fail("closed session resurrected");return true;});
        assertEquals(0,h.size());
    }
    @Test public void pausedBufferingAndConnectingDifferFromStoppedAndError() {
        for(int state:new int[]{2,3,4,5,6,8,9,10,11}) assertTrue(PlaybackPolicy.retainsSession(state));
        for(int state:new int[]{0,1,7,-1,999}) assertFalse(PlaybackPolicy.retainsSession(state));
    }
    @Test public void knownUidMustMatchClaimedPackage() {
        assertFalse(SessionEventPolicy.senderMatches(true,"player",new String[]{"attacker"}));
        assertFalse(SessionEventPolicy.senderMatches(true,"player",null));
        assertTrue(SessionEventPolicy.senderMatches(true,"player",new String[]{"shared","player"}));
    }
    @Test public void unavailableUidIsNotMistakenForAuthentication() {
        // Compatibility concession, explicitly opt-in and documented as spoofable.
        assertTrue(SessionEventPolicy.senderMatches(false,"player",null));
    }
}
