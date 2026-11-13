package com.shale.server.oauth;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.config.AiJdbcUrl;
import com.shale.data.runtime.RuntimeSessionService;
import com.shale.data.security.ai.AiOAuthExchangeStore;
import com.shale.server.mcp.McpPayloadLogFilter;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;

/** Dedicated unpooled sources, following the existing bootstrap provider. No provisioning or fallback. */
final class CimdRuntimeConnections {
    static final Map<String, String> ROLES;
    static {
        var roles = new LinkedHashMap<String, String>();
        roles.put("case", "AiExternalCaseRuntimeV2");
        roles.put("reviewer", "AiCimdAuthorizationCatalogV1");
        roles.put("publisher", "AiApplicationPublisher");
        roles.put("approval", "AiApplicationTenant");
        roles.put("onboarding", "AiExternalOnboardingRuntimeV2");
        roles.put("browser", "AiExternalOAuthBrowserV2");
        roles.put("issuer", "AiExternalOAuthIssuerV2");
        roles.put("metadata", "AiExternalMetadataPublisherV2");
        roles.put("resolver", "AiExternalOAuthResolverV2");
        roles.put("neutral", "AiExternalOAuthNeutralV2");
        ROLES = Collections.unmodifiableMap(roles);
    }
    private final Map<String, DataSource> sources;
    private final String database;

    CimdRuntimeConnections(Environment env) {
        this(configured(env), env.getRequiredProperty("shale.ai.data.database"));
    }

    CimdRuntimeConnections(Map<String, DataSource> sources, String database) {
        this.sources = Map.copyOf(sources);
        this.database = database;
    }

    static Map<String, String> roles(Environment env) {
        var roles = new LinkedHashMap<>(ROLES);
        if (env.getProperty("shale.ai.dcr.enabled", Boolean.class, false)) {
            roles.put("dcr-endpoint", "AiDcrEndpointV3");
            roles.put("dcr-metadata", "AiDcrMetadataReaderV2");
        }
        return roles;
    }

    /** Validate the entire settings set before creating sources or opening any connection. */
    static void validate(Environment env) {
        for (String name : roles(env).keySet()) settings(env, "shale.ai.cimd.runtime." + name);
        settings(env, "shale.ai.data.verifier");
        settings(env, "shale.ai.oauth.bootstrap");
        var config = new com.shale.data.config.Config(env::getProperty);
        AiJdbcUrl.validate(config.aiCaseDataJdbcUrl());
        config.aiCaseDataUser();
        config.aiCaseDataPassword();
    }

    private static Map<String, DataSource> configured(Environment env) {
        validate(env);
        var sources = new LinkedHashMap<String, DataSource>();
        for (String name : roles(env).keySet()) sources.put(name, source(env, "shale.ai.cimd.runtime." + name));
        return sources;
    }

    static DataSource source(Environment env, String prefix) {
        var settings = settings(env, prefix);
        McpPayloadLogFilter.installSqlProtection();
        var source = new SQLServerDataSource();
        source.setURL(settings.url());
        source.setUser(settings.user());
        source.setPassword(settings.password());
        source.setEncrypt("true");
        source.setTrustServerCertificate(false);
        source.setLoginTimeout(2);
        source.setSocketTimeout(5000);
        return source;
    }

    private record Settings(String url, String user, String password) {
        @Override public String toString() { return "CimdSqlSettings[redacted]"; }
    }

    private static Settings settings(Environment env, String prefix) {
        String url = AiJdbcUrl.validate(required(env, prefix + ".jdbc-url"));
        String expected = required(env, "shale.ai.data.database");
        boolean database = Arrays.stream(url.split(";")).skip(1).anyMatch(part -> {
            int split = part.indexOf('=');
            return split > 0 && part.substring(0, split).trim().equalsIgnoreCase("databaseName")
                    && part.substring(split + 1).equals(expected);
        });
        if (!database) throw unavailable();
        return new Settings(url, required(env, prefix + ".username"), required(env, prefix + ".password"));
    }

    private static String required(Environment env, String key) {
        String value = env.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing required runtime setting: " + key);
        return value;
    }

    DataSource source(String name) { return Objects.requireNonNull(sources.get(name)); }

    DbSessionProvider neutral(String name) {
        return () -> {
            try { return source(name).getConnection(); }
            catch (SQLException failure) { throw unavailable(); }
        };
    }

    DbSessionProvider scoped(String name, AiOAuthExchangeStore.Owner owner) {
        if (owner == null || owner.tenantId() <= 0 || owner.userId() <= 0) throw unavailable();
        return () -> {
            var session = new RuntimeSessionService(source(name));
            session.initialize(owner.tenantId(), owner.userId());
            try { return session.getConnection(5); }
            catch (SQLException failure) { throw unavailable(); }
        };
    }

    /** Verify actual configured principals once at construction, before capability publication.
     * Existing Case startup/admission catalogs still verify every role/member/permission and source. */
    void verifyIsolation(Map<String, String> roles, List<DataSource> forbidden) {
        var sids = new HashSet<String>();
        Identity anchor = null, predecessor = null;
        String capability = "predecessor";
        try {
            for (var source : forbidden) {
                var identity = identity(source, null);
                sids.add(identity.sid());
                if (predecessor == null) predecessor = identity;
            }
            for (var entry : roles.entrySet()) {
                capability = entry.getKey();
                var identity = identity(source(entry.getKey()), entry.getValue());
                if (!database.equals(identity.database()) || !sids.add(identity.sid())) throw unavailable();
                if (anchor == null) anchor = identity;
                else if (!anchor.server().equals(identity.server())) throw unavailable();
            }
            // The first forbidden source is the independently attested predecessor Case identity.
            if (anchor == null || predecessor == null || !database.equals(predecessor.database())
                    || !anchor.server().equals(predecessor.server())) throw unavailable();
        } catch (SQLException | IllegalStateException failure) {
            throw new IllegalStateException("CIMD runtime identity verification failed; capability=" + capability);
        }
    }

    private record Identity(String database, String server, String sid) { }

    private static Identity identity(DataSource source, String role) throws SQLException {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            connection.setNetworkTimeout(Runnable::run, 5000);
            statement.setQueryTimeout(5);
            try (var rows = statement.executeQuery("""
                    SELECT DB_NAME(), CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')), p.sid,
                      USER_NAME(), USER_ID(), ORIGINAL_LOGIN(), p.type, p.authentication_type,
                      CONVERT(int,SERVERPROPERTY('EngineEdition')), @@TRANCOUNT, @@OPTIONS,
                      SESSION_CONTEXT(N'ShaleClientId'), SESSION_CONTEXT(N'PrincipalUserId'),
                      (SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE session_id=@@SPID
                        AND authenticating_database_id=DB_ID() AND original_security_id=p.sid)
                    FROM sys.database_principals p WHERE principal_id=USER_ID()
                    """)) {
                if (!rows.next() || rows.getBytes(3) == null || rows.getBytes(3).length == 0
                        || rows.getString(1) == null || rows.getString(2) == null) throw unavailable();
                var identity = new Identity(rows.getString(1), rows.getString(2), Base64.getEncoder().encodeToString(rows.getBytes(3)));
                if (role != null) {
                    if (rows.getInt(5) <= 4 || !Objects.equals(rows.getString(4), rows.getString(6))
                            || !"S".equals(rows.getString(7)) || rows.getInt(8) != 2 || rows.getInt(9) != 5
                            || rows.getInt(10) != 0 || (rows.getInt(11) & 2) != 0
                            || rows.getObject(12) != null || rows.getObject(13) != null || rows.getInt(14) != 1)
                        throw unavailable();
                    String user = rows.getString(4);
                    try (var tokens = connection.createStatement()) {
                        tokens.setQueryTimeout(5);
                        try (var tokenRows = tokens.executeQuery("SELECT name FROM sys.user_token")) {
                            var names = new HashSet<String>();
                            while (tokenRows.next()) names.add(tokenRows.getString(1));
                            if (!names.equals(Set.of(user, "public", role))) throw unavailable();
                        }
                    }
                }
                if (rows.next()) throw unavailable();
                return identity;
            }
        }
    }

    @Override public String toString() { return "CimdRuntimeConnections[redacted]"; }
    private static IllegalStateException unavailable() {
        return new IllegalStateException("Dedicated contained runtime identity/configuration required.");
    }
}
