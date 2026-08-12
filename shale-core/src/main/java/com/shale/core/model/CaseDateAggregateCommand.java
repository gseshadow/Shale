package com.shale.core.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete nine-slot mutation envelope for a case aggregate operation. */
public record CaseDateAggregateCommand(int shaleClientId, int actorUserId, long caseId,
        byte[] expectedCaseRowVer, Map<MigratedCaseDateKey, CompatibilityCaseDateMutation> dates) {
    public CaseDateAggregateCommand {
        if (shaleClientId <= 0 || actorUserId <= 0 || caseId <= 0) throw new IllegalArgumentException("Tenant, actor and case are required.");
        if (expectedCaseRowVer == null || expectedCaseRowVer.length == 0) throw new IllegalArgumentException("expectedCaseRowVer is required.");
        expectedCaseRowVer = expectedCaseRowVer.clone();
        Objects.requireNonNull(dates, "dates");
        EnumMap<MigratedCaseDateKey, CompatibilityCaseDateMutation> copy = new EnumMap<>(MigratedCaseDateKey.class);
        copy.putAll(dates);
        if (copy.size() != MigratedCaseDateKey.values().length || !copy.keySet().containsAll(List.of(MigratedCaseDateKey.values())))
            throw new IllegalArgumentException("All nine migrated singleton intents are required.");
        copy.forEach((key, intent) -> { if (intent == null || intent.key() != key) throw new IllegalArgumentException("Mutation key mismatch."); });
        dates = Map.copyOf(copy);
    }
    @Override public byte[] expectedCaseRowVer() { return expectedCaseRowVer.clone(); }
}
