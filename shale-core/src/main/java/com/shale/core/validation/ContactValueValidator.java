package com.shale.core.validation;

/** JavaFX-free contract. Parsing, storage normalization and presentation are separate outputs. */
public interface ContactValueValidator {
    String DEFAULT_PHONE_REGION = "US";
    String PHONE_REGION_HELP = "US full or 7-digit local numbers; use +country code for international numbers.";
    enum PhoneKind { GLOBAL, US_LOCAL }
    /** canonicalNumber is E.164 only; localNumber is present only for US_LOCAL. */
    record Phone(String displayInput, String canonicalNumber, String extension, String preview,
                 PhoneKind kind, String localNumber) {
        public Phone(String displayInput, String canonicalNumber, String extension, String preview) {
            this(displayInput, canonicalNumber, extension, preview, PhoneKind.GLOBAL, null);
        }
        /** Whole-number storage/comparison key; never compare only suffixes of global numbers. */
        public String normalizedNumber() { return kind == PhoneKind.US_LOCAL ? localNumber : canonicalNumber; }
        public boolean dialableWithoutContext() { return kind == PhoneKind.GLOBAL; }
    }
    record Email(String displayInput, String comparisonKey, String transportAddress) {}
    Phone phone(String input, String extension, boolean required, String field);
    Email email(String input, boolean required, String field);
}
