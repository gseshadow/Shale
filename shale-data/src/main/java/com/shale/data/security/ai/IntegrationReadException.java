package com.shale.data.security.ai;

/** Closed operational outcome. Never carries a driver/encoder message or nested sensitive exception. */
public final class IntegrationReadException extends RuntimeException {
    private final IntegrationAccessAudit.Outcome outcome;
    private final IntegrationAccessAudit.Reason reason;
    IntegrationReadException(IntegrationAccessAudit.Outcome outcome, IntegrationAccessAudit.Reason reason) {
        super("Integration read unavailable."); this.outcome=outcome; this.reason=reason;
    }
    public IntegrationAccessAudit.Outcome outcome() { return outcome; }
    public IntegrationAccessAudit.Reason reason() { return reason; }
}
