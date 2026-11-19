package com.shale.data.security.ai;

import java.time.Duration;
import java.util.function.LongSupplier;

/** Installation integrity only. Never stores a credential, principal, grant or tenant decision. */
public final class AiInstallationReadiness implements AutoCloseable {
    private final LongSupplier clock;
    private final long maximumAge;
    private boolean verified, closed;
    private long verifiedAt;

    public AiInstallationReadiness() { this(System::nanoTime, Duration.ofMinutes(15).toNanos()); }
    AiInstallationReadiness(LongSupplier clock,long maximumAge) {
        if(maximumAge<=0)throw new IllegalArgumentException("Positive verification lifetime required.");
        this.clock=java.util.Objects.requireNonNull(clock);this.maximumAge=maximumAge;
    }
    public synchronized void requireOpen() { if(closed)throw new Unavailable(); }
    public synchronized void verified() {
        requireOpen();
        long now=clock.getAsLong();
        if(verified && now-verifiedAt>=maximumAge) { closed=true;throw new Unavailable(); }
        verifiedAt=now;verified=true;
    }
    public synchronized void requireReady() {
        requireOpen();
        if(!verified || clock.getAsLong()-verifiedAt>=maximumAge) { closed=true;throw new Unavailable(); }
    }
    @Override public synchronized void close() { closed=true; }
    public static final class Unavailable extends IllegalStateException {
        public Unavailable() { super("AI installation verification unavailable; restart required."); }
    }
}
