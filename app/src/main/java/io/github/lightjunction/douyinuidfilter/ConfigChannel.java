package io.github.lightjunction.douyinuidfilter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import java.util.function.Consumer;

/** The module supplies its settings endpoint when the user opens or saves the settings screen. */
final class ConfigChannel extends BroadcastReceiver {
    static final String ACTION = BuildConfig.APPLICATION_ID + ".CONFIGURATION_READY";
    static final String PERMISSION = BuildConfig.APPLICATION_ID + ".permission.PUBLISH_CONFIGURATION";
    static final String ENDPOINT = "endpoint";
    private final Context context;
    private final Consumer<FilterConfig> connected;
    private volatile IFilterSettings endpoint;

    ConfigChannel(Context context, Consumer<FilterConfig> connected) {
        this.context = context;
        this.connected = connected;
        IntentFilter filter = new IntentFilter(ACTION);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(this, filter, PERMISSION, null, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(this, filter, PERMISSION, null);
            }
            Log.i("DouyinUidFilter", "Settings receiver ready");
        } catch (RuntimeException error) {
            Log.w("DouyinUidFilter", "Settings receiver unavailable: " + error.getClass().getSimpleName());
        }
    }

    @Override public void onReceive(Context ignored, Intent intent) {
        if (!ACTION.equals(intent.getAction()) || !ConfigProvider.TARGET.equals(intent.getPackage())) return;
        if (Build.VERSION.SDK_INT >= 34 && !BuildConfig.APPLICATION_ID.equals(getSentFromPackage())) return;
        Bundle extras = intent.getExtras();
        IBinder binder = extras == null ? null : extras.getBinder(ENDPOINT);
        int receipt = acceptEndpoint(binder);
        if (isOrderedBroadcast()) setResultCode(receipt);
    }

    int acceptEndpoint(IBinder binder) {
        if (binder == null || !binder.isBinderAlive()) return Activity.RESULT_CANCELED;
        IFilterSettings offered = IFilterSettings.Stub.asInterface(binder);
        FilterConfig fresh;
        try {
            // Only a read from this exact endpoint can acknowledge the publisher's handoff.
            fresh = decode(offered.read());
        } catch (RemoteException | RuntimeException error) {
            Log.w("DouyinUidFilter", "Settings handoff read failed: " + error.getClass().getSimpleName());
            return Activity.RESULT_CANCELED;
        }
        endpoint = offered;
        connected.accept(fresh);
        Log.i("DouyinUidFilter", "Settings endpoint received");
        return Activity.RESULT_OK;
    }

    @SuppressWarnings("deprecation")
    static FilterConfig decode(Bundle values) {
        if (values == null) throw new IllegalArgumentException("Missing configuration");
        Object enabled = values.get("enabled");
        Object revision = values.get("revision");
        Object uids = values.get(values.containsKey("uids") ? "uids" : "uid");
        if (!(enabled instanceof Boolean) || !(revision instanceof Long) || !(uids instanceof String)) {
            throw new IllegalArgumentException("Incomplete configuration");
        }
        return new FilterConfig((Boolean) enabled, (String) uids, (Long) revision);
    }

    Bundle call(String method, Bundle extras) {
        IFilterSettings current = endpoint;
        if (current != null) {
            try {
                if ("get".equals(method)) return current.read();
                current.report(extras);
                return Bundle.EMPTY;
            } catch (RemoteException disconnected) {
                // A newly opened settings screen supplies a fresh endpoint.
            }
        }
        return context.getContentResolver().call(ConfigProvider.URI, method, null, extras);
    }
}
