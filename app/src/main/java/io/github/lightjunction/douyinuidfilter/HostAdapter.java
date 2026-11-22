package io.github.lightjunction.douyinuidfilter;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The only host-specific model adapter. Field names are verified against Douyin 41.2.0. */
final class HostAdapter implements CommentFilter.Access<Object> {
    static final String LIST_CLASS = "com.ss.android.ugc.aweme.comment.model.CommentItemList";
    static final String COMMENT_CLASS = "com.ss.android.ugc.aweme.comment.model.Comment";
    final Class<?> listType;
    final Class<?> commentType;
    private final Field items;
    private final Field total;
    private final Field user;
    private final Field uid;
    private final Field cid;
    private final Field replies;
    private final Field cursor;
    private final Field hasMore;
    private final Map<Class<?>, List<Field>> copyFields = new java.util.concurrent.ConcurrentHashMap<>();
    private final WeakIdentityMap<Object> originals = new WeakIdentityMap<>();
    private final WeakIdentityMap<FilterSession> sessions = new WeakIdentityMap<>();

    HostAdapter(ClassLoader loader) throws ReflectiveOperationException {
        listType = Class.forName(LIST_CLASS, false, loader);
        commentType = Class.forName(COMMENT_CLASS, false, loader);
        items = field(listType, "items");
        total = field(listType, "total");
        user = field(commentType, "user");
        uid = field(user.getType(), "uid");
        cid = field(commentType, "cid");
        replies = field(commentType, "replyComments");
        cursor = field(listType, "cursor");
        hasMore = field(listType, "hasMore");
    }

    static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try { Field field = current.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException ignored) { /* Walk inherited model fields. */ }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Object read(Field field, Object object) {
        if (object == null) return null;
        try { return field.get(object); }
        catch (IllegalAccessException exception) { throw new IllegalStateException(exception); }
    }

    private static String string(Object value) { return value instanceof String ? (String) value : ""; }
    @Override public String uid(Object comment) { return string(read(uid, read(user, original(comment)))); }
    @Override public String id(Object comment) { return string(read(cid, original(comment))); }
    @SuppressWarnings("unchecked")
    @Override public List<Object> replies(Object comment) {
        Object value = read(replies, original(comment));
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }
    @SuppressWarnings("unchecked")
    List<Object> items(Object response) {
        Object value = read(items, original(response));
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }
    Object original(Object response) {
        if (response == null) return null;
        Object source = originals.get(response);
        return source != null ? source : response;
    }

    void bind(Object response, FilterSession session) { sessions.put(original(response), session); }

    long visibleCount(Object response, FilterConfig config) {
        if (!config.enabled) return ((Number) read(total, original(response))).longValue();
        List<Object> source = items(response);
        Set<String> found = CommentFilter.matchingIds(source, config, this);
        FilterSession session = sessions.get(original(response));
        return session != null && sameConfig(session.config, config) ? session.record(found) : found.size();
    }

    static boolean sameConfig(FilterConfig a, FilterConfig b) {
        return a.enabled == b.enabled && a.revision == b.revision && a.uid.equals(b.uid);
    }

    boolean hasMatches(Object response, FilterConfig config) {
        return !CommentFilter.project(items(response), config, this).isEmpty();
    }

    long cursor(Object response) { return ((Number) read(cursor, original(response))).longValue(); }
    boolean hasMore(Object response) { return Boolean.TRUE.equals(read(hasMore, original(response))); }

    Object responseFrom(Object wrapper) throws ReflectiveOperationException {
        if (listType.isInstance(wrapper)) return wrapper;
        if (wrapper == null) return null;
        try {
            Object response = field(wrapper.getClass(), "data").get(wrapper);
            return listType.isInstance(response) ? response : null;
        } catch (NoSuchFieldException ignored) { return null; }
    }

    Object replaceResponse(Object wrapper, Object response) throws ReflectiveOperationException {
        if (listType.isInstance(wrapper)) return response;
        field(wrapper.getClass(), "data").set(wrapper, response);
        return wrapper;
    }

    List<Object> projectItems(Object response, FilterConfig config) throws ReflectiveOperationException {
        List<Object> source = items(response);
        if (!config.enabled) return source;
        visibleCount(response, config);
        return projectComments(source, config, new IdentityHashMap<>());
    }

    List<Object> projectReplies(Object comment, FilterConfig config) throws ReflectiveOperationException {
        if (!config.enabled) return replies(comment);
        return projectComments(replies(comment), config, new IdentityHashMap<>());
    }

    private List<Object> projectComments(List<Object> source, FilterConfig config,
            IdentityHashMap<Object, Object> copies) throws ReflectiveOperationException {
        List<Object> selected = CommentFilter.project(source, config, this);
        List<Object> result = new ArrayList<>(selected.size());
        for (Object comment : selected) {
            if (copies.containsKey(comment)) continue;
            Object copy = shallowCopy(comment);
            copies.put(comment, copy);
            List<Object> children = projectComments(replies(comment), config, copies);
            replies.set(copy, children);
            hideForeignQuote(copy, comment, config);
            // Keep server reply totals for pagination. The reply getter is filtered independently.
            // Existing UI can still request further replies that have not been downloaded.
            result.add(copy);
        }
        return result;
    }

    private void hideForeignQuote(Object copy, Object source, FilterConfig config) throws IllegalAccessException {
        try {
            String quotedUid = string(field(commentType, "replyToUserId").get(original(source)));
            if (config.matches(quotedUid)) return;
            // A retained reply must not bring a hidden author's quoted comment back into view.
            field(commentType, "replyComment").set(copy, null);
            field(commentType, "replyToReplyCommentText").set(copy, "");
            field(commentType, "firstLevelCommentText").set(copy, "");
        } catch (NoSuchFieldException ignored) { /* Older models may not render quotation fields. */ }
    }

    Object projectResponse(Object response, FilterConfig config) throws ReflectiveOperationException {
        Object source = original(response);
        if (!config.enabled) return source;
        Object copy = shallowCopy(source);
        items.set(copy, projectItems(source, config));
        total.setLong(copy, visibleCount(source, config));
        return copy;
    }

    private Object shallowCopy(Object source) throws ReflectiveOperationException {
        source = original(source);
        Constructor<?> constructor = source.getClass().getDeclaredConstructor();
        constructor.setAccessible(true);
        Object copy = constructor.newInstance();
        List<Field> fields = copyFields.get(source.getClass());
        if (fields == null) {
            fields = new ArrayList<>();
            for (Class<?> current = source.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true); fields.add(field);
                }
            }
            copyFields.put(source.getClass(), fields);
        }
        for (Field field : fields) field.set(copy, field.get(source));
        originals.put(copy, source);
        return copy;
    }
}
