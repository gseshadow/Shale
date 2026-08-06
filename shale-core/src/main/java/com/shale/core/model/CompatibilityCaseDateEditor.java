package com.shale.core.model;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure desktop view-model conversion from a nine-slot snapshot to mutation intents. */
public final class CompatibilityCaseDateEditor {
    private CompatibilityCaseDateEditor() {}

    public record EditedValue(LocalDateTime startsAt, LocalDateTime endsAt, boolean allDay) {}

    public static Map<MigratedCaseDateKey, CompatibilityCaseDateMutation> mutations(
            Map<MigratedCaseDateKey, CompatibilityCaseDateState> original,
            Map<MigratedCaseDateKey, EditedValue> edited) {
        Objects.requireNonNull(original, "original"); Objects.requireNonNull(edited, "edited");
        if (original.size() != 9 || edited.size() != 9 ||
                !original.keySet().containsAll(List.of(MigratedCaseDateKey.values())) ||
                !edited.keySet().containsAll(List.of(MigratedCaseDateKey.values())))
            throw new IllegalArgumentException("The editor requires exactly all nine compatibility dates.");
        EnumMap<MigratedCaseDateKey, CompatibilityCaseDateMutation> out = new EnumMap<>(MigratedCaseDateKey.class);
        for (MigratedCaseDateKey key : MigratedCaseDateKey.values()) {
            CompatibilityCaseDateState before = original.get(key);
            EditedValue after = edited.get(key);
            if (after == null || after.startsAt() == null) {
                out.put(key, before.occurrenceId() == null ? new CompatibilityCaseDateMutation.Unchanged(key)
                        : new CompatibilityCaseDateMutation.Clear(key, before.occurrenceId(), before.occurrenceRowVer()));
            } else {
                CompatibilityCaseDateMutation.Value value = new CompatibilityCaseDateMutation.Value(after.startsAt(), after.endsAt(), after.allDay());
                boolean same = before.occurrenceId() != null && Objects.equals(before.startsAt(), after.startsAt())
                        && Objects.equals(before.endsAt(), after.endsAt()) && before.allDay() == after.allDay();
                out.put(key, same ? new CompatibilityCaseDateMutation.Unchanged(key)
                        : before.occurrenceId() == null
                            ? new CompatibilityCaseDateMutation.Create(key, before.expectedAbsent(), value)
                            : new CompatibilityCaseDateMutation.Update(key, before.occurrenceId(), before.occurrenceRowVer(), value));
            }
        }
        return Map.copyOf(out);
    }
}
