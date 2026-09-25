package com.shale.core.dto;

import java.time.LocalDateTime;

/** Deterministic occurrence selected for a presentation slot; occurrence fields are null when absent. */
public record SelectedCaseDateOccurrenceDto(String selectionIdentity, int sortOrder,
        Long caseDateId, Integer storedCaseDateTypeId, LocalDateTime startsAt,
        LocalDateTime endsAt, Boolean allDay, Integer displayCaseDateTypeId,
        String displayName, String displayColor, String displaySystemKey, boolean supportsTime,
        boolean pendingConfirmation) { }
