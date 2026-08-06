package com.shale.core.model;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class CompatibilityCaseDateMutationTest {
    private static final byte[] RV = {1,2,3};
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 8, 6, 0, 0);

    @Test void allNineMappingsSupportAllFourExplicitIntents() {
        for (MigratedCaseDateKey key : MigratedCaseDateKey.values()) {
            var value = new CompatibilityCaseDateMutation.Value(DAY, null, true);
            assertEquals(key, new CompatibilityCaseDateMutation.Unchanged(key).key());
            assertEquals(key, new CompatibilityCaseDateMutation.Create(key, new CompatibilityCaseDateMutation.ExpectedAbsent(RV), value).key());
            assertEquals(key, new CompatibilityCaseDateMutation.Update(key, 7, RV, value).key());
            assertEquals(key, new CompatibilityCaseDateMutation.Clear(key, 7, RV).key());
        }
    }

    @Test void absenceAndOccurrenceVersionsAreRequiredAndDefensivelyCopied() {
        assertThrows(IllegalArgumentException.class, () -> new CompatibilityCaseDateMutation.ExpectedAbsent(null));
        assertThrows(IllegalArgumentException.class, () -> new CompatibilityCaseDateMutation.Clear(MigratedCaseDateKey.CALLER_DATE, 1, null));
        var token = new CompatibilityCaseDateMutation.ExpectedAbsent(RV);
        token.observedCaseRowVer()[0] = 99;
        assertEquals(1, token.observedCaseRowVer()[0]);
    }

    @Test void onlyIntakeMayBeTimed() {
        var timed = new CompatibilityCaseDateMutation.Value(DAY.withHour(9), null, false);
        assertDoesNotThrow(() -> new CompatibilityCaseDateMutation.Create(MigratedCaseDateKey.CALLER_DATE, new CompatibilityCaseDateMutation.ExpectedAbsent(RV), timed));
        assertThrows(IllegalArgumentException.class, () -> new CompatibilityCaseDateMutation.Create(MigratedCaseDateKey.DATE_OF_INJURY, new CompatibilityCaseDateMutation.ExpectedAbsent(RV), timed));
    }

    @Test void aggregateRequiresExactlyNineCorrectlyKeyedSlotsAndCaseVersion() {
        var intents = new EnumMap<MigratedCaseDateKey, CompatibilityCaseDateMutation>(MigratedCaseDateKey.class);
        for (var key : MigratedCaseDateKey.values()) intents.put(key, new CompatibilityCaseDateMutation.Unchanged(key));
        assertDoesNotThrow(() -> new CaseDateAggregateCommand(1, 2, 3, RV, intents));
        intents.remove(MigratedCaseDateKey.CALLER_DATE);
        assertThrows(IllegalArgumentException.class, () -> new CaseDateAggregateCommand(1, 2, 3, RV, intents));
    }

    @Test void discardedAliasCannotEnterTheCanonicalContract() {
        assertThrows(IllegalArgumentException.class, () -> MigratedCaseDateKey.require("medical_negligence_discovered"));
    }
}
