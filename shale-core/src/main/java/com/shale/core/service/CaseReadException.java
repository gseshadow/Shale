package com.shale.core.service;

/** Closed outcomes for the additive Case reads; never carry source values in public messages. */
public final class CaseReadException extends RuntimeException {
    public enum Kind { DENIED, OVERSIZED, TIMEOUT, AUDIT_UNAVAILABLE, READ_UNAVAILABLE }
    private final Kind kind;
    public CaseReadException(Kind kind) { this(kind, null); }
    public CaseReadException(Kind kind, Throwable cause) {
        super("Case read " + kind.name(), cause);
        this.kind = kind;
    }
    public Kind kind() { return kind; }
}
