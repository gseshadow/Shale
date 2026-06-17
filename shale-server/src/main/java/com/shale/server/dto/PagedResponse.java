package com.shale.server.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        Long total) {
    public PagedResponse {
        items = List.copyOf(items);
    }
}
