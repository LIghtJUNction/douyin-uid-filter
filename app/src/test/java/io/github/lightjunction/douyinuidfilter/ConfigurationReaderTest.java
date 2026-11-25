package io.github.lightjunction.douyinuidfilter;

import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigurationReaderTest {
    private final FilterConfig enabled = new FilterConfig(true, "123", 1);
    private static FilterConfig denied() { throw new SecurityException("fixture"); }

    @Test public void serviceConnectionRetriesImmediatelyAfterAnUnavailableRead() {
        ConfigurationReader reader = new ConfigurationReader();
        reader.read(50, () -> null);
        assertEquals("unavailable", reader.state());
        reader.invalidateRead();
        assertTrue(reader.read(51, () -> new FilterConfig(true, "123", 3)).matches("123"));
        assertEquals("enabled", reader.state());
    }
    @Test public void freshProcessReadsPersistedSetting() {
        assertTrue(new ConfigurationReader().read(0, () -> enabled).enabled);
        assertTrue(new ConfigurationReader().read(0, () -> enabled).enabled);
    }
    @Test public void unavailableGrantDoesNotMasqueradeAsDisabledSetting() {
        ConfigurationReader reader = new ConfigurationReader();
        assertFalse(reader.read(0, ConfigurationReaderTest::denied).enabled);
        assertEquals("unavailable", reader.state());
        assertTrue(reader.read(500, () -> enabled).enabled);
        assertEquals("enabled", reader.state());
    }
    @Test public void transientFailureRetainsLastSettingThenRecovers() {
        ConfigurationReader reader = new ConfigurationReader();
        reader.read(0, () -> enabled);
        assertSame(enabled, reader.read(500, ConfigurationReaderTest::denied));
        assertEquals("cached", reader.state());
        assertFalse(reader.read(1000, () -> FilterConfig.DISABLED).enabled);
        assertEquals("disabled", reader.state());
    }
    @Test public void nullResponseRetriesAndThrottleWorksAtZeroUptime() {
        ConfigurationReader reader = new ConfigurationReader();
        reader.read(0, () -> null);
        assertFalse(reader.read(499, () -> { fail("must throttle"); return enabled; }).enabled);
        assertEquals("unavailable", reader.state());
        assertTrue(reader.read(500, () -> enabled).enabled);
    }
    @Test public void restartedProcessDoesNotInheritStaleEnabledCache() {
        ConfigurationReader previous = new ConfigurationReader();
        previous.read(0, () -> enabled);
        ConfigurationReader restarted = new ConfigurationReader();
        assertFalse(restarted.read(0, ConfigurationReaderTest::denied).enabled);
        assertEquals("unavailable", restarted.state());
    }
}
