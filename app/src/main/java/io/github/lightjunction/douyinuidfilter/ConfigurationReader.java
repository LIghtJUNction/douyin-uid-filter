package io.github.lightjunction.douyinuidfilter;

/** Process-local cache. A failed read never becomes an authoritative disabled setting. */
final class ConfigurationReader {
    interface Source { FilterConfig read(); }
    private FilterConfig config = FilterConfig.DISABLED;
    private boolean attempted;
    private boolean loaded;
    private long lastRead;
    private String state = "pending";

    synchronized FilterConfig read(long now, Source source) {
        if (attempted && now - lastRead < 500) return config;
        attempted = true;
        lastRead = now;
        try {
            FilterConfig next = source.read();
            if (next == null) throw new IllegalStateException("Empty configuration");
            config = next;
            loaded = true;
            state = config.enabled ? "enabled" : "disabled";
        } catch (RuntimeException exception) {
            state = loaded ? "cached" : "unavailable";
        }
        return config;
    }

    synchronized void invalidateRead() { attempted = false; }

    synchronized String state() { return state; }
}
