package com.shale.server.runtime;

import java.time.Clock;
import java.time.Instant;

/** Bounded eligibility for otherwise-valid JWTs issued before the session-binding deployment boundary. */
public final class LegacyTokenCompatibilityPolicy {
    public static final String CUTOVER_ENV="SHALE_AUTH_SESSION_BINDING_CUTOVER_AT";
    private final Instant cutover; private final long maximumLegacyLifetimeSeconds; private final Clock clock;
    public LegacyTokenCompatibilityPolicy(Instant cutover,long maximumLegacyLifetimeSeconds,Clock clock){
        this.cutover=java.util.Objects.requireNonNull(cutover);this.clock=java.util.Objects.requireNonNull(clock);
        if(maximumLegacyLifetimeSeconds<=0)throw new IllegalArgumentException("maximum legacy lifetime must be positive");
        this.maximumLegacyLifetimeSeconds=maximumLegacyLifetimeSeconds;
    }
    public static LegacyTokenCompatibilityPolicy fromEnvironment(long tokenTtlSeconds){
        String value=System.getenv(CUTOVER_ENV);if(value==null)value=System.getProperty(CUTOVER_ENV);
        if(value==null||value.isBlank())throw new IllegalStateException("Missing required config: "+CUTOVER_ENV);
        try{return new LegacyTokenCompatibilityPolicy(Instant.parse(value.trim()),tokenTtlSeconds,Clock.systemUTC());}
        catch(java.time.format.DateTimeParseException e){throw new IllegalStateException(CUTOVER_ENV+" must be an ISO-8601 UTC instant.",e);}
    }
    public boolean permits(VerifiedAuthToken token){
        Instant issued=Instant.ofEpochSecond(token.issuedAtEpochSeconds());
        return issued.isBefore(cutover) && clock.instant().isBefore(cutover.plusSeconds(maximumLegacyLifetimeSeconds));
    }
}
