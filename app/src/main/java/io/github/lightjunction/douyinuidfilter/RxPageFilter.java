package io.github.lightjunction.douyinuidfilter;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import de.robv.android.xposed.XposedBridge;

/** Uses the host's existing Rx chain so cancellation also disposes automatic next-page work. */
final class RxPageFilter {
    private final HostAdapter adapter;
    private final Supplier<FilterConfig> configuration;
    private final Method fetch;
    private final Object requester;
    private final Object[] args;
    private final FilterSession session;
    private final BooleanSupplier current;
    private final PageWalk pages;
    private final Class<?> function;
    private final Method concatMap;
    private final Method just;
    private final Method empty;

    RxPageFilter(HostAdapter adapter, Supplier<FilterConfig> configuration, Method fetch,
            Object requester, Object[] args, FilterSession session, BooleanSupplier current)
            throws ReflectiveOperationException {
        this.adapter = adapter; this.configuration = configuration; this.fetch = fetch;
        this.requester = requester; this.args = args.clone(); this.session = session; this.current = current;
        pages = new PageWalk((Long) args[1], 32);
        Class<?> observable = fetch.getReturnType();
        function = Class.forName("io.reactivex.functions.Function", false, observable.getClassLoader());
        concatMap = observable.getMethod("concatMap", function);
        just = observable.getMethod("just", Object.class);
        empty = observable.getMethod("empty");
    }

    Object wrap(Object observable) throws ReflectiveOperationException {
        Object mapper = Proxy.newProxyInstance(function.getClassLoader(), new Class<?>[] {function}, (proxy, method, values) -> {
            switch (method.getName()) {
                case "toString": return "DouyinUidFilterPage";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == values[0];
                case "apply": return apply(values[0]);
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
        return concatMap.invoke(observable, mapper);
    }

    private Object apply(Object wrapper) throws Throwable {
        if (!current.getAsBoolean()) return empty.invoke(null);
        Object response = adapter.responseFrom(wrapper);
        if (response == null) return just.invoke(null, wrapper);
        FilterConfig selected = configuration.get();
        boolean unchanged = HostAdapter.sameConfig(selected, session.config);
        adapter.bind(response, unchanged ? session : new FilterSession(selected));
        Object projected = adapter.projectResponse(response, selected);
        if (selected.enabled && unchanged && pages.advance(adapter.cursor(response),
                adapter.hasMore(response), adapter.hasMatches(response, selected))) {
            Object[] next = args.clone();
            next[1] = pages.cursor();
            next[3] = false;
            Object nextPage = XposedBridge.invokeOriginalMethod(fetch, requester, next);
            return wrap(nextPage);
        }
        return just.invoke(null, adapter.replaceResponse(wrapper, projected));
    }
}
