package io.github.lightjunction.douyinuidfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public final class WeakIdentityMapTest {
    private static final class Comment {
        final String cid;

        Comment(String cid) { this.cid = cid; }

        @Override public boolean equals(Object other) {
            return other instanceof Comment && cid.equals(((Comment) other).cid);
        }

        @Override public int hashCode() { return cid.hashCode(); }
    }

    @Test public void equalOriginalAndCloneKeepSeparateAssociations() {
        Comment original = new Comment("101");
        Comment clone = new Comment("101");
        WeakIdentityMap<String> map = new WeakIdentityMap<>();

        assertEquals(original, clone);
        map.put(original, "original session");
        map.put(clone, "clone session");

        assertEquals("original session", map.get(original));
        assertEquals("clone session", map.get(clone));
    }

    @Test public void equalUnregisteredObjectDoesNotFindAnotherObjectsValue() {
        Comment original = new Comment("101");
        WeakIdentityMap<Comment> originals = new WeakIdentityMap<>();
        Comment clone = new Comment("101");
        originals.put(clone, original);

        assertSame(original, originals.get(clone));
        assertNull(originals.get(original));
        assertNull(originals.get(new Comment("101")));
    }

    @Test public void replacingOneIdentityDoesNotReplaceItsEqualPeer() {
        Comment first = new Comment("101");
        Comment second = new Comment("101");
        WeakIdentityMap<String> map = new WeakIdentityMap<>();
        map.put(first, "old");
        map.put(second, "other");

        map.put(first, "updated");

        assertEquals("updated", map.get(first));
        assertEquals("other", map.get(second));
    }

    @Test public void doesNotInvokeHostEqualsOrHashCode() {
        Object host = new Object() {
            @Override public boolean equals(Object other) {
                throw new AssertionError("Host equals must not be called");
            }
            @Override public int hashCode() {
                throw new AssertionError("Host hashCode must not be called");
            }
        };
        WeakIdentityMap<String> map = new WeakIdentityMap<>();

        map.put(host, "value");

        assertEquals("value", map.get(host));
        assertNull(map.get(new Object()));
    }

    @Test public void mutableHostHashCodeDoesNotBreakLookup() {
        final int[] hash = {1};
        Object host = new Object() {
            @Override public int hashCode() { return hash[0]; }
        };
        WeakIdentityMap<String> map = new WeakIdentityMap<>();
        map.put(host, "value");

        hash[0] = 999;

        assertEquals("value", map.get(host));
    }

    @Test(expected = NullPointerException.class) public void rejectsNullGetKey() {
        new WeakIdentityMap<>().get(null);
    }

    @Test(expected = NullPointerException.class) public void rejectsNullPutKey() {
        new WeakIdentityMap<>().put(null, "value");
    }
}
