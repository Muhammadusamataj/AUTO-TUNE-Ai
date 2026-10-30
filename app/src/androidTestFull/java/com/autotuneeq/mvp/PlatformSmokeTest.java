package com.autotuneeq.mvp;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Storage and platform-JSON integration; never claims acoustic verification. */
@RunWith(AndroidJUnit4.class)
public final class PlatformSmokeTest {
    @Test public void offlineProfileRoundTripAcrossStoreInstances() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String title="instrumented-"+System.nanoTime();
        Profile p=new Profile(new double[]{3,1,0,-1,-3},"device test");
        assertTrue(new ProfileStore(c).put(title,"test",p));
        assertArrayEquals(p.db,new ProfileStore(c).get(title,"test").db,0);
    }
    @Test public void androidEightListenerSettingMatchesExactComponentOnly() {
        android.content.ComponentName expected=new android.content.ComponentName("com.autotuneeq.mvp",
                "com.autotuneeq.mvp.MediaNotificationListener");
        assertTrue(SongMonitor.containsComponent("other/other.Listener:com.autotuneeq.mvp/.MediaNotificationListener",expected));
        assertFalse(SongMonitor.containsComponent("com.autotuneeq.mvp.fake/.MediaNotificationListener",expected));
        assertFalse(SongMonitor.containsComponent("malformed",expected));
        assertFalse(SongMonitor.containsComponent(null,expected));
    }
    @Test public void corruptedEntryFailsClosed() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        String title="malformed-test";
        c.getSharedPreferences("profiles_v2",Context.MODE_PRIVATE).edit()
                .putString("profile:"+TrackIdentity.key(title,"test"),"{broken").commit();
        assertNull(new ProfileStore(c).get(title,"test"));
    }
}
