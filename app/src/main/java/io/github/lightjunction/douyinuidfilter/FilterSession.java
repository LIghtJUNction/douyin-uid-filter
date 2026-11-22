package io.github.lightjunction.douyinuidfilter;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** One comment request session keeps its configuration and accumulated matches. */
final class FilterSession {
    final FilterConfig config;
    private final Set<String> matchingIds = new HashSet<>();

    FilterSession(FilterConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    synchronized long record(Set<String> ids) {
        matchingIds.addAll(Objects.requireNonNull(ids, "ids"));
        return matchingIds.size();
    }

    synchronized long getCount() { return matchingIds.size(); }
}
