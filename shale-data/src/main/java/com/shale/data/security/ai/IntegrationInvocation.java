package com.shale.data.security.ai;

import com.shale.core.security.ai.AiScope;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Server-created, immutable invocation. ACCESS provenance is mandatory; no protocol state is retained. */
public final class IntegrationInvocation {
    public enum Operation {
        SEARCH("search_cases", "SEARCH_MINIMIZED_CASES", AiScope.CASES_SEARCH, 10, 32768),
        OVERVIEW("get_case_overview", "READ_MINIMIZED_CASE_OVERVIEW", AiScope.CASES_OVERVIEW_READ, 1, 8192);
        private final String tool, shared;
        private final AiScope scope;
        private final int cost, bytes;
        Operation(String tool, String shared, AiScope scope, int cost, int bytes) {
            this.tool=tool; this.shared=shared; this.scope=scope; this.cost=cost; this.bytes=bytes;
        }
        public String tool() { return tool; }
        public String sharedOperation() { return shared; }
        public AiScope scope() { return scope; }
        public int rowCost() { return cost; }
        public int maximumBytes() { return bytes; }
    }
    private final VerifiedIntegrationContext authority;
    private final Operation operation;
    private final UUID invocationId, correlationId;
    private final Instant deadline;
    private final long monotonicDeadline;
    private IntegrationInvocation(VerifiedIntegrationContext context, Operation operation) {
        this(context,operation,System.nanoTime()+10_000_000_000L);
    }
    private IntegrationInvocation(VerifiedIntegrationContext context, Operation operation, long admissionDeadline) {
        if (context==null || context.credentialId()==null) throw new SecurityException("Verified ACCESS required.");
        authority=context; this.operation=Objects.requireNonNull(operation);
        invocationId=UUID.randomUUID(); correlationId=UUID.randomUUID();
        long remaining=Math.max(0,Math.min(10_000_000_000L,admissionDeadline-System.nanoTime()));
        Instant maximum=Instant.now().plusNanos(remaining);
        deadline=context.deadline().isBefore(maximum) ? context.deadline() : maximum;
        monotonicDeadline=admissionDeadline;
    }
    static IntegrationInvocation create(VerifiedIntegrationContext context, Operation operation) {
        return new IntegrationInvocation(context, operation);
    }
    /** The transport's original admission deadline, never a new ten-second tool budget. */
    public static IntegrationInvocation search(VerifiedIntegrationContext context,long admissionDeadline) {
        return new IntegrationInvocation(context,Operation.SEARCH,admissionDeadline);
    }
    public static IntegrationInvocation overview(VerifiedIntegrationContext context,long admissionDeadline) {
        return new IntegrationInvocation(context,Operation.OVERVIEW,admissionDeadline);
    }
    public VerifiedIntegrationContext authority() { return authority; }
    public Operation operation() { return operation; }
    public UUID invocationId() { return invocationId; }
    public UUID correlationId() { return correlationId; }
    /** Reservation identity is the same server UUID, never token/grant-specific accounting. */
    public UUID reservationId() { return invocationId; }
    public Instant deadline() { return deadline; }
    void check() {
        if (Thread.currentThread().isInterrupted()) throw new IntegrationReadException(IntegrationAccessAudit.Outcome.CANCELLED, IntegrationAccessAudit.Reason.CANCELLED);
        if (System.nanoTime()>=monotonicDeadline || !Instant.now().isBefore(deadline))
            throw new IntegrationReadException(IntegrationAccessAudit.Outcome.UNAVAILABLE, IntegrationAccessAudit.Reason.DEADLINE);
    }
    int timeoutSeconds() { check(); return Math.max(1, Math.min(5, (int)((monotonicDeadline-System.nanoTime())/1_000_000_000L))); }
    @Override public String toString() { return "IntegrationInvocation[redacted]"; }
}
