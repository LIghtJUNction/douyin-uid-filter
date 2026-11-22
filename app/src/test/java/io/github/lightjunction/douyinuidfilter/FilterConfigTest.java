package io.github.lightjunction.douyinuidfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FilterConfigTest {
    private static final String UID = "9007199254740993";

    @Test public void matchesLargeUidAsAnExactString() {
        FilterConfig config = new FilterConfig(true, UID, 1L);

        assertTrue(config.matches(UID));
        assertFalse(config.matches("9007199254740992"));
        assertFalse(config.matches("90071992547409930"));
        assertFalse(config.matches("09007199254740993"));
        assertFalse(config.matches(" " + UID));
        assertFalse(config.matches(UID + " "));
        assertFalse(config.matches(null));
    }

    @Test public void trimsConfiguredUidAndPreservesRevision() {
        FilterConfig config = new FilterConfig(true, " \t" + UID + "\n", 42L);

        assertEquals(UID, config.uid);
        assertTrue(config.enabled);
        assertTrue(config.matches(UID));
        assertEquals(42L, config.revision);
    }

    @Test public void acceptsPositiveAsciiIdsWithinLengthLimit() {
        assertTrue(FilterConfig.isValidUid("1"));
        assertTrue(FilterConfig.isValidUid(UID));
        assertTrue(FilterConfig.isValidUid("123456789012345678901234567890"));
    }

    @Test public void rejectsEmptyZeroMalformedAndNonAsciiIds() {
        String[] invalid = {null, "", " ", "0", "00", "0123", "-1", "+1", "1.0",
                "1e9", "12 34", "123\n", " 123", "１２３", "١٢٣", "user123",
                "1234567890123456789012345678901"};

        for (String value : invalid) {
            assertFalse("Must reject UID: " + value, FilterConfig.isValidUid(value));
        }
    }

    @Test public void invalidConfiguredUidCannotEnableFiltering() {
        String[] invalid = {null, "", " \t\n", "0", "-123", "nickname", "１２３"};

        for (String value : invalid) {
            FilterConfig config = new FilterConfig(true, value, 3L);
            assertFalse("Must disable invalid UID: " + value, config.enabled);
            assertFalse(config.matches(value));
        }
    }

    @Test public void explicitDisabledStateNeverMatchesEvenWithValidUid() {
        FilterConfig config = new FilterConfig(false, UID, 7L);

        assertEquals(UID, config.uid);
        assertFalse(config.enabled);
        assertFalse(config.matches(UID));
    }

    @Test public void disabledDefaultHasNoTarget() {
        assertFalse(FilterConfig.DISABLED.enabled);
        assertEquals("", FilterConfig.DISABLED.uid);
        assertFalse(FilterConfig.DISABLED.matches(""));
        assertFalse(FilterConfig.DISABLED.matches(UID));
    }
}
