package io.github.lightjunction.douyinuidfilter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import java.util.ArrayList;
import java.util.List;
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
    @Test public void multiUidConfigurationSurvivesProviderRecreationAndEmptyListOverridesLegacy() {
        context.getSharedPreferences(ConfigProvider.PREFS, 0).edit().putBoolean("enabled", true)
                .putString("uid", "789").putString("uids", "123\n456").commit();
        provider = Robolectric.buildContentProvider(ConfigProvider.class).create().get();
        assertTrue(read().matches("123")); assertTrue(read().matches("456"));
        assertFalse(read().matches("789"));
        context.getSharedPreferences(ConfigProvider.PREFS, 0).edit().putString("uids", "").commit();
        assertFalse(read().enabled);
    }
    @Test public void unauthorizedCallerCannotReadOrReport() {
        ShadowBinder.setCallingUid(Process.myUid() + 10000);
        for (String operation : new String[] {"get", "report"}) {
            try { provider.call(operation, null, new Bundle()); fail("caller accepted"); }
            catch (SecurityException expected) { }
        }
    }
    @Test public void publishedEndpointUsesTheSameSettingsAndChecksEachCaller() throws RemoteException {
        ConfigHandoff handoff = new ConfigHandoff(context);
        Intent sent = handoff.announcement();
        assertEquals(ConfigChannel.ACTION, sent.getAction());
        assertEquals(ConfigProvider.TARGET, sent.getPackage());
        assertEquals(java.util.Collections.singleton(ConfigChannel.ENDPOINT), sent.getExtras().keySet());
        IFilterSettings remote = IFilterSettings.Stub.asInterface(sent.getExtras().getBinder(ConfigChannel.ENDPOINT));
        SharedPreferences prefs = context.getSharedPreferences(ConfigProvider.PREFS, 0);
        prefs.edit().putBoolean("enabled", true).putString("uid", "123").putLong("revision", 3).commit();
        assertEquals("123", remote.read().getString("uids"));
        prefs.edit().putString("uids", "").putBoolean("enabled", false).putLong("revision", 4).commit();
        Bundle current = remote.read();
        assertFalse(current.getBoolean("enabled"));
        assertEquals("", current.getString("uids"));
        assertEquals(4, current.getLong("revision"));
        Bundle report = new Bundle();
        report.putString("configuration", "disabled");
        remote.report(report);
        assertEquals("disabled", context.getSharedPreferences(ConfigProvider.DIAGNOSTICS, 0)
                .getString("configuration", ""));
        ShadowBinder.setCallingUid(Process.myUid() + 10000);
        try { remote.read(); fail("untrusted client could read settings"); }
        catch (SecurityException expected) { }
        try { remote.report(report); fail("untrusted client could change diagnostics"); }
        catch (SecurityException expected) { }
    }
    @Test public void handoffAcknowledgesTheFreshEnabledOrDisabledRevision() {
        List<FilterConfig> applied = new ArrayList<>();
        ConfigChannel channel = new ConfigChannel(context, applied::add);
        IBinder endpoint = new ConfigHandoff(context).announcement().getExtras().getBinder(ConfigChannel.ENDPOINT);
        SharedPreferences prefs = context.getSharedPreferences(ConfigProvider.PREFS, 0);
        prefs.edit().putBoolean("enabled", true).putString("uid", "123").putLong("revision", 7).commit();
        assertEquals(Activity.RESULT_OK, channel.acceptEndpoint(endpoint));
        assertTrue(applied.get(0).matches("123"));
        assertEquals(7, applied.get(0).revision);
        prefs.edit().putBoolean("enabled", false).putString("uids", "").putLong("revision", 8).commit();
        assertEquals(Activity.RESULT_OK, channel.acceptEndpoint(endpoint));
        assertFalse(applied.get(1).enabled);
        assertTrue(applied.get(1).uids.isEmpty());
        assertEquals(8, applied.get(1).revision);
        ShadowBinder.setCallingUid(Process.myUid() + 10000);
        assertEquals(Activity.RESULT_CANCELED, channel.acceptEndpoint(endpoint));
        assertEquals(2, applied.size());
    }

    @Test public void missingMalformedOrDisconnectedHandoffsCannotAcknowledgeAnEarlierRead() {
        List<FilterConfig> applied = new ArrayList<>();
        ConfigChannel channel = new ConfigChannel(context, applied::add);
        context.getSharedPreferences(ConfigProvider.PREFS, 0).edit()
                .putBoolean("enabled", true).putString("uids", "456").putLong("revision", 9).commit();
        IBinder previous = new ConfigHandoff(context).announcement().getExtras().getBinder(ConfigChannel.ENDPOINT);
        assertEquals(Activity.RESULT_OK, channel.acceptEndpoint(previous));
        assertEquals(Activity.RESULT_CANCELED, channel.acceptEndpoint(null));
        Bundle malformed = new Bundle();
        malformed.putString("enabled", "false");
        malformed.putString("uids", "");
        malformed.putLong("revision", 10);
        IFilterSettings.Stub failing = new IFilterSettings.Stub() {
            int reads;
            @Override public Bundle read() throws RemoteException {
                switch (++reads) {
                    case 1: return null;
                    case 2: return Bundle.EMPTY;
                    case 3: return malformed;
                    default: throw new RemoteException();
                }
            }
            @Override public void report(Bundle ignored) { }
        };
        for (int attempt = 0; attempt < 4; attempt++) {
            assertEquals(Activity.RESULT_CANCELED, channel.acceptEndpoint(failing));
        }
        assertEquals(1, applied.size());
        assertTrue(applied.get(0).matches("456"));
        assertEquals(9, channel.call("get", null).getLong("revision"));
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
