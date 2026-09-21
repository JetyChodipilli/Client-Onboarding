package com.brainserve.clientonboarding.common.api;

import java.util.List;
import java.util.Map;

public record PageSlice<T>(List<T> items, int page, int size, long totalElements) {
    public PageSlice {
        items = List.copyOf(items);
    }

    public long totalPages() {
        return totalElements == 0 ? 0 : (totalElements + size - 1) / size;
    }

    public Map<String, Object> meta() {
        return Map.of("page", page, "size", size, "totalElements", totalElements,
                "totalPages", totalPages());
    }
}
