package com.autotuneeq.mvp;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RecommenderTest {
    private static Recommender.Query q(String genre) { Recommender.Query x=new Recommender.Query(); x.title="T"; x.artist="A"; x.genre=genre; return x; }
    @Test public void priorityIsSavedThenOnlineThenGenreThenNeutral() {
        Recommender.Query x=q("rock"); x.online=new Profile(new double[]{1,0,0,0,0},"curated"); x.onlineSource="curated";
        assertEquals(Recommender.Basis.ONLINE_FOR_SONG,Recommender.recommend(x,null).basis);
        x.saved=new Profile(new double[]{0,1,0,0,0},"mine");
        Recommender.Recommendation r=Recommender.recommend(x,null);
        assertEquals(Recommender.Basis.SAVED_FOR_SONG,r.basis); assertEquals(0.95,r.confidence,0); assertEquals("high",r.confidenceLabel());
        x.saved=null; x.online=null;
        assertEquals(Recommender.Basis.GENRE_PRESET,Recommender.recommend(x,null).basis);
        assertEquals(Recommender.Basis.NEUTRAL,Recommender.recommend(q(""),null).basis);
    }
    @Test public void neutralWhenGenreIsMissingOrUnknownAndSaysSo() {
        for(String g:new String[]{null,"","  ","polka","xyzzy",new String(new char[500]).replace('\0','a')}) {
            Recommender.Recommendation r=Recommender.recommend(q(g),null);
            assertEquals(g,Recommender.Basis.NEUTRAL,r.basis); assertEquals(0,r.confidence,0); assertEquals("none",r.confidenceLabel());
            for(double v:r.profile.db) assertEquals(0,v,0);
            assertTrue(r.explanation().toLowerCase().contains("neutral"));
        }
    }
    @Test public void genreMappingIsConservativeAndLongestKeywordWins() {
        assertEquals("ROCK",Recommender.genreClass("Hard Rock")); assertEquals("ROCK",Recommender.genreClass("indie rock")); assertEquals("ROCK",Recommender.genreClass("Alternative Rock")); assertEquals("POP",Recommender.genreClass("K-Pop")); assertNull(Recommender.genreClass("therapy sessions")); assertNull(Recommender.genreClass("rocksteady"));
        assertEquals("BASS",Recommender.genreClass("Hip-Hop")); assertEquals("BASS",Recommender.genreClass("Alternative R&B"));
        assertEquals("JAZZ",Recommender.genreClass("Smooth Jazz")); assertEquals("POP",Recommender.genreClass("pop"));
        assertEquals("VOCAL",Recommender.genreClass("Podcast")); assertNull(Recommender.genreClass("popular music"));   // "pop" inside a longer word is not a match
        assertNull(Recommender.genreClass("Unknown")); assertNull(Recommender.genreClass(null));
    }
    @Test public void genreSuggestionIsThePresetUntilTheUserTeachesIt() {
        Recommender.Recommendation r=Recommender.recommend(q("Rock"),new PreferenceModel());
        assertArrayEquals(Presets.ROCK.db(),r.profile.db,1e-9); assertEquals(0.40,r.confidence,1e-9); assertEquals("medium",r.confidenceLabel());
        assertEquals(Presets.ROCK,r.preset); assertTrue(r.explanation().contains("rule-based")); assertTrue(r.explanation().contains("genre \"Rock\""));
    }
    @Test public void learningMovesTowardTheUsersChoiceBoundedAndRaisesConfidence() {
        PreferenceModel m=new PreferenceModel(); double[] preset=Presets.ROCK.db(); double[] mine=preset.clone(); mine[0]-=2.0;  // user wants less bass
        double last=Recommender.recommend(q("rock"),m).confidence;
        for(int i=0;i<12;i++) {
            Recommender.Recommendation r=Recommender.recommend(q("rock"),m);
            assertTrue(r.confidence>=last); last=r.confidence; assertTrue(r.confidence<0.70);
            m.learn("ROCK",r.profile.db,mine);
        }
        double[] off=m.offset("ROCK");
        assertTrue("bass offset should be negative",off[0]<-1.0); assertTrue(Math.abs(off[0])<=PreferenceModel.MAX_OFFSET_DB+1e-9);
        for(int i=1;i<5;i++) assertEquals(0,off[i],1e-6);
        Recommender.Recommendation r=Recommender.recommend(q("rock"),m);
        assertEquals(preset[0]+off[0],r.profile.db[0],1e-6); assertTrue(r.explanation().contains("earlier choice"));
        assertEquals(0,m.offset("JAZZ")[0],0);
        for(double v:r.profile.db) assertTrue(Math.abs(v)<=Profile.MAX_DB+1e-9);
    }
    @Test public void thumbsDownHalvesAndResetForgets() {
        PreferenceModel m=new PreferenceModel(); m.learn("POP",new double[5],new double[]{1,0,0,0,0}); double before=m.offset("POP")[0];
        m.thumbsDown("POP"); assertEquals(before/2,m.offset("POP")[0],1e-9); m.reset(); assertEquals(0,m.offset("POP")[0],0); assertEquals(0,m.feedbackCount("POP"));
    }
    @Test public void persistenceRoundTripsAndHostileStateIsHarmless() {
        PreferenceModel m=new PreferenceModel(); m.learn("JAZZ",new double[5],new double[]{0,0,0,0,2});
        PreferenceModel back=PreferenceModel.fromJson(m.toJson());
        assertEquals(m.offset("JAZZ")[4],back.offset("JAZZ")[4],1e-3); assertEquals(1,back.feedbackCount("JAZZ"));
        for(String bad:new String[]{null,"","not json","[]","{\"ROCK\":5}","{\"ROCK\":{\"offset\":[9,9,9,9,9],\"n\":1}}","{\"ROCK\":{\"offset\":[1,2]}}","{\"EVIL\":{\"offset\":[1,1,1,1,1]}}",new String(new char[9000]).replace('\0','{')}) {
            PreferenceModel p=PreferenceModel.fromJson(bad);
            for(String c:new String[]{"ROCK","EVIL"}) for(double v:p.offset(c)) assertTrue(bad+" -> "+v,Math.abs(v)<=PreferenceModel.MAX_OFFSET_DB+1e-9);
        }
        assertEquals(0,PreferenceModel.fromJson("{\"EVIL\":{\"offset\":[1,1,1,1,1]}}").offset("EVIL")[0],0);   // unknown class ignored
        m.learn("ROCK",null,new double[5]); m.learn("ROCK",new double[5],new double[]{Double.NaN,0,0,0,0}); assertEquals(0,m.offset("ROCK")[0],0);
    }
}
