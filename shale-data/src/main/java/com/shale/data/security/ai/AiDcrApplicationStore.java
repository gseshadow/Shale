package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import java.time.Instant;
import java.util.*;

/** Tenant-neutral capabilities supplied explicitly by composition, never ordinary runtime authority.
 * Registration metadata must pass the server's DCR profile before this adapter is called.
 * SQL generates identity, bounds capacity, owns atomic audit and creates no tenant authority. */
public final class AiDcrApplicationStore {
    private final DbSessionProvider db;
    private final String issuer, resource;
    private final AiExternalContract contract;
    public AiDcrApplicationStore(DbSessionProvider db, String issuer, String resource) {
        this(db,issuer,resource,AiExternalContract.EXTERNAL_V2);
    }
    public AiDcrApplicationStore(DbSessionProvider db,String issuer,String resource,AiExternalContract contract) {
        if(contract==AiExternalContract.CIMD_V1)throw new IllegalArgumentException("DCR contract required.");
        this.contract=Objects.requireNonNull(contract);
        this.db = Objects.requireNonNull(db); this.issuer = issuer; this.resource = resource;
        AiIntegrationSecurityService.configuredHttps(issuer); AiIntegrationSecurityService.configuredHttps(resource);
    }
    public AiExternalContract contract(){return contract;}
    public record Registration(DcrClientIdentifier identifier, Instant issuedAt, AiApplicationRegistryService.Application application) {
        @Override public String toString() { return "DcrRegistration[redacted]"; }
    }
    public record Metadata(DcrClientIdentifier identifier, UUID application, long revision, String name,
                           String redirects, String keys, int methods, int scopes, byte[] digest, Instant freshUntil) {
        public Metadata { digest = digest.clone(); }
        @Override public byte[] digest() { return digest.clone(); }
        @Override public String toString() { return "DcrMetadata[redacted]"; }
    }
    public Registration register(String name, String validatedRedirects, String validatedPublicKeys, int methods, int scopes) {
        if (name == null || name.isBlank() || name.length() > 200 || validatedRedirects == null || validatedRedirects.length() > 16384 ||
                validatedPublicKeys == null || validatedPublicKeys.length() > 8192 || (methods != 1 && methods != 2) || scopes < 1 || scopes > 3)
            throw new IllegalArgumentException("Bounded validated DCR metadata required.");
        return AiSql.one(AiSql.call(db, contract==AiExternalContract.DCR_V3?"AiDcrRegisterApplicationV3":"AiDcrRegisterApplicationV2", r -> new Registration(
                new DcrClientIdentifier(r.getString("ClientId")), AiSql.instant(r, "IssuedAt"), application(r)),
                issuer, resource, name, validatedRedirects, validatedPublicKeys, methods, scopes, UUID.randomUUID()));
    }
    /** Requires the separate metadata-reader capability. No tenant/owner lookup or network fetch. */
    public Metadata prepare(DcrClientIdentifier identifier) {
        return AiSql.one(AiSql.call(db, "AiDcrPrepareMetadataV2", r -> new Metadata(
                new DcrClientIdentifier(r.getString("ExternalClientId")), AiSql.uuid(r,"ApplicationId"), r.getLong("ApplicationRevision"),
                r.getString("ClientName"), r.getString("Redirects"), r.getString("SigningKeys"), r.getInt("MethodMask"), r.getInt("ScopeMask"),
                r.getBytes("MetadataDigest"), AiSql.instant(r,"FreshUntil")), identifier.value(), issuer, resource));
    }
    /** Requires the separate administrator capability. Revision changes invalidate approval and credentials. */
    public AiApplicationRegistryService.Application changeState(UUID id, boolean disabled, AiIntegrationSecurityService.RowVersion expected) {
        return AiSql.one(AiSql.call(db, "AiDcrChangeStateV2", AiDcrApplicationStore::application,
                Objects.requireNonNull(id), disabled, Objects.requireNonNull(expected).bytes(), UUID.randomUUID()));
    }
    private static AiApplicationRegistryService.Application application(java.sql.ResultSet r) throws java.sql.SQLException {
        return new AiApplicationRegistryService.Application(AiSql.uuid(r,"ApplicationId"),r.getLong("Revision"),r.getBoolean("Disabled"),
                new AiIntegrationSecurityService.RowVersion(r.getBytes("RowVer")));
    }
    @Override public String toString() { return "DcrApplicationStore[redacted]"; }
}
