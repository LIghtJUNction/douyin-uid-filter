package com.ss.android.ugc.aweme.comment.model;

import java.util.ArrayList;
import java.util.List;

/** Public field names and primitive types mirror the supported response shape. */
public final class CommentItemList {
    public List<Comment> items = new ArrayList<>();
    public long total;
    public long cursor;
    public boolean hasMore;

    public CommentItemList() {}
}
