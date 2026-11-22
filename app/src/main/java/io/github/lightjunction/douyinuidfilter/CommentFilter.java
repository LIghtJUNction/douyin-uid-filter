package io.github.lightjunction.douyinuidfilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Projects loaded comments without changing source objects. The host adapter must also
 * project each reply getter; matching parents retain their original child model objects.
 * Accessors must read raw data, without re-entering a hooked reply getter.
 */
public final class CommentFilter {
    private CommentFilter() {}

    public interface Access<T> {
        String uid(T comment);
        String id(T comment);
        List<T> replies(T comment);
    }

    public static <T> List<T> project(List<T> source, FilterConfig config, Access<T> access) {
        if (!config.enabled || source == null) return source;
        List<T> result = new ArrayList<>();
        Set<T> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> emitted = new LinkedHashSet<>();
        projectInto(source, config, access, result, visited, emitted);
        return result;
    }

    private static <T> void projectInto(List<T> source, FilterConfig config, Access<T> access,
            List<T> result, Set<T> visited, Set<String> emitted) {
        if (source == null) return;
        for (T comment : source) {
            if (comment == null || !visited.add(comment)) continue;
            if (config.matches(access.uid(comment))) {
                String cid = access.id(comment);
                if (cid == null || cid.isEmpty() || emitted.add(cid)) result.add(comment);
            } else {
                // Matching replies remain accessible even when their parent is filtered out.
                projectInto(access.replies(comment), config, access, result, visited, emitted);
            }
        }
    }

    public static <T> Set<String> matchingIds(List<T> source, FilterConfig config, Access<T> access) {
        Set<String> result = new LinkedHashSet<>();
        Set<T> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(source, config, access, result, visited);
        return result;
    }

    private static <T> void collect(List<T> source, FilterConfig config, Access<T> access,
            Set<String> result, Set<T> visited) {
        if (source == null || !config.enabled) return;
        for (T comment : source) {
            if (comment == null || !visited.add(comment)) continue;
            if (config.matches(access.uid(comment))) {
                String id = access.id(comment);
                result.add(id == null || id.isEmpty()
                    ? "identity:" + System.identityHashCode(comment) : id);
            }
            collect(access.replies(comment), config, access, result, visited);
        }
    }
}
