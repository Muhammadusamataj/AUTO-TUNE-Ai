package com.autotuneeq.mvp;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PlayerSupportTest {
    @Test public void youtubeIsUnsupportedUntilAnEventIsActuallySeen() {
        PlayerSupport p=new PlayerSupport();
        assertEquals(PlayerSupport.Status.REPORTED_NO_SESSION_EVENTS,p.status("com.google.android.youtube"));
        assertTrue(p.describe("com.google.android.youtube").contains("UNSUPPORTED"));
        p.sawOpen("com.google.android.youtube");
        assertEquals(PlayerSupport.Status.SESSION_EVENT_SEEN,p.status("com.google.android.youtube"));
        assertFalse(p.describe("com.google.android.youtube").contains("UNSUPPORTED"));
    }
    @Test public void unknownPlayersAreUnverifiedNeverAssumedSupported() {
        PlayerSupport p=new PlayerSupport();
        assertEquals(PlayerSupport.Status.UNVERIFIED,p.status("com.spotify.music"));
        assertEquals(PlayerSupport.Status.UNVERIFIED,p.status(""));
        assertTrue(p.describe("com.spotify.music").contains("unverified"));
        p.sawOpen("com.spotify.music"); assertEquals(PlayerSupport.Status.SESSION_EVENT_SEEN,p.status("com.spotify.music"));
        p.reset(); assertEquals(PlayerSupport.Status.UNVERIFIED,p.status("com.spotify.music"));
    }
    @Test public void silentUnverifiedPlayerIsCalledOutAfterTwentySeconds() {
        PlayerSupport p=new PlayerSupport();
        assertTrue(p.describe("com.spotify.music",5000).contains("unverified"));
        assertTrue(p.describe("com.spotify.music",PlayerSupport.NO_EVENT_AFTER_MS).contains("NOT active"));
        p.sawOpen("com.spotify.music");
        assertTrue(p.describe("com.spotify.music",600000).contains("cooperative"));
        assertTrue(p.describe("com.google.android.youtube",600000).contains("UNSUPPORTED"));
        assertTrue(p.describe("",600000).contains("unverified"));
    }
}
