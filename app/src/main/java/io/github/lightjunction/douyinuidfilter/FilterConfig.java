package io.github.lightjunction.douyinuidfilter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** UIDs remain strings; a setting matches any author in the selected set. */
public final class FilterConfig {
    public static final FilterConfig DISABLED = new FilterConfig(false, "", 0L);
    public final boolean enabled;
    /** Canonical newline-separated value; retained name keeps session comparisons compatible. */
    public final String uid;
    public final Set<String> uids;
    public final long revision;

    public FilterConfig(boolean enabled, String input, long revision) {
        Set<String> parsed;
        String normalized;
        try {
            normalized = normalizeUids(input);
            parsed = new LinkedHashSet<>();
            if (!normalized.isEmpty()) Collections.addAll(parsed, normalized.split("\\n"));
        } catch (IllegalArgumentException invalid) {
            normalized = input == null ? "" : input.trim();
            parsed = Collections.emptySet();
        }
        this.uid = normalized;
        this.uids = Collections.unmodifiableSet(parsed);
        this.enabled = enabled && !parsed.isEmpty();
        this.revision = revision;
    }

    /** Empty lines are ignored; invalid nonempty lines reject the entire edit. */
    public static String normalizeUids(String input) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        if (input != null) for (String line : input.split("\\r?\\n", -1)) {
            String value = line.trim();
            if (value.isEmpty()) continue;
            if (!isValidUid(value)) throw new IllegalArgumentException("Invalid UID line");
            selected.add(value);
        }
        return String.join("\n", selected);
    }

    public static boolean isValidUid(String value) {
        if (value == null || value.isEmpty() || value.length() > 30 || value.charAt(0) == '0') return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    public boolean matches(String candidate) { return enabled && uids.contains(candidate); }
}
