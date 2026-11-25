package io.github.lightjunction.douyinuidfilter;

import android.app.Application;
import android.app.Activity;
import android.view.Menu;
import android.view.Window;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Legacy API entry supported by LSPosed, Vector and compatible Xposed frameworks. */
public final class ModuleEntry implements IXposedHookLoadPackage {
    private static final String TAG = "DouyinUidFilter";
    private final AtomicBoolean installed = new AtomicBoolean();
    private final ConfigurationReader configurationReader = new ConfigurationReader();
    private String reportedConfiguration = "pending";
    private boolean adapterReady;
    private int installedHooks;
    private String hostVersion = "unknown";
    private Context context;
    private HostAdapter adapter;
    private ConfigChannel settings;
    private final WeakIdentityMap<FilterSession> requestSessions = new WeakIdentityMap<>();
    private long lastError;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!ConfigProvider.TARGET.equals(param.packageName)
                || !ConfigProvider.TARGET.equals(param.processName)) return;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam hook) {
                if (!installed.compareAndSet(false, true)) return;
                context = (Context) hook.args[0];
                settings = new ConfigChannel(context, ModuleEntry.this::onConfigConnected);
                install(context.getClassLoader());
            }
        });
    }

    private synchronized void onConfigConnected(FilterConfig fresh) {
        configurationReader.invalidateRead();
        configuration(() -> fresh);
    }

    private FilterConfig configuration() {
        return configuration(() -> {
            try {
                return ConfigChannel.decode(settings.call("get", null));
            } catch (RuntimeException exception) {
                error("configuration", exception);
                throw exception;
            }
        });
    }

    private synchronized FilterConfig configuration(ConfigurationReader.Source source) {
        FilterConfig selected = configurationReader.read(SystemClock.elapsedRealtime(), source);
        String state = configurationReader.state();
        if (!state.equals(reportedConfiguration)) {
            reportedConfiguration = state;
            // Fixed vocabulary only; never include configuration values or exception messages.
            String message = TAG + ": configuration=" + state;
            Log.i(TAG, message);
            XposedBridge.log(message);
            report(adapterReady, installedHooks, hostVersion);
        }
        return selected;
    }

    private void install(ClassLoader loader) {
        int hooks = 0;
        String version = "unknown";
        try {
            version = context.getPackageManager().getPackageInfo(ConfigProvider.TARGET, 0).versionName;
            adapter = new HostAdapter(loader);
            configuration();
            XposedHelpers.findAndHookMethod(adapter.listType, "getItems", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    FilterConfig selected = configuration();
                    try { param.setResult(adapter.projectItems(param.thisObject, selected)); }
                    catch (Throwable exception) {
                        if (selected.enabled) param.setResult(Collections.emptyList());
                        error("comment list", exception);
                    }
                }
            });
            hooks++;

            Method total = null;
            for (String name : new String[] {"K0", "getTotal"}) {
                try { total = adapter.listType.getDeclaredMethod(name); break; }
                catch (NoSuchMethodException ignored) { }
            }
            if (total == null || total.getReturnType() != long.class) throw new NoSuchMethodException("CommentItemList total getter");
            XposedBridge.hookMethod(total, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) param.setResult(adapter.visibleCount(param.thisObject, configuration()));
                }
            });
            hooks++;

            // Field-backed getter in 41.2.0; stable-name fallback supports older compatible models.
            Method replyGetter = null;
            for (String name : new String[] {"LLLL", "getReplyComments"}) {
                try { replyGetter = adapter.commentType.getDeclaredMethod(name); break; }
                catch (NoSuchMethodException ignored) { }
            }
            if (replyGetter == null || !List.class.isAssignableFrom(replyGetter.getReturnType())) {
                throw new NoSuchMethodException("Comment reply getter");
            }
            XposedBridge.hookMethod(replyGetter, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    FilterConfig selected = configuration();
                    if (param.hasThrowable()) return;
                    try { param.setResult(adapter.projectReplies(param.thisObject, selected)); }
                    catch (Throwable exception) { param.setResult(Collections.emptyList()); error("replies", exception); }
                }
            });
            hooks++;

            // Project parsed models too: some host paths read public fields directly.
            Class<?> gson = XposedHelpers.findClassIfExists("com.google.gson.Gson", loader);
            if (gson != null) {
                for (Method method : gson.getDeclaredMethods()) {
                    if (!method.getName().equals("fromJson") || method.getReturnType() == void.class) continue;
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable() || !adapter.listType.isInstance(param.getResult())) return;
                            FilterConfig selected = configuration();
                            if (!selected.enabled) return;
                            try {
                                Object source = param.getResult();
                                Object projected = adapter.projectResponse(source, selected);
                                param.setResult(projected);
                                Log.i(TAG, "page filtered: loaded=" + adapter.items(source).size()
                                        + " visible=" + adapter.visibleCount(source, selected));
                            } catch (Throwable exception) {
                                param.setThrowable(new IllegalStateException("Comment filtering failed"));
                                error("parsed response", exception);
                            }
                        }
                    });
                    hooks++;
                }
            }
            Class<?> requests = XposedHelpers.findClassIfExists(
                    "com.ss.android.ugc.aweme.comment.data.list.CommentListPageNetRequest", loader);
            if (requests != null) for (Method method : requests.getDeclaredMethods()) {
                Class<?>[] args = method.getParameterTypes();
                if (args.length == 4 && args[1] == long.class && args[2] == int.class && args[3] == boolean.class
                        && method.getReturnType().getName().equals("io.reactivex.Observable")) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            FilterConfig selected = configuration();
                            FilterSession session = requestSessions.get(param.thisObject);
                            if (((Long) param.args[1]) == 0L || session == null || !HostAdapter.sameConfig(selected, session.config)) {
                                session = new FilterSession(selected);
                                requestSessions.put(param.thisObject, session);
                            }
                            param.setObjectExtra("filter_session", session);
                        }
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable() || param.getResult() == null) return;
                            FilterSession session = (FilterSession) param.getObjectExtra("filter_session");
                            if (session == null || !session.config.enabled) return;
                            Object requester = param.thisObject;
                            try {
                                RxPageFilter pager = new RxPageFilter(adapter, ModuleEntry.this::configuration,
                                        method, requester, param.args, session, () -> requestSessions.get(requester) == session);
                                param.setResult(pager.wrap(param.getResult()));
                            } catch (Throwable exception) {
                                param.setThrowable(new IllegalStateException("Comment pagination filtering failed"));
                                error("pagination", exception);
                            }
                        }
                    });
                    hooks++;
                }
            }
            // Only append to an existing Android options menu. Custom host menus
            // need version-specific validation and remain untouched.
            try {
                XposedHelpers.findAndHookMethod(Activity.class, "onCreatePanelMenu", int.class, Menu.class,
                        new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                if (!param.hasThrowable() && Boolean.TRUE.equals(param.getResult())
                                        && ((Integer) param.args[0]) == Window.FEATURE_OPTIONS_PANEL) {
                                    try { HostSettingsEntry.addTo((Activity) param.thisObject, (Menu) param.args[1]); }
                                    catch (RuntimeException unavailable) { error("settings entry", unavailable); }
                                }
                            }
                        });
                hooks++;
            } catch (RuntimeException unavailable) { error("settings entry installation", unavailable); }
            report(true, hooks, version);
            XposedBridge.log(TAG + ": ready, host=" + version + ", hooks=" + hooks);
        } catch (Throwable exception) {
            report(false, hooks, version);
            error("adapter installation", exception);
        }
    }

    private void report(boolean ready, int hooks, String version) {
        adapterReady = ready; installedHooks = hooks; hostVersion = version;
        try {
            Bundle data = new Bundle(); data.putBoolean("ready", ready);
            data.putString("configuration", configurationReader.state());
            data.putInt("hooks", hooks); data.putString("version", version);
            settings.call("report", data);
        } catch (RuntimeException exception) { error("module status", exception); }
    }

    private synchronized void error(String stage, Throwable exception) {
        long now = SystemClock.elapsedRealtime();
        if (lastError != 0 && now - lastError < 10_000) return;
        lastError = now;
        String message = TAG + ": " + stage + " failed: " + exception.getClass().getSimpleName();
        Log.e(TAG, message);
        XposedBridge.log(message);
    }
}
