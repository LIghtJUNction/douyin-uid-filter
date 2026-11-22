package io.github.lightjunction.douyinuidfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PageWalkTest {
    @Test public void emptyInitialPageCanAdvanceToTheNextPage() {
        PageWalk walk = new PageWalk(0L, 32);

        assertTrue(walk.advance(20L, true, false));
        assertEquals(20L, walk.cursor());
        assertTrue(walk.advance(40L, true, false));
        assertEquals(40L, walk.cursor());
    }

    @Test public void matchingPageStopsWithoutConsumingItsNextCursor() {
        PageWalk walk = new PageWalk(0L, 32);
        assertTrue(walk.advance(20L, true, false));

        assertFalse(walk.advance(40L, true, true));
        assertEquals(20L, walk.cursor());
        assertFalse(walk.advance(40L, true, false));
        assertEquals(20L, walk.cursor());
    }

    @Test public void finalPageStopsWithoutConsumingItsNextCursor() {
        PageWalk walk = new PageWalk(20L, 32);

        assertFalse(walk.advance(40L, false, false));
        assertEquals(20L, walk.cursor());
    }

    @Test public void repeatedInitialCursorStopsImmediately() {
        PageWalk walk = new PageWalk(20L, 32);

        assertFalse(walk.advance(20L, true, false));
        assertEquals(20L, walk.cursor());
    }

    @Test public void cycleToAnyPreviouslyVisitedCursorStops() {
        PageWalk walk = new PageWalk(0L, 32);
        assertTrue(walk.advance(20L, true, false));
        assertTrue(walk.advance(40L, true, false));

        assertFalse(walk.advance(20L, true, false));
        assertEquals(40L, walk.cursor());
    }

    @Test public void pageLimitIncludesTheInitialPage() {
        PageWalk walk = new PageWalk(0L, 32);
        for (long cursor = 1L; cursor < 32L; cursor++) {
            assertTrue(walk.advance(cursor, true, false));
        }

        assertFalse(walk.advance(32L, true, false));
        assertEquals(31L, walk.cursor());
    }

    @Test public void onePageLimitMakesNoAdditionalRequest() {
        PageWalk walk = new PageWalk(100L, 1);

        assertFalse(walk.advance(200L, true, false));
        assertEquals(100L, walk.cursor());
    }

    @Test public void uniqueJumpingCursorsArePreservedWithoutArithmetic() {
        PageWalk walk = new PageWalk(7L, 32);
        long largeCursor = 1_000_000_123_456_789L;

        assertTrue(walk.advance(largeCursor, true, false));
        assertEquals(largeCursor, walk.cursor());
        assertTrue(walk.advance(3L, true, false));
        assertEquals(3L, walk.cursor());
        assertTrue(walk.advance(Long.MAX_VALUE, true, false));
        assertEquals(Long.MAX_VALUE, walk.cursor());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAZeroPageLimit() {
        new PageWalk(0L, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsANegativePageLimit() {
        new PageWalk(0L, -1);
    }
}
