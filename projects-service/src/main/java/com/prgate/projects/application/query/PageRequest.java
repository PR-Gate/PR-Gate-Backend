package com.prgate.projects.application.query;

public record PageRequest(int page, int size) {
    public PageRequest {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE - 101) {
            throw new IllegalArgumentException("Page must be nonnegative and size between 1 and 100");
        }
    }
    public int offset() { return page * size; }
}
