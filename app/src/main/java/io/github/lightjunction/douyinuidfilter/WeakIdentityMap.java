package io.github.lightjunction.douyinuidfilter;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Weak keys compared by object identity, independent of host model equals/hashCode. */
final class WeakIdentityMap<V> {
    private final ReferenceQueue<Object> collectedKeys = new ReferenceQueue<>();
    private final Map<IdentityKey, V> entries = new HashMap<>();

    synchronized V get(Object key) {
        Objects.requireNonNull(key, "key");
        removeCollectedKeys();
        return entries.get(new IdentityKey(key, null));
    }

    synchronized void put(Object key, V value) {
        Objects.requireNonNull(key, "key");
        removeCollectedKeys();
        entries.put(new IdentityKey(key, collectedKeys), value);
    }

    private void removeCollectedKeys() {
        IdentityKey key;
        while ((key = (IdentityKey) collectedKeys.poll()) != null) {
            entries.remove(key);
        }
    }

    private static final class IdentityKey extends WeakReference<Object> {
        private final int hash;

        IdentityKey(Object key, ReferenceQueue<Object> queue) {
            super(key, queue);
            hash = System.identityHashCode(key);
        }

        @Override public int hashCode() { return hash; }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityKey)) return false;
            Object key = get();
            // Cleared references only equal themselves, never another cleared reference.
            return key != null && key == ((IdentityKey) other).get();
        }
    }
}
