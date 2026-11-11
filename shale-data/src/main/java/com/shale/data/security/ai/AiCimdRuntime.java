package com.shale.data.security.ai;

import com.shale.core.runtime.DbSessionProvider;
import com.shale.data.config.AiJdbcUrl;
import com.shale.data.runtime.RuntimeSessionService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.function.Function;

/** Dormant, explicitly constructed runtime. Startup and every lease attest; failure closes all pools.
 * No HTTP registration, legacy source fallback, identity provisioning or feature activation. */
public final class AiCimdRuntime implements AutoCloseable {
    public record Endpoint(String url,String username,String password) {
        public Endpoint {
            AiJdbcUrl.validate(url);
            if(username==null || username.isBlank() || password==null || password.isBlank()) throw invalid();
        }
        @Override public String toString() { return "CimdEndpoint[redacted]"; }
    }
    public record Configuration(Map<AiCimdIdentity,Endpoint> endpoints,String database,String issuer,String resource,AiCaseDataAttestation.Contract contract,AiCimdRuntimeAttestation.CatalogBinding catalogBinding) {
        public Configuration(Map<AiCimdIdentity,Endpoint> endpoints,String database,String issuer,String resource,AiCaseDataAttestation.Contract contract) {
            this(endpoints,database,issuer,resource,contract,null);
        }
        public Configuration {
            endpoints=Map.copyOf(endpoints);
            if(!endpoints.keySet().equals(EnumSet.allOf(AiCimdIdentity.class)) || database==null || database.isBlank() || contract==null) throw invalid();
            AiIntegrationSecurityService.configuredHttps(issuer); AiIntegrationSecurityService.configuredHttps(resource);
            var names=new HashSet<String>();
            for(var endpoint:endpoints.values()) if(!names.add(endpoint.username().toLowerCase(Locale.ROOT))) throw invalid();
        }
        @Override public String toString() { return "CimdConfiguration[redacted]"; }
        /** Reads only dedicated keys. All values resolve before any pool allocation. */
        public static Configuration read(Function<String,String> settings) {
            if(!"V2".equals(required(settings,"shale.ai.cimd.catalog-version"))) throw invalid();
            var binding=new AiCimdRuntimeAttestation.CatalogBinding(required(settings,"shale.ai.cimd.catalog-certificate-sha256"),required(settings,"shale.ai.cimd.catalog-owner-sid"));
            var endpoints=new EnumMap<AiCimdIdentity,Endpoint>(AiCimdIdentity.class);
            for(var role:AiCimdIdentity.values()) {
                String prefix="shale.ai.cimd."+role.key();
                endpoints.put(role,new Endpoint(required(settings,prefix+".jdbc-url"),required(settings,prefix+".username"),required(settings,prefix+".password")));
            }
            try {
                return new Configuration(endpoints,required(settings,"shale.ai.cimd.database"),required(settings,"shale.ai.cimd.issuer"),required(settings,"shale.ai.cimd.resource"),
                        AiCaseDataAttestation.Contract.valueOf(required(settings,"shale.ai.cimd.contract-version")),binding);
            } catch(RuntimeException invalid) { throw invalid(); }
        }
    }
    private final Configuration config;
    private final EnumMap<AiCimdIdentity,HikariDataSource> pools=new EnumMap<>(AiCimdIdentity.class);
    private final AiCimdRuntimeAttestation attestation;
    private boolean closed;
    private AiCimdRuntime(Configuration config,List<DataSource> forbidden) {
        this.config=Objects.requireNonNull(config);
        if(forbidden.size()!=4 || forbidden.stream().anyMatch(Objects::isNull)) throw invalid();
        try {
            for(var role:AiCimdIdentity.values()) pools.put(role,new HikariDataSource(poolConfiguration(role,config.endpoints().get(role))));
            var sources=new EnumMap<AiCimdIdentity,DataSource>(AiCimdIdentity.class);
            var users=new EnumMap<AiCimdIdentity,String>(AiCimdIdentity.class);
            pools.forEach((role,pool)->{sources.put(role,pool);users.put(role,config.endpoints().get(role).username());});
            attestation=new AiCimdRuntimeAttestation(sources,users,forbidden,config.database(),config.issuer(),config.resource(),config.contract(),config.catalogBinding());
            attestation.verify();
        } catch(RuntimeException failure) { close(); throw unavailable(); }
    }
    /** Ordinary auth, ordinary runtime/legacy OAuth, bootstrap, legacy Case, in that order. */
    public static AiCimdRuntime open(Configuration config,List<DataSource> forbidden) { return new AiCimdRuntime(config,List.copyOf(forbidden)); }
    static HikariConfig poolConfiguration(AiCimdIdentity role,Endpoint endpoint) {
        var c=new HikariConfig(); c.setJdbcUrl(endpoint.url()); c.setUsername(endpoint.username()); c.setPassword(endpoint.password());
        c.setPoolName("cimd-"+role.key()); c.setMinimumIdle(0); c.setMaximumPoolSize(2);
        c.setConnectionTimeout(3000); c.setValidationTimeout(1000); c.setInitializationFailTimeout(-1); c.setMaxLifetime(900000);
        c.addDataSourceProperty("encrypt","true"); c.addDataSourceProperty("trustServerCertificate","false");
        c.addDataSourceProperty("loginTimeout","3"); c.addDataSourceProperty("socketTimeout","30000");
        c.addDataSourceProperty("applicationName","ShaleCimd-"+role.key()); return c;
    }
    public synchronized void verify() {
        if(closed) throw unavailable();
        try { attestation.verify(); } catch(RuntimeException failure) { close(); throw unavailable(); }
    }
    /** Owner is supplied by authenticated WEB authority or the restricted SQL digest resolver. */
    public AiCimdOAuthStore oauth(AiCimdIdentity role,AiOAuthExchangeStore.Owner owner) {
        if(!Set.of(AiCimdIdentity.BROWSER,AiCimdIdentity.ISSUER,AiCimdIdentity.RESOLVER,AiCimdIdentity.METADATA,AiCimdIdentity.NEUTRAL).contains(role)) throw invalid();
        return new AiCimdOAuthStore(provider(role,owner),config.issuer(),config.resource());
    }
    public AiApplicationRegistryService registryPublisher() {
        return new AiApplicationRegistryService(provider(AiCimdIdentity.REGISTRY_PUBLISHER,null),config.issuer(),config.resource());
    }
    public AiTenantApplicationService registryTenant(AiOAuthExchangeStore.Owner owner) { return new AiTenantApplicationService(provider(AiCimdIdentity.REGISTRY_TENANT,owner)); }
    public AiCaseDataRequest authenticate(String token,long deadline) {
        return AiCaseDataAccess.authenticate(caseSource(),token,config.issuer(),config.resource(),deadline,AiCaseDataAccess.Namespace.CIMD_V1);
    }
    public IntegrationCaseReader reader(AiCaseDataRequest request) {
        return new AiCaseDataAccess(caseSource(),caseSource(),request,AiCaseDataAccess.Namespace.CIMD_V1);
    }
    private DataSource caseSource() {
        // DataSource intentionally does not expose a pool or accept alternate credentials.
        return new DataSource() {
            @Override public Connection getConnection() { return lease(AiCimdIdentity.CASE,null); }
            @Override public Connection getConnection(String user,String password)throws SQLException { throw new SQLFeatureNotSupportedException(); }
            @Override public <T>T unwrap(Class<T> type)throws SQLException { throw new SQLFeatureNotSupportedException(); }
            @Override public boolean isWrapperFor(Class<?> type) { return false; }
            @Override public java.io.PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(java.io.PrintWriter writer)throws SQLException { throw new SQLFeatureNotSupportedException(); }
            @Override public void setLoginTimeout(int seconds)throws SQLException { throw new SQLFeatureNotSupportedException(); }
            @Override public int getLoginTimeout() { return 3; }
            @Override public java.util.logging.Logger getParentLogger()throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }
        };
    }
    private DbSessionProvider provider(AiCimdIdentity role,AiOAuthExchangeStore.Owner owner) {
        if(role.tenantScoped() != (owner!=null) || owner!=null && (owner.tenantId()<=0 || owner.userId()<=0)) throw invalid();
        return ()->lease(role,owner);
    }
    private synchronized Connection lease(AiCimdIdentity role,AiOAuthExchangeStore.Owner owner) {
        verify();
        HikariDataSource pool=pools.get(role); Connection c=null;
        try {
            if(owner==null) c=pool.getConnection();
            else { var scoped=new RuntimeSessionService(pool); scoped.initialize(owner.tenantId(),owner.userId()); c=scoped.getConnection(5); }
            Connection acquired=c;
            return (Connection)java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("unwrap")) throw new SQLFeatureNotSupportedException();
                if(method.getName().equals("isWrapperFor")) return false;
                if(method.getName().equals("close")) {
                    if(acquired.isClosed()) return null;
                    try {
                        AiCimdRuntimeAttestation.require(acquired,"SELECT CASE WHEN @@TRANCOUNT=0 AND (@@OPTIONS & 2)=0 THEN 1 ELSE 0 END",1);
                        try(var s=AiCimdRuntimeAttestation.statement(acquired)) { s.execute("EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=NULL; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=NULL"); }
                        acquired.close(); return null;
                    } catch(Exception failure) { pool.evictConnection(acquired); acquired.close(); throw new SQLException("CIMD lease restoration failed closed."); }
                }
                try { return method.invoke(acquired,args); }
                catch(java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
            });
        } catch(Exception failure) {
            if(c!=null) { pool.evictConnection(c); try { c.close(); } catch(SQLException ignored) { } }
            close(); throw unavailable();
        }
    }
    @Override public synchronized void close() { closed=true; pools.values().forEach(HikariDataSource::close); }
    private static String required(Function<String,String> settings,String key) { String value=settings.apply(key); if(value==null || value.isBlank()) throw invalid(); return value; }
    private static IllegalStateException invalid() { return new IllegalStateException("Incomplete or inconsistent dedicated CIMD configuration."); }
    private static IllegalStateException unavailable() { return new IllegalStateException("CIMD runtime unavailable; reconstruct and reattest all dedicated pools."); }
}
