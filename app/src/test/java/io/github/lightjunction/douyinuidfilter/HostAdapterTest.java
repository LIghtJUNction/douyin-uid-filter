package io.github.lightjunction.douyinuidfilter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ss.android.ugc.aweme.comment.model.Comment;
import com.ss.android.ugc.aweme.comment.model.CommentItemList;
import com.ss.android.ugc.aweme.profile.model.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;

public final class HostAdapterTest {
    private static final String TARGET = "9007199254740993";
    private static final String OTHER = "9007199254740992";
    private static final FilterConfig CONFIG = new FilterConfig(true, TARGET, 1L);
    private HostAdapter adapter;

    @Before public void createAdapter() throws ReflectiveOperationException {
        adapter = new HostAdapter(HostAdapterTest.class.getClassLoader());
    }

    private static Comment comment(String uid, String cid, Comment... replies) {
        Comment comment = new Comment();
        comment.user = new User();
        comment.user.uid = uid;
        comment.cid = cid;
        comment.awemeId = "video-a";
        comment.text = "Comment " + cid;
        comment.replyComments.addAll(Arrays.asList(replies));
        comment.replyCommentTotal = replies.length;
        return comment;
    }

    private static CommentItemList page(long cursor, boolean hasMore, Comment... comments) {
        CommentItemList response = new CommentItemList();
        response.items.addAll(Arrays.asList(comments));
        response.total = 500L;
        response.cursor = cursor;
        response.hasMore = hasMore;
        return response;
    }

    private static List<String> ids(List<Comment> comments) {
        List<String> result = new ArrayList<>();
        for (Comment comment : comments) result.add(comment.cid);
        return result;
    }

    private static void assertOnlyTargetComments(List<Comment> comments) {
        for (Comment comment : comments) {
            assertEquals(TARGET, comment.user.uid);
            assertOnlyTargetComments(comment.replyComments);
        }
    }

    @Test public void decoderIgnoresNullOtherModelsAndEnvelopesBeforeReadingConfiguration()
            throws ReflectiveOperationException {
        DecodedPageProjection decoded = new DecodedPageProjection(adapter,
                () -> { throw new AssertionError("Unrelated result requested configuration"); },
                (page, config) -> { throw new AssertionError("Unrelated result was projected"); });
        Object envelope = new Object() { public final CommentItemList data = new CommentItemList(); };
        Comment otherModel = comment(TARGET, "101");

        assertNull(decoded.apply(null));
        assertSame(envelope, decoded.apply(envelope));
        assertSame(otherModel, decoded.apply(otherModel));
        assertSame(otherModel.replyComments, decoded.apply(otherModel.replyComments));
    }

    @Test public void nestedDecodersReuseProjectionForAnEquivalentConfiguration()
            throws ReflectiveOperationException {
        AtomicInteger notifications = new AtomicInteger();
        CommentItemList source = page(20L, true, comment(TARGET, "101"), comment(OTHER, "102"));
        DecodedPageProjection decoded = new DecodedPageProjection(adapter,
                () -> new FilterConfig(true, TARGET, 1L), (input, selected) -> {
                    assertSame(source, input);
                    notifications.incrementAndGet();
                });

        CommentItemList selected = (CommentItemList) decoded.apply(source);
        assertNotSame(source, selected);
        assertEquals(Collections.singletonList("101"), ids(selected.items));
        assertSame(selected, decoded.apply(selected));
        assertEquals(1, notifications.get());
    }

    @Test public void decoderReprojectsChangedSelectionFromRawPageAndRestoresOriginalIdentity()
            throws ReflectiveOperationException {
        AtomicReference<FilterConfig> config = new AtomicReference<>(FilterConfig.DISABLED);
        AtomicInteger projections = new AtomicInteger();
        DecodedPageProjection decoded = new DecodedPageProjection(adapter, config::get,
                (input, selected) -> projections.incrementAndGet());
        CommentItemList source = page(0L, false, comment(TARGET, "101"), comment(OTHER, "102"));

        assertSame(source, decoded.apply(source));
        config.set(CONFIG);
        CommentItemList first = (CommentItemList) decoded.apply(source);
        config.set(new FilterConfig(true, OTHER, CONFIG.revision));
        CommentItemList second = (CommentItemList) decoded.apply(first);
        assertEquals(Collections.singletonList("102"), ids(second.items));
        config.set(new FilterConfig(true, OTHER, CONFIG.revision + 1));
        CommentItemList revised = (CommentItemList) decoded.apply(second);
        assertNotSame(second, revised);
        assertSame(revised, decoded.apply(revised));
        config.set(FilterConfig.DISABLED);
        assertSame(source, decoded.apply(revised));
        assertSame(source, decoded.apply(first));
        assertEquals(Arrays.asList("101", "102"), ids(source.items));
        assertEquals(500L, source.total);
        assertEquals(3, projections.get());
    }

    @Test public void emptyTailPagePreservesMatchesFromEarlierPageInSameSession()
            throws ReflectiveOperationException {
        FilterSession session = new FilterSession(CONFIG);
        CommentItemList first = page(20L, true,
                comment(TARGET, "101"), comment(OTHER, "102"), comment(TARGET, "103"));
        adapter.bind(first, session);
        CommentItemList firstView = (CommentItemList) adapter.projectResponse(first, CONFIG);
        assertEquals(2L, firstView.total);

        CommentItemList tail = page(20L, false);
        adapter.bind(tail, session);
        CommentItemList tailView = (CommentItemList) adapter.projectResponse(tail, CONFIG);

        assertTrue(tailView.items.isEmpty());
        assertEquals(2L, tailView.total);
        assertEquals(2L, adapter.visibleCount(tailView, CONFIG));
        assertEquals(2L, session.getCount());
        assertEquals(500L, first.total);
        assertEquals(500L, tail.total);
        assertFalse(tailView.hasMore);
    }

    @Test public void unboundEmptyResponseDoesNotInheritAnotherSessionsCount() {
        FilterSession session = new FilterSession(CONFIG);
        CommentItemList first = page(20L, true, comment(TARGET, "101"));
        adapter.bind(first, session);
        assertEquals(1L, adapter.visibleCount(first, CONFIG));

        assertEquals(0L, adapter.visibleCount(page(0L, false), CONFIG));
        assertEquals(1L, session.getCount());
    }

    @Test public void sameCidCloneRestoresOriginalRepliesWhenFilteringIsDisabled()
            throws ReflectiveOperationException {
        Comment targetReply = comment(TARGET, "102");
        Comment otherReply = comment(OTHER, "103");
        Comment parent = comment(TARGET, "101", targetReply, otherReply);
        CommentItemList source = page(20L, true, parent);
        CommentItemList view = (CommentItemList) adapter.projectResponse(source, CONFIG);
        Comment parentCopy = view.items.get(0);

        assertNotSame(parent, parentCopy);
        assertEquals(parent, parentCopy);
        assertSame(parent, adapter.original(parentCopy));
        assertSame("CID equality must not make the original resolve to another object",
                parent, adapter.original(parent));
        assertEquals(Collections.singletonList("102"), ids(parentCopy.replyComments));

        List<Object> restoredReplies = adapter.projectReplies(parentCopy, FilterConfig.DISABLED);

        assertSame(parent.replyComments, restoredReplies);
        assertSame(targetReply, restoredReplies.get(0));
        assertSame(otherReply, restoredReplies.get(1));
        assertSame(source.items, adapter.projectItems(view, FilterConfig.DISABLED));
        assertSame(source, adapter.projectResponse(view, FilterConfig.DISABLED));
        assertEquals(500L, adapter.visibleCount(view, FilterConfig.DISABLED));
    }

    @Test public void changingUidFindsRepliesRemovedFromThePreviousProjection()
            throws ReflectiveOperationException {
        Comment targetReply = comment(TARGET, "102");
        Comment otherReply = comment(OTHER, "103");
        Comment parent = comment(TARGET, "101", targetReply, otherReply);
        CommentItemList source = page(20L, true, parent);
        CommentItemList oldView = (CommentItemList) adapter.projectResponse(source, CONFIG);
        Comment oldParentCopy = oldView.items.get(0);
        FilterConfig changed = new FilterConfig(true, OTHER, 2L);

        List<Object> repliesForChangedUid = adapter.projectReplies(oldParentCopy, changed);
        CommentItemList newView = (CommentItemList) adapter.projectResponse(oldView, changed);

        assertEquals(1, repliesForChangedUid.size());
        assertSame(otherReply, adapter.original(repliesForChangedUid.get(0)));
        assertEquals(Collections.singletonList("103"), ids(newView.items));
        assertSame(otherReply, adapter.original(newView.items.get(0)));
        assertEquals(1L, newView.total);
        assertEquals(Collections.singletonList("102"), ids(oldParentCopy.replyComments));
        assertEquals(Arrays.asList("102", "103"), ids(parent.replyComments));
    }

    @Test public void projectionsPreserveSourceListsObjectsAndPagination()
            throws ReflectiveOperationException {
        Comment targetReply = comment(TARGET, "102");
        Comment otherReply = comment(OTHER, "103");
        Comment parent = comment(TARGET, "101");
        List<Comment> originalReplies = Collections.unmodifiableList(Arrays.asList(targetReply, otherReply));
        parent.replyComments = originalReplies;
        parent.replyCommentTotal = 99L;
        Comment otherParent = comment(OTHER, "104");
        CommentItemList source = page(1234567890123L, true);
        List<Comment> originalItems = Collections.unmodifiableList(Arrays.asList(parent, otherParent));
        source.items = originalItems;

        CommentItemList view = (CommentItemList) adapter.projectResponse(source, CONFIG);
        Comment parentCopy = view.items.get(0);

        assertNotSame(source, view);
        assertSame(source, adapter.original(view));
        assertNotSame(originalItems, view.items);
        assertNotSame(originalReplies, parentCopy.replyComments);
        assertEquals(Collections.singletonList("101"), ids(view.items));
        assertEquals(Collections.singletonList("102"), ids(parentCopy.replyComments));
        assertEquals(2L, view.total);
        assertEquals(99L, parentCopy.replyCommentTotal);
        assertEquals(source.cursor, view.cursor);
        assertTrue(view.hasMore);
        assertEquals(source.cursor, adapter.cursor(view));
        assertTrue(adapter.hasMore(view));

        parentCopy.text = "Changed only in projected model";
        parentCopy.replyComments.clear();
        view.items.clear();

        assertSame(originalItems, source.items);
        assertSame(originalReplies, parent.replyComments);
        assertEquals(Arrays.asList("101", "104"), ids(source.items));
        assertEquals(Arrays.asList("102", "103"), ids(parent.replyComments));
        assertEquals("Comment 101", parent.text);
        assertEquals(500L, source.total);
        assertEquals(1234567890123L, source.cursor);
        assertTrue(source.hasMore);
    }

    @Test public void mixedParentsAndRepliesExposeOnlyTargetThroughoutProjectedTree()
            throws ReflectiveOperationException {
        Comment promoted = comment(TARGET, "103", comment(OTHER, "104"));
        Comment hiddenParent = comment(OTHER, "101", comment(OTHER, "102", promoted));
        Comment promotedWithinReplies = comment(TARGET, "107");
        Comment targetChild = comment(TARGET, "108");
        Comment targetParent = comment(TARGET, "105",
                comment(OTHER, "106", promotedWithinReplies), targetChild);
        CommentItemList source = page(20L, true,
                hiddenParent, targetParent, comment(OTHER, "109"));

        CommentItemList view = (CommentItemList) adapter.projectResponse(source, CONFIG);

        assertEquals(Arrays.asList("103", "105"), ids(view.items));
        assertTrue(view.items.get(0).replyComments.isEmpty());
        assertEquals(Arrays.asList("107", "108"), ids(view.items.get(1).replyComments));
        assertOnlyTargetComments(view.items);
        assertEquals(4L, view.total);
        assertEquals(3, source.items.size());
        assertEquals(Collections.singletonList("104"), ids(promoted.replyComments));
        assertEquals(Arrays.asList("106", "108"), ids(targetParent.replyComments));
    }

    @Test public void latePagesAndEmptyTailsRemainIsolatedAcrossSessions() {
        FilterSession firstSession = new FilterSession(CONFIG);
        FilterSession secondSession = new FilterSession(CONFIG);
        CommentItemList firstPage = page(20L, true, comment(TARGET, "101"), comment(TARGET, "102"));
        CommentItemList secondPage = page(20L, true, comment(TARGET, "201"));
        secondPage.items.get(0).awemeId = "video-b";
        adapter.bind(firstPage, firstSession);
        adapter.bind(secondPage, secondSession);
        assertEquals(2L, adapter.visibleCount(firstPage, CONFIG));
        assertEquals(1L, adapter.visibleCount(secondPage, CONFIG));

        CommentItemList lateFirstPage = page(40L, false, comment(TARGET, "103"));
        adapter.bind(lateFirstPage, firstSession);
        CommentItemList secondTail = page(20L, false);
        adapter.bind(secondTail, secondSession);

        assertEquals(3L, adapter.visibleCount(lateFirstPage, CONFIG));
        assertEquals(1L, adapter.visibleCount(secondTail, CONFIG));
        assertEquals(3L, firstSession.getCount());
        assertEquals(1L, secondSession.getCount());
    }

    @Test public void differentConfigurationDoesNotAddMatchesToOldSession() {
        FilterSession session = new FilterSession(CONFIG);
        CommentItemList source = page(20L, true,
                comment(TARGET, "101"), comment(OTHER, "102"), comment(OTHER, "103"));
        adapter.bind(source, session);
        assertEquals(1L, adapter.visibleCount(source, CONFIG));
        FilterConfig changed = new FilterConfig(true, OTHER, 2L);

        assertEquals(2L, adapter.visibleCount(source, changed));
        assertEquals(1L, session.getCount());

        FilterSession newSession = new FilterSession(changed);
        adapter.bind(source, newSession);
        assertEquals(2L, adapter.visibleCount(source, changed));
        assertEquals(2L, newSession.getCount());
        assertEquals(1L, session.getCount());
    }

    @Test public void bindingProjectedResponseUsesItsOriginalIdentity()
            throws ReflectiveOperationException {
        CommentItemList source = page(20L, true, comment(TARGET, "101"));
        CommentItemList view = (CommentItemList) adapter.projectResponse(source, CONFIG);
        FilterSession session = new FilterSession(CONFIG);

        adapter.bind(view, session);
        assertEquals(1L, adapter.visibleCount(source, CONFIG));
        assertEquals(1L, adapter.visibleCount(view, CONFIG));

        CommentItemList tail = page(20L, false);
        adapter.bind(tail, session);
        assertEquals(1L, adapter.visibleCount(tail, CONFIG));
        assertEquals(1L, session.getCount());
    }

    @Test public void foreignUserQuoteIsRemovedOnlyFromProjectedComment()
            throws ReflectiveOperationException {
        Comment quoted = comment(OTHER, "101");
        Comment retained = comment(TARGET, "102");
        retained.replyToUserId = OTHER;
        retained.replyComment = quoted;
        retained.replyToReplyCommentText = "Quoted text from another user";
        retained.firstLevelCommentText = "First-level text from another user";
        CommentItemList source = page(20L, true, retained);

        CommentItemList view = (CommentItemList) adapter.projectResponse(source, CONFIG);
        Comment copy = view.items.get(0);

        assertNull(copy.replyComment);
        assertEquals("", copy.replyToReplyCommentText);
        assertEquals("", copy.firstLevelCommentText);
        assertEquals(retained.text, copy.text);
        assertSame(retained, adapter.original(copy));
        assertSame(quoted, retained.replyComment);
        assertEquals("Quoted text from another user", retained.replyToReplyCommentText);
        assertEquals("First-level text from another user", retained.firstLevelCommentText);
        assertEquals("Comment 101", quoted.text);
    }

    @Test public void quoteOfTargetUsersOwnCommentIsPreserved()
            throws ReflectiveOperationException {
        Comment quoted = comment(TARGET, "101");
        Comment retained = comment(TARGET, "102");
        retained.replyToUserId = TARGET;
        retained.replyComment = quoted;
        retained.replyToReplyCommentText = "Earlier text from the target user";
        retained.firstLevelCommentText = "First-level text from the target user";
        CommentItemList source = page(20L, true, retained);

        CommentItemList view = (CommentItemList) adapter.projectResponse(source, CONFIG);
        Comment copy = view.items.get(0);

        assertNotSame(retained, copy);
        assertSame(quoted, copy.replyComment);
        assertEquals("Earlier text from the target user", copy.replyToReplyCommentText);
        assertEquals("First-level text from the target user", copy.firstLevelCommentText);
        assertSame(quoted, retained.replyComment);
        assertEquals(retained.replyToReplyCommentText, copy.replyToReplyCommentText);
        assertEquals(retained.firstLevelCommentText, copy.firstLevelCommentText);
    }
}
