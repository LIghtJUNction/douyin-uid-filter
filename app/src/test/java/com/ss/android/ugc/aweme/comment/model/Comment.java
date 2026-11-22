package com.ss.android.ugc.aweme.comment.model;

import com.ss.android.ugc.aweme.profile.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Models the host's CID-based equality, which must not determine clone provenance. */
public final class Comment {
    public User user;
    public String cid;
    public String awemeId;
    public String text;
    public List<Comment> replyComments = new ArrayList<>();
    public long replyCommentTotal;
    public String replyToUserId;
    public Comment replyComment;
    public String replyToReplyCommentText;
    public String firstLevelCommentText;

    public Comment() {}

    @Override public boolean equals(Object other) {
        return other instanceof Comment && Objects.equals(cid, ((Comment) other).cid);
    }

    @Override public int hashCode() { return Objects.hashCode(cid); }
}
