package com.autotuneeq.mvp;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PresetsAndExchangeTest {
    @Test public void everyPresetIsInsideTheSafeRangeAndAppliesAsCutOnlyWithinSixDb() {
        assertEquals(7,Presets.values().length);
        for(Presets p:Presets.values()) {
            double[] d=p.db(); assertEquals(5,d.length);
            for(double v:d) assertTrue(p+" outside +/-3 dB",Math.abs(v)<=Profile.MAX_DB);
            double max=0,min=0; for(double v:d) { max=Math.max(max,v); min=Math.min(min,v); }
            assertTrue(p+" span",max-min<=6.0+1e-9);
            assertEquals(5,p.profile().db.length);
        }
        for(double v:Presets.FLAT.db()) assertEquals(0,v,0);
        assertTrue(Presets.BASS.db()[0]>0 && Presets.BASS.db()[0]>=Presets.BASS.db()[4]);
        assertTrue(Presets.VOCAL.db()[2]>0 && Presets.VOCAL.db()[3]>0);
        assertFalse(Presets.CUSTOM.builtIn()); assertTrue(Presets.ROCK.builtIn());
    }
    @Test public void presetArraysAreDefensiveCopiesAndIdsNeverThrow() {
        Presets.BASS.db()[0]=-3; assertEquals(3.0,Presets.BASS.db()[0],0);
        assertEquals(Presets.JAZZ,Presets.fromId("jazz")); assertEquals(Presets.ROCK,Presets.fromId(" ROCK "));
        assertEquals(Presets.FLAT,Presets.fromId(null)); assertEquals(Presets.FLAT,Presets.fromId("nonsense")); assertEquals(Presets.FLAT,Presets.fromId(""));
    }
    @Test public void exportThenImportRoundTrips() {
        List<ProfileExchange.Entry> in=new ArrayList<>();
        in.add(ProfileExchange.song("Caf\u00e9 Song","The Artist",new Profile(new double[]{1,-1,0.5,0,3},"x")));
        in.add(ProfileExchange.preset("My night mode",new Profile(new double[]{-3,0,0,0,1.25},"x")));
        ProfileExchange.Result r=ProfileExchange.parse(ProfileExchange.export(in));
        assertFalse(r.fatal); assertEquals(2,r.accepted.size()); assertTrue(r.rejected.isEmpty());
        ProfileExchange.Entry a=r.accepted.get(0), b=r.accepted.get(1);
        assertEquals("song",a.kind); assertEquals("Caf\u00e9 Song",a.title); assertEquals("The Artist",a.artist); assertArrayEquals(new double[]{1,-1,0.5,0,3},a.db,1e-9);
        assertEquals("preset",b.kind); assertEquals("",b.artist); assertEquals("imported",a.profile().source);
    }
    @Test public void hostileImportsAreRefusedOrSkippedNeverApplied() {
        assertTrue(ProfileExchange.parse("").fatal); assertTrue(ProfileExchange.parse(null).fatal); assertTrue(ProfileExchange.parse("[]").fatal);
        assertTrue(ProfileExchange.parse("{\"format\":\"other\"}").fatal);
        assertTrue(ProfileExchange.parse("{\"format\":\"autotuneeq-profiles\",\"version\":2,\"profiles\":[]}").fatal);
        assertTrue(ProfileExchange.parse(new String(new char[ProfileExchange.MAX_BYTES+1]).replace('\0',' ')).fatal);
        StringBuilder deep=new StringBuilder(); for(int i=0;i<100000;i++) deep.append('['); assertTrue(ProfileExchange.parse(deep.toString()).fatal);
        String head="{\"format\":\"autotuneeq-profiles\",\"version\":1,\"profiles\":[";
        String[] bad={
            "{\"kind\":\"song\",\"title\":\"A\",\"artist\":\"B\",\"bands_db\":[0,0,0,0,9]}",                 // out of range
            "{\"kind\":\"song\",\"title\":\"A\",\"artist\":\"B\",\"bands_db\":[0,0,0,0]}",                   // 4 bands
            "{\"kind\":\"song\",\"title\":\"A\",\"artist\":\"B\",\"bands_db\":[0,0,0,0,\"1\"]}",              // string number
            "{\"kind\":\"song\",\"title\":\"A\",\"artist\":\"B\",\"bands_db\":[0,0,0,0,null]}",
            "{\"kind\":\"preset\",\"title\":\"P\",\"artist\":\"X\",\"bands_db\":[0,0,0,0,0]}",                // preset with artist
            "{\"kind\":\"evil\",\"title\":\"A\",\"artist\":\"B\",\"bands_db\":[0,0,0,0,0]}",
            "{\"kind\":\"song\",\"title\":\"\",\"artist\":\"B\",\"bands_db\":[0,0,0,0,0]}",
            "{\"kind\":\"song\",\"title\":\"A\\u0000\",\"artist\":\"B\",\"bands_db\":[0,0,0,0,0]}",
            "{\"kind\":\"song\",\"title\":\""+new String(new char[201]).replace('\0','x')+"\",\"artist\":\"B\",\"bands_db\":[0,0,0,0,0]}",
            "42","\"text\""};
        for(String item:bad) { ProfileExchange.Result r=ProfileExchange.parse(head+item+"]}"); assertFalse(item,r.fatal); assertTrue(item,r.accepted.isEmpty()); assertEquals(item,1,r.rejected.size()); }
        ProfileExchange.Result mixed=ProfileExchange.parse(head+bad[0]+",{\"kind\":\"song\",\"title\":\"Ok\",\"artist\":\"Me\",\"bands_db\":[1,1,1,1,1]}]}");
        assertEquals(1,mixed.accepted.size()); assertEquals(1,mixed.rejected.size()); assertTrue(mixed.summary().contains("1 imported"));
        StringBuilder many=new StringBuilder(head); for(int i=0;i<201;i++) many.append(i>0?",":"").append("{\"kind\":\"song\",\"title\":\"T").append(i).append("\",\"artist\":\"A\",\"bands_db\":[0,0,0,0,0]}");
        assertTrue(ProfileExchange.parse(many+"]}").fatal);
    }
    @Test public void duplicatesCollapseByIdentityKeyAndKeepTheLast() {
        String t="{\"format\":\"autotuneeq-profiles\",\"version\":1,\"profiles\":["
            +"{\"kind\":\"song\",\"title\":\"Yellow (Official Video)\",\"artist\":\"Coldplay\",\"bands_db\":[1,0,0,0,0]},"
            +"{\"kind\":\"song\",\"title\":\"Coldplay - Yellow\",\"artist\":\"Coldplay - Topic\",\"bands_db\":[2,0,0,0,0]}]}";
        ProfileExchange.Result r=ProfileExchange.parse(t);
        assertEquals(1,r.accepted.size()); assertEquals(2.0,r.accepted.get(0).db[0],0);
    }
}
