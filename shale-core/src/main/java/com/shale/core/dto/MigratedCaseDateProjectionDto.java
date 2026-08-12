package com.shale.core.dto;

import com.shale.core.model.MigratedCaseDateKey;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Minimal, read-only projection of the nine authoritative migrated Case Date meanings.
 * This type deliberately carries neither edit commands nor optimistic-concurrency tokens.
 */
public record MigratedCaseDateProjectionDto(long caseId, Map<MigratedCaseDateKey, Slot> dates) {
    public MigratedCaseDateProjectionDto {
        if (caseId <= 0) throw new IllegalArgumentException("caseId must be positive.");
        Objects.requireNonNull(dates, "dates");
        EnumMap<MigratedCaseDateKey, Slot> copy = new EnumMap<>(MigratedCaseDateKey.class);
        copy.putAll(dates);
        if (copy.size() != MigratedCaseDateKey.values().length
                || !copy.keySet().containsAll(List.of(MigratedCaseDateKey.values()))) {
            throw new IllegalArgumentException("All nine migrated Case Date slots are required.");
        }
        copy.forEach((key, slot) -> {
            if (slot == null || slot.key() != key) throw new IllegalArgumentException("Slot key mismatch.");
        });
        dates = Map.copyOf(copy);
    }

    public Slot date(MigratedCaseDateKey key) { return dates.get(Objects.requireNonNull(key, "key")); }

    /** A value is absent only when {@code present} is false; null end times remain valid present values. */
    public record Slot(MigratedCaseDateKey key, boolean present, LocalDateTime startsAt,
                       LocalDateTime endsAt, boolean allDay) {
        public Slot {
            Objects.requireNonNull(key, "key");
            if (present != (startsAt != null)) throw new IllegalArgumentException("A present slot requires StartsAt.");
            if (!present && endsAt != null) throw new IllegalArgumentException("An absent slot cannot have EndsAt.");
            if (present && !key.supportsTime() && !allDay) {
                throw new IllegalArgumentException("Only intake supports a timed migrated value.");
            }
        }

        public static Slot absent(MigratedCaseDateKey key) { return new Slot(key, false, null, null, true); }
        public static Slot present(MigratedCaseDateKey key, LocalDateTime startsAt,
                                   LocalDateTime endsAt, boolean allDay) {
            return new Slot(key, true, startsAt, endsAt, allDay);
        }
    }

    public static MigratedCaseDateProjectionDto empty(long caseId) {
        EnumMap<MigratedCaseDateKey, Slot> slots = new EnumMap<>(MigratedCaseDateKey.class);
        for (MigratedCaseDateKey key : MigratedCaseDateKey.values()) slots.put(key, Slot.absent(key));
        return new MigratedCaseDateProjectionDto(caseId, slots);
    }
}
