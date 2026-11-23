package io.github.lightjunction.douyinuidfilter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Process;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import static org.junit.Assert.*;

/** Android JVM fixture; does not emulate package visibility, URI grant lifecycle or Xposed. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ConfigProviderAndroidTest {
    private Context context;
    private ConfigProvider provider;
    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        provider = Robolectric.buildContentProvider(ConfigProvider.class).create().get();
        ShadowBinder.setCallingUid(Process.myUid());
        context.getSharedPreferences(ConfigProvider.PREFS, 0).edit().clear().commit();
        context.getSharedPreferences(ConfigProvider.DIAGNOSTICS, 0).edit().clear().commit();
    }
    private FilterConfig read() {
        Bundle data = provider.call("get", null, null);
        return new FilterConfig(data.getBoolean("enabled"), data.getString("uid"), data.getLong("revision"));
    }
    @Test public void recreatedReaderAndProviderLoadSavedConfiguration() {
        context.getSharedPreferences(ConfigProvider.PREFS, 0).edit()
                .putBoolean("enabled", true).putString("uid", "123").putLong("revision", 7).commit();
        assertTrue(new ConfigurationReader().read(0, this::read).enabled);
        provider = Robolectric.buildContentProvider(ConfigProvider.class).create().get();
        FilterConfig loaded = new ConfigurationReader().read(0, this::read);
        assertTrue(loaded.enabled);
        assertEquals(7, loaded.revision);
    }
    @Test public void unauthorizedCallerCannotReadOrReport() {
        ShadowBinder.setCallingUid(Process.myUid() + 10000);
        for (String operation : new String[] {"get", "report"}) {
            try { provider.call(operation, null, new Bundle()); fail("caller accepted"); }
            catch (SecurityException expected) { }
        }
    }
    @Test public void diagnosticsAcceptOnlyBoundedStateAndDoNotStoreSensitiveExtras() {
        Bundle data = new Bundle();
        data.putBoolean("ready", true); data.putString("configuration", "unavailable");
        data.putString("uid", "123"); data.putString("cookie", "secret");
        data.putString("comment", "fixture");
        provider.call("report", null, data);
        SharedPreferences diagnostic = context.getSharedPreferences(ConfigProvider.DIAGNOSTICS, 0);
        assertEquals("unavailable", diagnostic.getString("configuration", ""));
        assertFalse(diagnostic.contains("uid"));
        assertFalse(diagnostic.contains("cookie"));
        assertFalse(diagnostic.contains("comment"));
        data.putString("configuration", "arbitrary private text");
        provider.call("report", null, data);
        assertEquals("pending", diagnostic.getString("configuration", ""));
    }
}
