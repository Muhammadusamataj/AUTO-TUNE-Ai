package com.autotuneeq.mvp;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DiagAndPollTest {
    @Test public void diagLogNeverContainsTitlesAndIsBounded() {
        DiagLog d=new DiagLog();
        String secretKey=TrackIdentity.key("Very Secret Song Title","Hidden Artist");
        String ref=d.songRef(secretKey);
        assertTrue(ref.matches("song#[0-9a-f]{6}"));
        assertEquals(ref,d.songRef(secretKey));
        assertNotEquals(ref,d.songRef(TrackIdentity.key("Another","Artist")));
        assertNotEquals("salt differs per run",ref,new DiagLog().songRef(secretKey));
        d.event("song",ref+" player=com.example.player");
        d.event("weird","line\nbreak\u0000 and tab\t"+new String(new char[500]).replace('\0','x'));
        String out=d.dump();
        assertFalse(out.contains("Secret")); assertFalse(out.contains("Hidden")); assertTrue(out.contains(ref));
        for(String line:out.split("\n")) assertTrue(line.length()<DiagLog.MAX_DETAIL+60);
        assertFalse(out.substring(out.indexOf("weird")).contains("\u0000"));
        for(int i=0;i<1000;i++) d.event("x","e"+i);
        assertEquals(DiagLog.MAX_LINES,d.size());
        assertTrue(d.dump().contains("e999")); assertFalse(d.dump().contains(" e0\n"));
    }
    @Test public void pollGateCoalescesBurstsButNeverStarvesThePoll() {
        PollGate g=new PollGate();
        g.scheduled(1000,2500);                                       // periodic poll is pending far in the future
        assertEquals(PollGate.SOON_MS,g.requestSoon(1000));          // first signal pulls one poll forward
        for(int t=1001;t<1250;t+=10) assertEquals(-1,g.requestSoon(t)); // burst inside the window adds nothing
        g.scheduled(1250,2500);                                       // poll ran and rescheduled itself
        assertEquals(PollGate.SOON_MS,g.requestSoon(1300));           // a new signal pulls the next poll forward
        assertEquals(-1,g.requestSoon(1301));
        g.scheduled(5000,0);                                          // poll due right now
        assertEquals(-1,g.requestSoon(5000));
    }
    @Test public void missCacheHonoursItsOwnTtl() {
        MissCache errors=new MissCache(2*60*1000L);
        errors.record("k",1000);
        assertTrue(errors.isMiss("k",1000+2*60*1000L)); assertFalse(errors.isMiss("k",1001+2*60*1000L));
        assertTrue(MissCache.TTL_MS>2*60*1000L);
    }
    @Test public void abSummaryLabelsBandsWithFrequencies() {
        AbSession s=new AbSession(9,3,2,1);
        while(!s.done()) s.answer(AbSession.Answer.SAME);
        String text=s.summary(new int[]{60,910,14000}).text;
        assertTrue(text.contains("band 0 (60 Hz)")); assertTrue(text.contains("band 2 (14000 Hz)")); assertTrue(text.contains("NOT HEARD"));
        assertFalse(s.summary(2).text.contains(" Hz)"));
    }
}
