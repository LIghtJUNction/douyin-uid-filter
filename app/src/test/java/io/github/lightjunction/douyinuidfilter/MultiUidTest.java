package io.github.lightjunction.douyinuidfilter;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import com.ss.android.ugc.aweme.comment.model.Comment;
import com.ss.android.ugc.aweme.comment.model.CommentItemList;
import com.ss.android.ugc.aweme.profile.model.User;

public class MultiUidTest {
    @Test public void trimsDeduplicatesAndPreservesLargeStringIds() {
        FilterConfig config = new FilterConfig(true, " 123 \r\n\n9007199254740993\n123\n", 1);
        assertEquals("123\n9007199254740993", config.uid);
        assertEquals(2, config.uids.size());
        assertTrue(config.matches("123"));
        assertTrue(config.matches("9007199254740993"));
        assertFalse(config.matches("9007199254740992"));
    }
    @Test public void invalidNonemptyLineRejectsWholeEdit() {
        for (String input : Arrays.asList("123\n012", "123\nname", "123,456", "123\n12 34")) {
            try { FilterConfig.normalizeUids(input); fail("accepted invalid edit"); }
            catch (IllegalArgumentException expected) { }
            assertFalse(new FilterConfig(true, input, 1).enabled);
        }
    }
    @Test public void removalDisableAndColdReadDoNotKeepDeletedTargets() {
        ConfigurationReader reader = new ConfigurationReader();
        reader.read(0, () -> new FilterConfig(true, "123\n456", 1));
        FilterConfig removed = reader.read(500, () -> new FilterConfig(true, "456", 2));
        assertFalse(removed.matches("123"));
        assertTrue(removed.matches("456"));
        assertFalse(new FilterConfig(false, "123\n456", 3).matches("123"));
        assertFalse(new FilterConfig(true, "\n", 4).enabled);
        FilterConfig cold = new ConfigurationReader().read(0, () -> new FilterConfig(true, "123\n456", 5));
        assertTrue(cold.matches("123")); assertTrue(cold.matches("456"));
    }
    private static Comment comment(String author, String id, Comment... replies) {
        Comment item = new Comment(); item.user = new User(); item.user.uid = author;
        item.cid = id; item.awemeId = "virtual-video";
        item.replyComments.addAll(Arrays.asList(replies)); item.replyCommentTotal = replies.length;
        return item;
    }
    @Test public void mainCommentsRepliesAndCountUseUnionAndRecoverOnSwitch() throws Exception {
        HostAdapter adapter = new HostAdapter(getClass().getClassLoader());
        CommentItemList source = new CommentItemList();
        source.items.add(comment("123", "1", comment("456", "2"), comment("789", "3")));
        source.items.add(comment("456", "4")); source.items.add(comment("789", "5")); source.total = 300;
        FilterConfig both = new FilterConfig(true, "123\n456", 1);
        CommentItemList view = (CommentItemList) adapter.projectResponse(source, both);
        assertEquals(2, view.items.size()); assertEquals(1, view.items.get(0).replyComments.size());
        assertEquals("456", view.items.get(0).replyComments.get(0).user.uid);
        assertEquals(3, view.total);
        CommentItemList switched = (CommentItemList) adapter.projectResponse(view, new FilterConfig(true, "456", 2));
        assertEquals(2, switched.total);
        CommentItemList none = (CommentItemList) adapter.projectResponse(view, new FilterConfig(true, "999", 3));
        assertTrue(none.items.isEmpty()); assertEquals(0, none.total);
        assertSame(source, adapter.projectResponse(view, FilterConfig.DISABLED));
        assertEquals(3, source.items.size()); assertEquals(2, source.items.get(0).replyComments.size());
    }
}
