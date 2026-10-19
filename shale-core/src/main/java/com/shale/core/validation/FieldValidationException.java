package com.shale.core.validation;

import java.util.List;

/** Safe errors contain field identifiers and fixed messages, never submitted values. */
public final class FieldValidationException extends IllegalArgumentException {
    public record FieldError(String field, String code, String message) {}
    private final List<FieldError> errors;
    public FieldValidationException(String field, String code, String message) {
        super(message);
        errors = List.of(new FieldError(field, code, message));
    }
    public List<FieldError> errors() { return errors; }
}
