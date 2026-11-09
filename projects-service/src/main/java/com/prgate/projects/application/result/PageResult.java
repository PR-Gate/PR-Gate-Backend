package com.prgate.projects.application.result;

import com.prgate.projects.application.query.PageRequest;
import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, boolean hasNext) {
    public PageResult { items = List.copyOf(items); }
    public static <T> PageResult<T> from(List<T> fetched, PageRequest request) {
        return new PageResult<>(fetched.subList(0, Math.min(fetched.size(), request.size())),
                request.page(), request.size(), fetched.size() > request.size());
    }
}
