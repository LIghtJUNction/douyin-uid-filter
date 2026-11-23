package io.github.lightjunction.douyinuidfilter;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Process;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Switch;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBinder;
import org.robolectric.util.ReflectionHelpers;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SettingsUiTest {
    private SharedPreferences prefs;
    @Before public void clear() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences(ConfigProvider.PREFS, 0);
        prefs.edit().clear().commit();
        ShadowBinder.setCallingUid(Process.myUid());
    }
    @Test public void oldSingleUidSurvivesUpgradeAndSavesAsMultiUid() {
        prefs.edit().putBoolean("enabled", true).putString("uid", "123").commit();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        EditText input = ReflectionHelpers.getField(activity, "uid");
        assertEquals("123", input.getText().toString());
        input.setText(" 123 \n456\n123\n");
        ReflectionHelpers.callInstanceMethod(activity, "save");
        assertEquals("123\n456", prefs.getString("uids", ""));
        assertFalse(prefs.contains("uid"));
        MainActivity reopened = Robolectric.buildActivity(MainActivity.class).setup().get();
        EditText coldInput = ReflectionHelpers.getField(reopened, "uid");
        assertEquals("123\n456", coldInput.getText().toString());
    }
    @Test public void invalidOrEmptyEnabledEditDoesNotOverwriteSavedConfig() {
        prefs.edit().putBoolean("enabled", true).putString("uids", "123\n456").commit();
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        EditText input = ReflectionHelpers.getField(activity, "uid");
        for (String invalid : new String[] {"123\nname", "\n"}) {
            input.setText(invalid);
            ReflectionHelpers.callInstanceMethod(activity, "save");
            assertNotNull(input.getError());
            assertEquals("123\n456", prefs.getString("uids", ""));
        }
        Switch enabled = ReflectionHelpers.getField(activity, "enabled");
        enabled.setChecked(false); input.setText("");
        ReflectionHelpers.callInstanceMethod(activity, "save");
        assertFalse(prefs.getBoolean("enabled", true));
        assertEquals("", prefs.getString("uids", "missing"));
    }
    public static class SyntheticHost extends Activity {
        @Override public String getPackageName() { return ConfigProvider.TARGET; }
    }
    @Test public void existingHostMenuGetsSingleClearlyNamedEntryOpeningSettings() {
        SyntheticHost host = Robolectric.buildActivity(SyntheticHost.class).setup().get();
        Menu menu = new PopupMenu(host, new View(host)).getMenu();
        assertFalse(HostSettingsEntry.addTo(host, menu));
        menu.add("Existing host action");
        assertTrue(HostSettingsEntry.addTo(host, menu));
        assertFalse(HostSettingsEntry.addTo(host, menu));
        assertEquals(2, menu.size());
        assertEquals("UID 评论过滤", menu.getItem(1).getTitle().toString());
        assertTrue(menu.performIdentifierAction(menu.getItem(1).getItemId(), 0));
        Intent intent = shadowOf(host).getNextStartedActivity();
        assertEquals(MainActivity.class.getName(), intent.getComponent().getClassName());
    }
    @Test public void unrelatedPackageMenuIsUntouched() {
        Activity unrelated = Robolectric.buildActivity(Activity.class).setup().get();
        Menu menu = new PopupMenu(unrelated, new View(unrelated)).getMenu();
        menu.add("Existing");
        assertFalse(HostSettingsEntry.addTo(unrelated, menu)); assertEquals(1, menu.size());
    }
}
