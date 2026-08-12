package com.shale.core.model;

import java.util.Locale;

/**
 * Protected application meanings for Case Dates. The persisted key, never the
 * enum ordinal or a Case Date Type label, is the database/API identity.
 */
public enum CaseDateSemanticRole {
    INTAKE("INTAKE"),
    STATUTE_OF_LIMITATIONS("STATUTE_OF_LIMITATIONS"),
    TORT_NOTICE_DEADLINE("TORT_NOTICE_DEADLINE");

    private final String persistedKey;

    CaseDateSemanticRole(String persistedKey) { this.persistedKey = persistedKey; }

    public String persistedKey() { return persistedKey; }
    public String displayName() { return switch(this) { case INTAKE -> "Intake"; case STATUTE_OF_LIMITATIONS -> "Statute of Limitations"; case TORT_NOTICE_DEADLINE -> "Tort Notice Deadline"; }; }

    public static CaseDateSemanticRole require(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        for (CaseDateSemanticRole role : values()) {
            if (role.persistedKey.equals(normalized)) return role;
        }
        throw new IllegalArgumentException("Unknown Case Date semantic role.");
    }
}
