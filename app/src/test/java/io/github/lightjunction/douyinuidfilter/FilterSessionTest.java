package io.github.lightjunction.douyinuidfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public final class FilterSessionTest {
    private static final FilterConfig CONFIG = new FilterConfig(true, "123456789", 1L);

    private static Set<String> ids(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test public void capturesConfigurationAndStartsWithZero() {
        FilterSession session = new FilterSession(CONFIG);

        assertSame(CONFIG, session.config);
        assertEquals(0L, session.getCount());
    }

    @Test public void accumulatesDistinctIdsAcrossPages() {
        FilterSession session = new FilterSession(CONFIG);

        assertEquals(2L, session.record(ids("101", "102")));
        assertEquals(3L, session.record(ids("102", "103")));
        assertEquals(3L, session.record(ids("101", "102", "103")));
        assertEquals(3L, session.getCount());
    }

    @Test public void emptyTailPagePreservesExistingMatches() {
        FilterSession session = new FilterSession(CONFIG);
        session.record(ids("101", "102"));

        assertEquals(2L, session.record(Collections.emptySet()));
        assertEquals(2L, session.getCount());
    }

    @Test public void emptyInitialPageDoesNotPreventLaterMatches() {
        FilterSession session = new FilterSession(CONFIG);

        assertEquals(0L, session.record(Collections.emptySet()));
        assertEquals(1L, session.record(ids("101")));
        assertEquals(1L, session.record(Collections.emptySet()));
    }

    @Test public void independentVideoSessionsDoNotShareCounts() {
        FilterSession first = new FilterSession(CONFIG);
        FilterSession second = new FilterSession(CONFIG);

        first.record(ids("101", "102"));
        assertEquals(0L, second.getCount());
        second.record(ids("101"));

        assertEquals(2L, first.getCount());
        assertEquals(1L, second.getCount());
    }

    @Test public void refreshSessionIsUnaffectedByLateOldSessionResults() {
        FilterSession oldSession = new FilterSession(CONFIG);
        oldSession.record(ids("101"));
        FilterSession refreshed = new FilterSession(CONFIG);
        refreshed.record(ids("201"));

        oldSession.record(ids("102", "103"));

        assertEquals(3L, oldSession.getCount());
        assertEquals(1L, refreshed.getCount());
    }

    @Test public void configurationChangeHasItsOwnCapturedConfigAndCount() {
        FilterSession oldSession = new FilterSession(CONFIG);
        FilterConfig changedConfig = new FilterConfig(true, "987654321", 2L);
        FilterSession changed = new FilterSession(changedConfig);
        oldSession.record(ids("101"));

        assertSame(CONFIG, oldSession.config);
        assertSame(changedConfig, changed.config);
        assertEquals(0L, changed.getCount());
    }

    @Test public void recordingCopiesIdsInsteadOfRetainingCallersSet() {
        FilterSession session = new FilterSession(CONFIG);
        Set<String> page = ids("101");
        session.record(page);

        page.clear();
        page.add("102");

        assertEquals(1L, session.getCount());
        assertEquals(2L, session.record(page));
    }

    @Test(expected = NullPointerException.class) public void rejectsNullConfiguration() {
        new FilterSession(null);
    }
}
