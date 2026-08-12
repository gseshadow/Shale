package com.shale.core.dto;

import java.time.LocalDateTime;
import java.util.Arrays;

/** Authoritative mapped Case Date state exposed by existing-case APIs. */
public record MappedCaseDateSnapshotDto(String key, String systemKey, Long occurrenceId,
        Integer caseDateTypeId, LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay,
        byte[] occurrenceRowVer, boolean absent, byte[] absenceCaseRowVer) {
    public MappedCaseDateSnapshotDto {
        occurrenceRowVer = copy(occurrenceRowVer);
        absenceCaseRowVer = copy(absenceCaseRowVer);
        if (absent == (occurrenceId != null)) throw new IllegalArgumentException("Mapped date must be present or absent.");
        if (absent && (caseDateTypeId != null || startsAt != null || occurrenceRowVer != null || absenceCaseRowVer == null))
            throw new IllegalArgumentException("Absent mapped date requires only its Case-version witness.");
        if (!absent && (caseDateTypeId == null || startsAt == null || occurrenceRowVer == null || absenceCaseRowVer != null))
            throw new IllegalArgumentException("Present mapped date requires occurrence identity and version.");
    }
    private static byte[] copy(byte[] value) { return value == null ? null : Arrays.copyOf(value, value.length); }
    @Override public byte[] occurrenceRowVer() { return copy(occurrenceRowVer); }
    @Override public byte[] absenceCaseRowVer() { return copy(absenceCaseRowVer); }
}
