package dev.tobyscamera.folia.storage;

import java.util.Objects;

public record PhotoQuery(String term, Sort sort, int page, int pageSize) {
    public enum Sort { NEWEST, OLDEST }

    public PhotoQuery {
        term = term == null ? "" : term.trim();
        sort = Objects.requireNonNull(sort, "sort");
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        if (pageSize < 1 || pageSize == Integer.MAX_VALUE) throw new IllegalArgumentException("page size must be between 1 and " + (Integer.MAX_VALUE - 1));
    }
}
