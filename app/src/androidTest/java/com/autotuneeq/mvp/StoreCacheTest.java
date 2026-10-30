package com.autotuneeq.mvp;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Platform SharedPreferences behaviour of the 0.3.4 cache changes. Not run in the author's environment. */
@RunWith(AndroidJUnit4.class)
public final class StoreCacheTest {
    @Test public void manualProfileSurvivesMoreThanTheCacheCapacityOfOnlineProfiles() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProfileStore store=new ProfileStore(c);
        String stamp="pin-"+System.nanoTime();
        Profile manual=new Profile(new double[]{1,0,-1,0,1},"saved manual test");
        assertTrue(store.putManual(stamp,"artist",manual));
        for(int i=0;i<230;i++) assertTrue(store.put(stamp+"-online-"+i,"artist",Profile.flat()));
        Profile back=store.get(stamp,"artist");
        assertNotNull("manual profile must outlive cached online profiles",back);
        assertArrayEquals(manual.db,back.db,0);
    }
    @Test public void notFoundIsRememberedAndClearedWhenAProfileIsStored() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProfileStore store=new ProfileStore(c);
        String title="miss-"+System.nanoTime();
        assertFalse(store.recentMiss(title,"artist"));
        store.recordMiss(title,"artist");
        assertTrue(store.recentMiss(title,"artist"));
        assertTrue(new ProfileStore(c).recentMiss(title,"artist"));
        assertTrue(store.put(title,"artist",Profile.flat()));
        assertFalse(store.recentMiss(title,"artist"));
    }
}
