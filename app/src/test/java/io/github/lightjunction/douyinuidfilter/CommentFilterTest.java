package io.github.lightjunction.douyinuidfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public final class CommentFilterTest {
    private static final String TARGET = "9007199254740993";
    private static final String OTHER = "9007199254740992";
    private static final FilterConfig CONFIG = new FilterConfig(true, TARGET, 1L);

    private static final class Comment {
        final String uid;
        final String id;
        final List<Comment> replies = new ArrayList<>();

        Comment(String uid, String id) { this.uid = uid; this.id = id; }
    }

    private static final CommentFilter.Access<Comment> ACCESS = new CommentFilter.Access<Comment>() {
        @Override public String uid(Comment comment) { return comment.uid; }
        @Override public String id(Comment comment) { return comment.id; }
        @Override public List<Comment> replies(Comment comment) { return comment.replies; }
    };

    @Test public void keepsOnlyExactAuthorMatchesInSourceOrder() {
        Comment first = new Comment(TARGET, "101");
        Comment nearUid = new Comment(OTHER, "102");
        Comment second = new Comment(TARGET, "103");
        Comment suffixUid = new Comment(TARGET + "0", "104");
        Comment unknown = new Comment(null, "105");

        assertEquals(Arrays.asList(first, second), CommentFilter.project(
                Arrays.asList(first, nearUid, second, suffixUid, unknown), CONFIG, ACCESS));
    }

    @Test public void doesNotMutateUnmodifiableSourceOrOriginalModels() {
        Comment target = new Comment(TARGET, "101");
        Comment other = new Comment(OTHER, "102");
        Comment hiddenReply = new Comment(OTHER, "103");
        target.replies.add(hiddenReply);
        List<Comment> source = Collections.unmodifiableList(Arrays.asList(other, target));

        List<Comment> result = CommentFilter.project(source, CONFIG, ACCESS);

        assertNotSame(source, result);
        assertEquals(Arrays.asList(other, target), source);
        assertEquals(Collections.singletonList(target), result);
        assertSame(target, result.get(0));
        assertEquals(Collections.singletonList(hiddenReply), target.replies);
        assertEquals(TARGET, target.uid);
        assertEquals("101", target.id);
    }

    @Test public void preservesNullSource() {
        assertNull(CommentFilter.project(null, CONFIG, ACCESS));
        assertTrue(CommentFilter.matchingIds(null, CONFIG, ACCESS).isEmpty());
    }

    @Test public void emptyAndNullEntriesProduceEmptyResult() {
        assertTrue(CommentFilter.project(Collections.<Comment>emptyList(), CONFIG, ACCESS).isEmpty());
        assertTrue(CommentFilter.project(Arrays.asList(null, null), CONFIG, ACCESS).isEmpty());
        assertTrue(CommentFilter.matchingIds(Arrays.asList(null, null), CONFIG, ACCESS).isEmpty());
    }

    @Test public void absentTargetProducesZeroMatchingIds() {
        Comment first = new Comment(OTHER, "101");
        first.replies.add(new Comment(OTHER, "102"));
        List<Comment> source = Collections.singletonList(first);

        assertTrue(CommentFilter.project(source, CONFIG, ACCESS).isEmpty());
        assertEquals(0, CommentFilter.matchingIds(source, CONFIG, ACCESS).size());
    }

    @Test public void disabledOrInvalidConfigurationLeavesSourceUntouched() {
        List<Comment> source = Collections.singletonList(new Comment(OTHER, "101"));
        CommentFilter.Access<Comment> mustNotRead = new CommentFilter.Access<Comment>() {
            @Override public String uid(Comment comment) { throw new AssertionError("Unexpected read"); }
            @Override public String id(Comment comment) { throw new AssertionError("Unexpected read"); }
            @Override public List<Comment> replies(Comment comment) { throw new AssertionError("Unexpected read"); }
        };

        assertSame(source, CommentFilter.project(source, FilterConfig.DISABLED, mustNotRead));
        assertSame(source, CommentFilter.project(source, new FilterConfig(true, "bad", 2L), mustNotRead));
        assertTrue(CommentFilter.matchingIds(source, FilterConfig.DISABLED, mustNotRead).isEmpty());
    }

    @Test public void promotesTargetReplyBelowNonTargetParent() {
        Comment parent = new Comment(OTHER, "101");
        Comment targetReply = new Comment(TARGET, "102");
        Comment otherReply = new Comment(OTHER, "103");
        parent.replies.addAll(Arrays.asList(otherReply, targetReply));

        assertEquals(Collections.singletonList(targetReply),
                CommentFilter.project(Collections.singletonList(parent), CONFIG, ACCESS));
        assertEquals(Arrays.asList(otherReply, targetReply), parent.replies);
    }

    @Test public void promotesTargetBelowSeveralFilteredAncestors() {
        Comment parent = new Comment(OTHER, "101");
        Comment intermediate = new Comment(OTHER, "102");
        Comment target = new Comment(TARGET, "103");
        parent.replies.add(intermediate);
        intermediate.replies.add(target);

        assertEquals(Collections.singletonList(target),
                CommentFilter.project(Collections.singletonList(parent), CONFIG, ACCESS));
        assertEquals(Collections.singleton("103"),
                CommentFilter.matchingIds(Collections.singletonList(parent), CONFIG, ACCESS));
    }

    @Test public void separateReplyProjectionHidesNonTargetRepliesOfMatchingParent() {
        Comment parent = new Comment(TARGET, "101");
        Comment otherReply = new Comment(OTHER, "102");
        Comment targetReply = new Comment(TARGET, "103");
        parent.replies.addAll(Arrays.asList(otherReply, targetReply));

        // The host must apply project both to the main list and to its reply getter.
        assertEquals(Collections.singletonList(parent),
                CommentFilter.project(Collections.singletonList(parent), CONFIG, ACCESS));
        assertEquals(Collections.singletonList(targetReply),
                CommentFilter.project(parent.replies, CONFIG, ACCESS));
        assertEquals(Arrays.asList(otherReply, targetReply), parent.replies);
        assertEquals(Arrays.asList("101", "103"), new ArrayList<>(
                CommentFilter.matchingIds(Collections.singletonList(parent), CONFIG, ACCESS)));
    }

    @Test public void deduplicatesRepeatedObjectsAndCidsPreservingFirstOccurrence() {
        Comment first = new Comment(TARGET, "101");
        Comment sameCid = new Comment(TARGET, "101");
        Comment next = new Comment(TARGET, "102");
        List<Comment> source = Arrays.asList(first, first, sameCid, next, next);

        assertEquals(Arrays.asList(first, next), CommentFilter.project(source, CONFIG, ACCESS));
        assertEquals(Arrays.asList("101", "102"),
                new ArrayList<>(CommentFilter.matchingIds(source, CONFIG, ACCESS)));
        assertEquals(5, source.size());
    }

    @Test public void countsAndDisplaysDistinctMissingCidCommentsOncePerObject() {
        Comment nullId = new Comment(TARGET, null);
        Comment emptyId = new Comment(TARGET, "");
        List<Comment> source = Arrays.asList(nullId, emptyId, nullId, emptyId);

        assertEquals(Arrays.asList(nullId, emptyId), CommentFilter.project(source, CONFIG, ACCESS));
        Set<String> ids = CommentFilter.matchingIds(source, CONFIG, ACCESS);
        assertEquals("Visible comments without cid must not produce a zero count", 2, ids.size());
        assertFalse(ids.contains(null));
        assertFalse(ids.contains(""));
    }

    @Test(timeout = 1000L) public void terminatesForNonTargetCycleAndKeepsReachableTarget() {
        Comment first = new Comment(OTHER, "101");
        Comment second = new Comment(OTHER, "102");
        Comment target = new Comment(TARGET, "103");
        first.replies.add(second);
        second.replies.addAll(Arrays.asList(first, target));

        assertEquals(Collections.singletonList(target),
                CommentFilter.project(Collections.singletonList(first), CONFIG, ACCESS));
        assertEquals(Collections.singleton("103"),
                CommentFilter.matchingIds(Collections.singletonList(first), CONFIG, ACCESS));
    }

    @Test(timeout = 1000L) public void selfReferencingTargetDoesNotDuplicateCount() {
        Comment target = new Comment(TARGET, "101");
        target.replies.add(target);

        assertEquals(Collections.singletonList(target),
                CommentFilter.project(Collections.singletonList(target), CONFIG, ACCESS));
        assertEquals(Collections.singleton("101"),
                CommentFilter.matchingIds(Collections.singletonList(target), CONFIG, ACCESS));
    }

    @Test public void sharedReplyAcrossFilteredParentsIsEmittedOnce() {
        Comment first = new Comment(OTHER, "101");
        Comment second = new Comment(OTHER, "102");
        Comment shared = new Comment(TARGET, "103");
        first.replies.add(shared);
        second.replies.add(shared);
        List<Comment> source = Arrays.asList(first, second, shared);

        assertEquals(Collections.singletonList(shared), CommentFilter.project(source, CONFIG, ACCESS));
        assertEquals(Collections.singleton("103"), CommentFilter.matchingIds(source, CONFIG, ACCESS));
    }
}
