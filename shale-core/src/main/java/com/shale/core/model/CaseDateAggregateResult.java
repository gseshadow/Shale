package com.shale.core.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Refreshed concurrency snapshot returned after an aggregate commit. */
public record CaseDateAggregateResult(byte[] caseRowVer,
        Map<MigratedCaseDateKey, CompatibilityCaseDateState> dates,
        Set<MigratedCaseDateKey> conflicts) {
    public CaseDateAggregateResult(byte[] caseRowVer, Map<MigratedCaseDateKey, CompatibilityCaseDateState> dates) {
        this(caseRowVer, dates, Set.of());
    }
    public CaseDateAggregateResult {
        if (caseRowVer == null || caseRowVer.length == 0) throw new IllegalArgumentException("caseRowVer is required.");
        caseRowVer = caseRowVer.clone();
        EnumMap<MigratedCaseDateKey, CompatibilityCaseDateState> copy = new EnumMap<>(MigratedCaseDateKey.class);
        copy.putAll(dates);
        if (copy.size() != MigratedCaseDateKey.values().length || !copy.keySet().containsAll(List.of(MigratedCaseDateKey.values())))
            throw new IllegalArgumentException("All nine refreshed states are required.");
        dates = Map.copyOf(copy);
        conflicts = conflicts == null ? Set.of() : Set.copyOf(conflicts);
        if (!dates.keySet().containsAll(conflicts)) throw new IllegalArgumentException("Conflict keys must be mapped dates.");
    }
    @Override public byte[] caseRowVer() { return caseRowVer.clone(); }
}
