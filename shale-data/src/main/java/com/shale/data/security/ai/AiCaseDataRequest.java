package com.shale.data.security.ai;

/** Request-owned SQL proof, separate from the immutable credential-free identity snapshot.
 * The transport owner closes it in finally AFTER synchronous dispatch and bounded cleanup.
 * Cancellation alone does not close it. Never serialize or log this carrier. */
public final class AiCaseDataRequest implements AutoCloseable {
    private final VerifiedIntegrationContext authority;
    private final ProofDigest proof;
    private int users;
    private boolean ownerClosed;
    AiCaseDataRequest(VerifiedIntegrationContext authority, ProofDigest proof) {
        this.authority = java.util.Objects.requireNonNull(authority);
        this.proof = java.util.Objects.requireNonNull(proof);
    }
    public VerifiedIntegrationContext authority() { return authority; }
    ProofDigest proof() { return proof; }
    synchronized Use retain() {
        if(ownerClosed)throw new SecurityException("Integration request is closed.");
        users++;return new Use();
    }
    final class Use implements AutoCloseable {
        private boolean released;
        @Override public void close() {
            synchronized(AiCaseDataRequest.this) {
                if(released)return;released=true;users--;
                if(ownerClosed && users==0)proof.close();
            }
        }
    }
    @Override public synchronized void close() {
        ownerClosed=true;
        if(users==0)proof.close();
    }
    @Override public String toString() { return "AiCaseDataRequest[redacted]"; }
}
