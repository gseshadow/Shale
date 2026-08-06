package com.shale.core.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stable contract for the nine Phase 3B singleton case-date meanings.
 *
 * <p>The enum names deliberately retain the compatibility property names. Runtime
 * identity is always {@link #systemKey}; labels and database column names are not
 * identities.</p>
 */
public enum MigratedCaseDateKey {
    CALLER_DATE("CallerDate", "intake", true),
    DATE_OF_INJURY("DateOfInjury", "date_of_injury", false),
    DATE_OF_MEDICAL_NEGLIGENCE("DateOfMedicalNegligence", "date_of_medical_negligence", false),
    DATE_MEDICAL_NEGLIGENCE_DISCOVERED("DateMedicalNegligenceWasDiscovered", "date_medical_negligence_discovered", false),
    STATUTE_OF_LIMITATIONS("StatuteOfLimitations", "statute_of_limitations", false),
    TORT_NOTICE_DEADLINE("TortNoticeDeadline", "tort_notice_deadline", false),
    DISCOVERY_DEADLINE("DiscoveryDeadline", "discovery_deadline", false),
    DATE_FEE_AGREEMENT_SIGNED("DateFeeAgreementSigned", "fee_agreement_signed", false),
    DATE_NON_ENGAGEMENT_LETTER_SENT("DateNonEngagementLetterSent", "non_engagement_letter_sent", false);

    public static final String DISCARDED_ALIAS = "medical_negligence_discovered";
    private static final Map<String, MigratedCaseDateKey> BY_KEY = List.of(values()).stream()
            .collect(Collectors.toUnmodifiableMap(MigratedCaseDateKey::systemKey, Function.identity()));

    private final String compatibilityField;
    private final String systemKey;
    private final boolean supportsTime;

    MigratedCaseDateKey(String compatibilityField, String systemKey, boolean supportsTime) {
        this.compatibilityField = compatibilityField;
        this.systemKey = systemKey;
        this.supportsTime = supportsTime;
    }

    public String compatibilityField() { return compatibilityField; }
    public String systemKey() { return systemKey; }
    public boolean supportsTime() { return supportsTime; }

    /** Exact, case-normalized stable-key lookup. The discarded draft alias always fails. */
    public static MigratedCaseDateKey require(String value) {
        String key = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (DISCARDED_ALIAS.equals(key)) {
            throw new IllegalArgumentException("Discarded Case Date SystemKey alias is not supported.");
        }
        MigratedCaseDateKey result = BY_KEY.get(key);
        if (result == null) throw new IllegalArgumentException("SystemKey is not a migrated singleton Case Date meaning.");
        return result;
    }
}
