package com.shale.core.dto;

/** Ordered stable type identity and its current or historical presentation. */
public record CaseDatePresentationSelectionDto(
        String selectionIdentity, int sortOrder, EffectiveCaseDateTypeDto type, boolean historical) {}
