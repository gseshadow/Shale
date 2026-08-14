package com.shale.ui.controller;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.shale.core.model.CaseDateAggregateCommand;
import com.shale.core.model.CaseDateAggregateResult;
import com.shale.core.model.CompatibilityCaseDateEditor;
import com.shale.core.model.CompatibilityCaseDateMutation;
import com.shale.core.model.CompatibilityCaseDateState;
import com.shale.core.model.MigratedCaseDateKey;

/**
 * Non-visual owner of the existing-case editor's coherent optimistic-concurrency snapshot.
 * Controller code supplies only visible values; stable-key and occurrence concurrency logic
 * remains in the core converter.
 */
final class AuthoritativeCaseDateEditor {
    private CaseDateAggregateResult snapshot;
    private boolean saving;

    void replace(CaseDateAggregateResult replacement) {
        snapshot = Objects.requireNonNull(replacement, "replacement");
        saving = false;
    }

    void invalidate() { snapshot = null; saving = false; }
    boolean isLoaded() { return snapshot != null; }
    boolean isSaving() { return saving; }
    boolean hasConflict(MigratedCaseDateKey key) { return snapshot != null && snapshot.conflicts().contains(key); }
    Map<MigratedCaseDateKey, CompatibilityCaseDateState> states() {
        if (snapshot == null) throw new IllegalStateException("Authoritative Case Dates are not loaded.");
        return snapshot.dates();
    }

    CaseDateAggregateCommand beginSave(int tenantId, int actorId, long caseId,
            Map<MigratedCaseDateKey, CompatibilityCaseDateEditor.EditedValue> edited) {
        if (saving) throw new IllegalStateException("A Case Dates save is already in progress.");
        if (snapshot == null) throw new IllegalStateException("Reload Case Dates before saving.");
        if (!snapshot.conflicts().isEmpty())
            throw new IllegalStateException("Resolve conflicting protected Case Dates in the Dates manager, then reload.");
        Map<MigratedCaseDateKey, CompatibilityCaseDateMutation> intents =
                CompatibilityCaseDateEditor.mutations(snapshot.dates(), edited);
        if (intents.values().stream().allMatch(CompatibilityCaseDateMutation.Unchanged.class::isInstance)) return null;
        saving = true;
        return new CaseDateAggregateCommand(tenantId, actorId, caseId, snapshot.caseRowVer(), intents);
    }

    void failedSave() { saving = false; }

    static Map<MigratedCaseDateKey, CompatibilityCaseDateEditor.EditedValue> values(
            Map<MigratedCaseDateKey, CompatibilityCaseDateState> states) {
        EnumMap<MigratedCaseDateKey, CompatibilityCaseDateEditor.EditedValue> result = new EnumMap<>(MigratedCaseDateKey.class);
        for (MigratedCaseDateKey key : MigratedCaseDateKey.values()) {
            CompatibilityCaseDateState state = Objects.requireNonNull(states.get(key), "Missing " + key);
            result.put(key, new CompatibilityCaseDateEditor.EditedValue(state.startsAt(), state.endsAt(), state.allDay()));
        }
        return result;
    }
}
