package com.shale.core.validation;

/** JavaFX-free contract. Parsing, storage normalization and presentation are separate outputs. */
public interface ContactValueValidator {
    String DEFAULT_PHONE_REGION = "US";
    String PHONE_REGION_HELP = "US numbers by default; use +country code for international numbers.";
    record Phone(String displayInput, String canonicalNumber, String extension, String preview) {}
    record Email(String displayInput, String comparisonKey, String transportAddress) {}
    Phone phone(String input, String extension, boolean required, String field);
    Email email(String input, boolean required, String field);
}
