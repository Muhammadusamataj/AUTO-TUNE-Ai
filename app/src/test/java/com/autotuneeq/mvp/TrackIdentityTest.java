package com.autotuneeq.mvp;

import org.junit.Test;
import static org.junit.Assert.*;

/** Normalization removes packaging noise only; it must never merge different recordings/versions. */
public final class TrackIdentityTest {
    private static String k(String t,String a) { return TrackIdentity.key(t,a); }
    @Test public void youtubeStyleNoiseMapsToTheCleanRecording() {
        String clean=k("Yellow","Coldplay");
        assertEquals(clean,k("Coldplay - Yellow (Official Video)","Coldplay"));
        assertEquals(clean,k("Coldplay \u2013 Yellow [Official Music Video] [4K]","Coldplay - Topic"));
        assertEquals(clean,k("Yellow - Official Video","Coldplay"));
        assertEquals(clean,k("Yellow | Official Music Video","Coldplay"));
        assertEquals(clean,k("Yellow (Official Video) [HD]","COLDPLAY"));
        assertEquals(clean,k("Yellow (Lyrics)","Coldplay")); assertEquals(clean,k("Yellow (Audio)","Coldplay"));
        assertEquals(clean,k("Yellow [1080p]","Coldplay")); assertEquals(clean,k("Yellow (Official Lyric Video)","Coldplay"));
    }
    @Test public void differentVersionsAreNeverMerged() {
        String clean=k("Yellow","Coldplay");
        for(String v:new String[]{"Yellow (Live)","Yellow (Remix)","Yellow (Remastered 2011)","Yellow (feat. X)","Yellow (Radio Edit)","Yellow (Acoustic)","Yellow (Instrumental)","Yellow (Demo)","Yellow Part 2","Yellow (Official Video","Yellow (Official Video]"})
            assertNotEquals(v,clean,k(v,"Coldplay"));
        assertNotEquals(clean,k("Yellow","Coldplay Tribute"));
    }
    @Test public void artistPrefixIsStrippedOnlyWhenItRepeatsTheArtistField() {
        assertNotEquals(k("Yellow","Coldplay"),k("Other Artist - Yellow","Coldplay"));
        assertNotEquals(k("Yellow","Coldplay"),k("Coldplay-Yellow","Coldplay"));      // needs spaces around the dash
        assertEquals(k("Yellow","Coldplay"),k("Coldplay \u2014 Yellow","Coldplay"));
        assertEquals("yellow",TrackIdentity.canonicalTitle("Coldplay - Yellow","Coldplay"));
        assertEquals("coldplay -",TrackIdentity.canonicalTitle("Coldplay -","Coldplay"));    // never reduces to an empty title
    }
    @Test public void greekFinalSigmaAndControlCharactersAreStable() {
        assertEquals(k("\u03a3\u039f\u03a6\u039f\u03a3","A"),k("\u03c3\u03bf\u03c6\u03bf\u03c2","A"));
        assertEquals("a\u0000b",TrackIdentity.canonicalTitle("a\u0000b","x"));
        assertEquals("a b",TrackIdentity.canonicalTitle("a\u001cb","x"));
        assertEquals("a b",TrackIdentity.canonicalTitle("a\u0085b","x"));
    }
    @Test public void normalizationIsIdempotent() {
        String[][] samples={{"Coldplay - Yellow (Official Video) [4K]","Coldplay - Topic"},{"A  (Lyrics)","b"},{"x","y"},{"","" }};
        for(String[] s:samples) {
            String t=TrackIdentity.canonicalTitle(s[0],s[1]), a=TrackIdentity.canonicalArtist(s[1]);
            assertEquals(t,TrackIdentity.canonicalTitle(t,a)); assertEquals(a,TrackIdentity.canonicalArtist(a));
        }
    }
    @Test public void legacyKeyKeepsTheOldFormatForCacheMigration() {
        assertEquals("6:artistsong",TrackIdentity.legacyKey("Song (Official Video)","Artist"));
        assertEquals("6:artistsong (lyrics)",TrackIdentity.legacyKey("Song (Lyrics)","Artist"));   // old rules did not strip this
        assertNotEquals(TrackIdentity.legacyKey("Song (Lyrics)","Artist"),k("Song (Lyrics)","Artist"));
    }
}
