package io.github.lightjunction.douyinuidfilter;

/** A UID stays a string: real IDs can exceed JavaScript's safe integer range. */
public final class FilterConfig {
    public static final FilterConfig DISABLED = new FilterConfig(false, "", 0L);
    public final boolean enabled;
    public final String uid;
    public final long revision;

    public FilterConfig(boolean enabled, String uid, long revision) {
        this.uid = uid == null ? "" : uid.trim();
        this.enabled = enabled && isValidUid(this.uid);
        this.revision = revision;
    }

    public static boolean isValidUid(String value) {
        if (value == null || value.isEmpty() || value.length() > 30 || value.charAt(0) == '0') return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    public boolean matches(String candidate) { return enabled && uid.equals(candidate); }
}
