package com.autotuneeq.mvp;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class LitePlatformTest {
    @Test public void liteHasNoListenerCaptureOrForegroundServicePermissions() throws Exception {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_PERMISSIONS|PackageManager.GET_SERVICES);
        assertTrue(c.getPackageName().endsWith(".lite"));
        assertTrue(p.services==null || p.services.length==0);
        for(String permission:p.requestedPermissions)
            assertFalse(permission,permission.contains("RECORD_AUDIO") || permission.contains("NOTIFICATION") || permission.contains("FOREGROUND_SERVICE"));
    }
    @Test public void manualProfileSurvivesStoreRecreation() {
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Profile p=new Profile(new double[]{-3,0,3,0,-3},"Lite platform test");
        String key="lite-"+System.nanoTime();
        assertTrue(new ProfileStore(c).put(key,"artist",p));
        assertArrayEquals(p.db,new ProfileStore(c).get(key,"artist").db,0);
    }
}
