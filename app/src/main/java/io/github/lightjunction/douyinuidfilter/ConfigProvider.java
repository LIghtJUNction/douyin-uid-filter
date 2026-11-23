package io.github.lightjunction.douyinuidfilter;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Only the module and the selected host can read configuration. External writes are rejected. */
public final class ConfigProvider extends ContentProvider {
    public static final String TARGET = "com.ss.android.ugc.aweme";
    public static final String AUTHORITY = "io.github.lightjunction.douyinuidfilter.settings";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY);
    public static final String PREFS = "filter";
    public static final String DIAGNOSTICS = "diagnostics";

    @Override public boolean onCreate() { return true; }

    private void checkCaller() {
        int caller = Binder.getCallingUid();
        if (caller == Process.myUid()) return;
        String[] packages = getContext().getPackageManager().getPackagesForUid(caller);
        if (packages != null) for (String name : packages) if (TARGET.equals(name)) return;
        throw new SecurityException("Only the module and Douyin may access this provider");
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        checkCaller();
        Bundle result = new Bundle();
        if ("get".equals(method)) {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS, 0);
            result.putBoolean("enabled", prefs.getBoolean("enabled", false));
            result.putString("uid", prefs.getString("uid", ""));
            result.putLong("revision", prefs.getLong("revision", 0L));
        } else if ("report".equals(method) && extras != null) {
            // Diagnostics are bounded metadata; no comment, video ID, UID or account data is stored.
            String version = extras.getString("version", "");
            if (version == null) version = "";
            if (version.length() > 40) version = version.substring(0, 40);
            String configuration = extras.getString("configuration", "pending");
            if (!"enabled".equals(configuration) && !"disabled".equals(configuration)
                    && !"cached".equals(configuration) && !"unavailable".equals(configuration)) {
                configuration = "pending";
            }
            getContext().getSharedPreferences(DIAGNOSTICS, 0).edit()
                .putString("configuration", configuration)
                .putBoolean("ready", extras.getBoolean("ready", false))
                .putString("version", version)
                .putInt("hooks", Math.max(0, extras.getInt("hooks", 0)))
                .putLong("last_seen", System.currentTimeMillis()).apply();
        } else {
            throw new IllegalArgumentException("Unsupported operation");
        }
        return result;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
