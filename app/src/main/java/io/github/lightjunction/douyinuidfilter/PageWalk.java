package io.github.lightjunction.douyinuidfilter;

import java.util.HashSet;
import java.util.Set;

/** Bounds an empty-page search while preserving the host's opaque cursors. */
final class PageWalk {
    private final int maxPages;
    private final Set<Long> seenCursors = new HashSet<>();
    private long currentCursor;
    private boolean stopped;

    PageWalk(long initialCursor, int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must include at least the initial page");
        }
        this.maxPages = maxPages;
        currentCursor = initialCursor;
        seenCursors.add(initialCursor);
    }

    boolean advance(long nextCursor, boolean hasMore, boolean hasMatches) {
        if (stopped || hasMatches || !hasMore || seenCursors.size() >= maxPages
                || !seenCursors.add(nextCursor)) {
            stopped = true;
            return false;
        }
        currentCursor = nextCursor;
        return true;
    }

    long cursor() {
        return currentCursor;
    }
}
