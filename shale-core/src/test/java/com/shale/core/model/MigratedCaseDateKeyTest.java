package com.shale.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MigratedCaseDateKeyTest {
    @Test void freezesAllNineInventoryMappings() {
        assertEquals(Set.of(
                "CallerDate:intake:true",
                "DateOfInjury:date_of_injury:false",
                "DateOfMedicalNegligence:date_of_medical_negligence:false",
                "DateMedicalNegligenceWasDiscovered:date_medical_negligence_discovered:false",
                "StatuteOfLimitations:statute_of_limitations:false",
                "TortNoticeDeadline:tort_notice_deadline:false",
                "DiscoveryDeadline:discovery_deadline:false",
                "DateFeeAgreementSigned:fee_agreement_signed:false",
                "DateNonEngagementLetterSent:non_engagement_letter_sent:false"),
                Stream.of(MigratedCaseDateKey.values())
                        .map(k -> k.compatibilityField() + ":" + k.systemKey() + ":" + k.supportsTime())
                        .collect(Collectors.toSet()));
    }

    @Test void rejectsDiscardedAliasRatherThanNormalizingIt() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MigratedCaseDateKey.require(" medical_negligence_discovered "));
        assertTrue(error.getMessage().contains("Discarded"));
    }

    @Test void stableLookupIsCaseNormalized() {
        assertSame(MigratedCaseDateKey.DATE_MEDICAL_NEGLIGENCE_DISCOVERED,
                MigratedCaseDateKey.require("DATE_MEDICAL_NEGLIGENCE_DISCOVERED"));
    }
}
