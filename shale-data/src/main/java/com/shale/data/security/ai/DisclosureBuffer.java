package com.shale.data.security.ai;

import java.nio.ByteBuffer;
import java.util.UUID;

/** One complete immutable payload authorized for one internal handoff; never a streaming source. */
public final class DisclosureBuffer {
    private final byte[] bytes;
    private final UUID invocationId,correlationId;
    DisclosureBuffer(byte[] bytes, IntegrationInvocation i) {
        this.bytes=bytes.clone(); invocationId=i.invocationId(); correlationId=i.correlationId();
    }
    public ByteBuffer bytes() { return ByteBuffer.wrap(bytes).asReadOnlyBuffer(); }
    public int size() { return bytes.length; }
    public UUID invocationId() { return invocationId; }
    public UUID correlationId() { return correlationId; }
    @Override public String toString() { return "DisclosureBuffer[redacted]"; }
}
