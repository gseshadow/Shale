package com.shale.core.model;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

/**
 * Concurrency intent for one authoritative migrated singleton occurrence.
 * Absence is a witnessed state, never an omitted occurrence id.
 */
public sealed interface CompatibilityCaseDateMutation
        permits CompatibilityCaseDateMutation.Unchanged,
                CompatibilityCaseDateMutation.Create,
                CompatibilityCaseDateMutation.Update,
                CompatibilityCaseDateMutation.Clear {
    MigratedCaseDateKey key();

    record Value(LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay) {
        public Value {
            Objects.requireNonNull(startsAt, "startsAt");
            if (endsAt != null && endsAt.isBefore(startsAt))
                throw new IllegalArgumentException("endsAt cannot precede startsAt.");
        }
    }

    /** The Cases.RowVer observed while this singleton was absent. */
    record ExpectedAbsent(byte[] observedCaseRowVer) {
        public ExpectedAbsent {
            observedCaseRowVer = copyRequired(observedCaseRowVer, "observedCaseRowVer");
        }
        @Override public byte[] observedCaseRowVer() { return observedCaseRowVer.clone(); }
    }

    record Unchanged(MigratedCaseDateKey key) implements CompatibilityCaseDateMutation {
        public Unchanged { Objects.requireNonNull(key, "key"); }
    }

    record Create(MigratedCaseDateKey key, ExpectedAbsent expectedAbsent, Value value)
            implements CompatibilityCaseDateMutation {
        public Create {
            Objects.requireNonNull(key, "key"); Objects.requireNonNull(expectedAbsent, "expectedAbsent");
            validateValue(key, value);
        }
    }

    record Update(MigratedCaseDateKey key, long occurrenceId, byte[] expectedRowVer, Value value)
            implements CompatibilityCaseDateMutation {
        public Update {
            if (occurrenceId <= 0) throw new IllegalArgumentException("occurrenceId must be positive.");
            Objects.requireNonNull(key, "key"); expectedRowVer = copyRequired(expectedRowVer, "expectedRowVer");
            validateValue(key, value);
        }
        @Override public byte[] expectedRowVer() { return expectedRowVer.clone(); }
    }

    record Clear(MigratedCaseDateKey key, long occurrenceId, byte[] expectedRowVer)
            implements CompatibilityCaseDateMutation {
        public Clear {
            Objects.requireNonNull(key, "key");
            if (occurrenceId <= 0) throw new IllegalArgumentException("occurrenceId must be positive.");
            expectedRowVer = copyRequired(expectedRowVer, "expectedRowVer");
        }
        @Override public byte[] expectedRowVer() { return expectedRowVer.clone(); }
    }

    private static void validateValue(MigratedCaseDateKey key, Value value) {
        Objects.requireNonNull(value, "value");
        if (!key.supportsTime() && !value.allDay())
            throw new IllegalArgumentException(key.systemKey() + " requires an all-day value.");
    }

    private static byte[] copyRequired(byte[] value, String name) {
        if (value == null || value.length == 0) throw new IllegalArgumentException(name + " is required.");
        return Arrays.copyOf(value, value.length);
    }
}
