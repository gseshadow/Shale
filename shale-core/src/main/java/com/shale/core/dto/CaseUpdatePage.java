package com.shale.core.dto;

import java.util.List;

public record CaseUpdatePage(List<CaseUpdateDto> items, CaseUpdateQuery.Cursor next) {
    public CaseUpdatePage { items = List.copyOf(items); }
}
