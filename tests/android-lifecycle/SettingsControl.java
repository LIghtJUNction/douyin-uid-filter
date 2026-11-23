package io.github.lightjunction.lifecycle;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Test APK signed like the settings APK; not installed or shipped with production. */
public final class SettingsControl extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) { this.arguments = arguments; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context target = getTargetContext();
            boolean enabled = Boolean.parseBoolean(arguments.getString("enabled", "true"));
            android.content.SharedPreferences prefs = target.getSharedPreferences("filter", 0);
            if (Boolean.parseBoolean(arguments.getString("write", "false"))) {
                if (!prefs.edit().putBoolean("enabled", enabled).putString("uid", "123")
                        .putLong("revision", prefs.getLong("revision", 0) + 1).commit()) {
                    throw new IllegalStateException("save-failed");
                }
            }
            result.putBoolean("enabled", prefs.getBoolean("enabled", false));
            result.putLong("revision", prefs.getLong("revision", 0));
            if (Boolean.parseBoolean(arguments.getString("open_settings", "true"))) {
                Intent intent = new Intent().setClassName(target,
                        "io.github.lightjunction.douyinuidfilter.MainActivity")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Activity activity = startActivitySync(intent);
                waitForIdleSync();
                activity.finish();
            }
            result.putString("result", "saved");
            finish(Activity.RESULT_OK, result);
        } catch (RuntimeException exception) {
            result.putString("error", exception.getClass().getSimpleName());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
