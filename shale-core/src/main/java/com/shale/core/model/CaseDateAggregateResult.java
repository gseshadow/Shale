package com.shale.core.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Refreshed concurrency snapshot returned after an aggregate commit. */
public record CaseDateAggregateResult(byte[] caseRowVer,
        Map<MigratedCaseDateKey, CompatibilityCaseDateState> dates) {
    public CaseDateAggregateResult {
        if (caseRowVer == null || caseRowVer.length == 0) throw new IllegalArgumentException("caseRowVer is required.");
        caseRowVer = caseRowVer.clone();
        EnumMap<MigratedCaseDateKey, CompatibilityCaseDateState> copy = new EnumMap<>(MigratedCaseDateKey.class);
        copy.putAll(dates);
        if (copy.size() != MigratedCaseDateKey.values().length || !copy.keySet().containsAll(List.of(MigratedCaseDateKey.values())))
            throw new IllegalArgumentException("All nine refreshed states are required.");
        dates = Map.copyOf(copy);
    }
    @Override public byte[] caseRowVer() { return caseRowVer.clone(); }
}
