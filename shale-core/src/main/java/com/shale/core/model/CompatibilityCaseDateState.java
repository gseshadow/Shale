package com.shale.core.model;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

/** Read model supplying all identity needed for a later safe aggregate edit. */
public record CompatibilityCaseDateState(
        MigratedCaseDateKey key, String systemKey, LocalDateTime startsAt,
        LocalDateTime endsAt, boolean allDay, Long occurrenceId, Integer caseDateTypeId,
        byte[] occurrenceRowVer, CompatibilityCaseDateMutation.ExpectedAbsent expectedAbsent) {
    public CompatibilityCaseDateState {
        Objects.requireNonNull(key, "key");
        if (!key.systemKey().equals(systemKey)) throw new IllegalArgumentException("SystemKey is not canonical for key.");
        boolean present = occurrenceId != null;
        if (present != (occurrenceRowVer != null) || present != (startsAt != null) || present != (caseDateTypeId != null) || present == (expectedAbsent != null))
            throw new IllegalArgumentException("State must be exactly present or explicitly absent.");
        if (present && occurrenceId <= 0) throw new IllegalArgumentException("occurrenceId must be positive.");
        if (occurrenceRowVer != null) occurrenceRowVer = Arrays.copyOf(occurrenceRowVer, occurrenceRowVer.length);
    }
    @Override public byte[] occurrenceRowVer() { return occurrenceRowVer == null ? null : occurrenceRowVer.clone(); }
}
