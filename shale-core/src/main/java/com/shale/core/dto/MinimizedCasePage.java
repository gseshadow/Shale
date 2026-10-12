package com.shale.core.dto;

import java.util.List;

/** No count. At page 100, hasMore still reports the probe; consumers must label the window ceiling. */
public record MinimizedCasePage(List<MinimizedCaseOverview> items, int page, int size, boolean hasMore) {
    public MinimizedCasePage {
        if (page < 0 || page > 100 || size < 1 || size > 25 || items.size() > size)
            throw new com.shale.core.service.CaseReadException(com.shale.core.service.CaseReadException.Kind.READ_UNAVAILABLE);
        items = List.copyOf(items);
    }
}
