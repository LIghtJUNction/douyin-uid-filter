package io.github.lightjunction.douyinuidfilter;

import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Publishes only to the chosen host following a local user action. No settings are in the Intent. */
final class ConfigHandoff {
    private final Context context;
    private final IFilterSettings.Stub endpoint;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int generation;

    ConfigHandoff(Context context) {
        this.context = context.getApplicationContext();
        endpoint = new IFilterSettings.Stub() {
            @Override public Bundle read() {
                return ConfigProvider.authorizedCall(ConfigHandoff.this.context, "get", null);
            }
            @Override public void report(Bundle diagnostics) {
                ConfigProvider.authorizedCall(ConfigHandoff.this.context, "report", diagnostics);
            }
        };
    }

    void publish(boolean launchingHost) {
        long deadline = SystemClock.elapsedRealtime() + (launchingHost ? 20_000 : 0);
        send(++generation, deadline);
    }

    Intent announcement() {
        Bundle data = new Bundle();
        data.putBinder(ConfigChannel.ENDPOINT, endpoint);
        return new Intent(ConfigChannel.ACTION).setPackage(ConfigProvider.TARGET)
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtras(data);
    }

    private void send(int request, long deadline) {
        if (request != generation) return;
        Intent intent = announcement();
        BroadcastReceiver receipt = new BroadcastReceiver() {
            @Override public void onReceive(Context ignored, Intent delivered) {
                if (getResultCode() != Activity.RESULT_OK && SystemClock.elapsedRealtime() < deadline) {
                    main.postDelayed(() -> send(request, deadline), 1_000);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 34) {
            Bundle options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle();
            context.sendOrderedBroadcast(intent, null, options, receipt, main, Activity.RESULT_CANCELED, null, null);
        } else {
            context.sendOrderedBroadcast(intent, null, receipt, main, Activity.RESULT_CANCELED, null, null);
        }
    }
}
