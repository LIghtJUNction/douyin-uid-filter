package io.github.lightjunction.lifecycle;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.util.Log;
import android.widget.TextView;
import io.github.lightjunction.douyinuidfilter.FilterConfig;
import org.json.JSONObject;

/** Synthetic host only. No network, account, comment models or real Douyin code. */
public final class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        JSONObject result = new JSONObject();
        try {
            result.put("pid", android.os.Process.myPid());
            Bundle values = getContentResolver().call(Uri.parse(
                    "content://io.github.lightjunction.douyinuidfilter.settings"), "get", null, null);
            if (values == null) throw new IllegalStateException("no-data");
            FilterConfig selected = new FilterConfig(values.getBoolean("enabled"),
                    values.getString("uid", ""), values.getLong("revision"));
            int visible = 0;
            for (String virtualAuthor : new String[] {"123", "456"}) {
                if (!selected.enabled || selected.matches(virtualAuthor)) visible++;
            }
            result.put("state", "read");
            result.put("enabled", selected.enabled);
            result.put("revision", selected.revision);
            result.put("visible", visible);
        } catch (RuntimeException exception) {
            try { result.put("state", "unavailable"); result.put("error", exception.getClass().getSimpleName()); }
            catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
        } catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
        String payload = result.toString();
        TextView view = new TextView(this); view.setText(payload); setContentView(view);
        Log.i("LifecycleProbe", payload);
    }
}
