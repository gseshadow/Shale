package com.shale.core.validation;

/** Explicit intake provenance, independent of structured phone rows. */
public enum PhoneUnavailableReason {
    UNKNOWN("Unknown"), NOT_PROVIDED("Not provided"), NO_PHONE("No phone");
    private final String label;
    PhoneUnavailableReason(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
