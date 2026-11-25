package io.github.lightjunction.douyinuidfilter;

import java.util.function.BiConsumer;

/** Applies the active filter once across nested decoders returning the same page. */
final class DecodedPageProjection {
    private final HostAdapter adapter;
    private final ConfigurationReader.Source configuration;
    private final BiConsumer<Object, FilterConfig> onProjected;
    private final WeakIdentityMap<FilterConfig> projectedWith = new WeakIdentityMap<>();

    DecodedPageProjection(HostAdapter adapter, ConfigurationReader.Source configuration,
            BiConsumer<Object, FilterConfig> onProjected) {
        this.adapter = adapter;
        this.configuration = configuration;
        this.onProjected = onProjected;
    }

    Object apply(Object page) throws ReflectiveOperationException {
        if (page == null || page.getClass() != adapter.listType) return page;

        FilterConfig selected = configuration.read();
        if (!selected.enabled) return adapter.original(page);
        FilterConfig previous = projectedWith.get(page);
        if (previous != null && HostAdapter.sameConfig(previous, selected)) return page;

        Object result = adapter.projectResponse(page, selected);
        projectedWith.put(result, selected);
        onProjected.accept(page, selected);
        return result;
    }
}
