package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.shale.server.runtime.AuthVerificationSupport.Phase.*;
import static com.shale.server.runtime.AuthVerificationSupport.Step.*;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.shale.data.auth.AuthServiceImpl;
import com.shale.data.auth.BCryptPasswordVerifier;
import com.shale.data.errors.AuthException;
import com.shale.data.runtime.RuntimeSessionService;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Opt-in SYNTHETIC auth/RLS fixture, not an authoritative schema or production-grant verification. */
class PreTenantAuthenticationLocalSqlServerTest {
    private final String suffix=UUID.randomUUID().toString().replace("-", "");
    private final String db="ShaleAuthVerify_"+suffix;
    private final String authLogin="AuthVerify_"+suffix+"A",runtimeLogin="AuthVerify_"+suffix+"R";
    private final String authSecret="A!9"+UUID.randomUUID(),runtimeSecret="R!9"+UUID.randomUUID();
    private final String marker=UUID.randomUUID().toString();
    private boolean created,marked,authCreated,runtimeCreated;
    private byte[] authSid,runtimeSid;
    private int port;
    private HikariDataSource authPool,runtimePool;
    private final AuthVerificationSupport diagnostics=new AuthVerificationSupport(System.out);

    @Test void boundedAuthAndRememberedRotationUnderNonBypassRoles() {
        assumeTrue(System.getenv("SHALE_AUTH_VERIFY_OPT_IN")!=null,
                "NOT RUN: use build/auth-verification/run.py on dedicated disposable loopback SQL");
        diagnostics.verify(()->{
            validateConfiguration();
            diagnostics.at(SETUP,RESOURCES);setup();
            diagnostics.step(POOLS);
            authPool=pool(authLogin,authSecret);runtimePool=pool(runtimeLogin,runtimeSecret);
            diagnostics.at(CREDENTIAL_IDENTITY,CREDENTIALS);credentials();
            diagnostics.at(REMEMBERED_ROTATION,SESSION_SEED);remembered();
            diagnostics.at(RUNTIME_ISOLATION,ISOLATION);runtimeIsolation();
            System.out.println("AUTH_VERIFY PASS credential_identity");
            System.out.println("AUTH_VERIFY PASS remembered_rotation");
            System.out.println("AUTH_VERIFY PASS runtime_isolation");
        },this::cleanup);
    }

    private void validateConfiguration() {
        require("LOCAL_DISPOSABLE_AUTH".equals(env("OPT_IN")));
        var values=new HashMap<String,String>(System.getenv());
        System.getProperties().forEach((k,v)->values.put(k.toString(),v.toString()));
        for(var e:values.entrySet())require(!e.getKey().matches("(?i)(SHALE_(APP|RT|AUTH(?!_VERIFY)|SERVER|SUGGESTION).*|SPRING_DATASOURCE.*|SQLCONNSTR_.*|SQLAZURECONNSTR_.*)")||e.getValue().isBlank());
        for(String name:List.of("JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","_JAVA_OPTIONS","MAVEN_ARGS"))require(System.getenv().getOrDefault(name,"").isBlank());
        port=Integer.parseInt(env("PORT"));require(port>=1024&&port<=65535);
        require(env("ADMIN_USER")!=null&&!env("ADMIN_USER").isBlank());require(env("ADMIN_PASSWORD")!=null&&!env("ADMIN_PASSWORD").isBlank());
    }
    private static String env(String key){return System.getenv("SHALE_AUTH_VERIFY_"+key);}
    private static void require(boolean condition){if(!condition)throw new IllegalStateException("Local auth verification prerequisite failed");}
    private SQLServerDataSource source(String database,String user,String password) {
        require(database.equals("master")||database.equals(db));
        var s=new SQLServerDataSource();s.setURL("jdbc:sqlserver://127.0.0.1:"+port+";databaseName="+database+";encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=15000");
        s.setUser(user);s.setPassword(password);return s;
    }
    private Connection admin(String database)throws SQLException{return source(database,env("ADMIN_USER"),env("ADMIN_PASSWORD")).getConnection();}
    private HikariDataSource pool(String login,String secret) {
        var cfg=new HikariConfig();cfg.setDataSource(source(db,login,secret));cfg.setMaximumPoolSize(1);cfg.setMinimumIdle(0);cfg.setPoolName("auth-verifier");
        return new HikariDataSource(cfg);
    }
    private static void execute(Connection c,String sql)throws SQLException{try(var q=c.createStatement()){q.setQueryTimeout(10);q.execute(sql);}}
    private void setup()throws Exception {
        try(var c=admin("master")) {
            try(var q=c.createStatement();var r=q.executeQuery("SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")){require(r.next());require(r.getInt(1)!=5&&r.getInt(1)!=8);}
            try(var q=c.prepareStatement("SELECT DB_ID(?),SUSER_ID(?),SUSER_ID(?)")){q.setString(1,db);q.setString(2,authLogin);q.setString(3,runtimeLogin);try(var r=q.executeQuery()){require(r.next());require(r.getObject(1)==null&&r.getObject(2)==null&&r.getObject(3)==null);}}
            execute(c,"CREATE DATABASE ["+db+"]");created=true;
            execute(c,"CREATE LOGIN ["+authLogin+"] WITH PASSWORD='"+authSecret+"', CHECK_POLICY=OFF");authCreated=true;authSid=loginSid(c,authLogin);
            execute(c,"CREATE LOGIN ["+runtimeLogin+"] WITH PASSWORD='"+runtimeSecret+"', CHECK_POLICY=OFF");runtimeCreated=true;runtimeSid=loginSid(c,runtimeLogin);
        }
        try(var c=admin(db)) {
            execute(c,"CREATE TABLE dbo.AuthVerifierOwner(Marker uniqueidentifier NOT NULL)");
            try(var q=c.prepareStatement("INSERT dbo.AuthVerifierOwner VALUES(?)")){q.setObject(1,UUID.fromString(marker));q.executeUpdate();}marked=true;
            execute(c,"CREATE TABLE dbo.ShaleClients(Id int PRIMARY KEY)");execute(c,"INSERT dbo.ShaleClients VALUES(11),(22)");
            execute(c,"CREATE TABLE dbo.Users(Id int PRIMARY KEY,ShaleClientId int NULL,email nvarchar(255),password_hash nvarchar(255),name_first nvarchar(50),name_last nvarchar(50),color nvarchar(20),is_attorney bit,is_admin bit,is_deleted bit NULL,IsRemoved bit,default_organization int,organization_id int,initials nvarchar(20))");
            execute(c,"CREATE TABLE dbo.UserSessions(SessionId uniqueidentifier PRIMARY KEY,ShaleClientId int NOT NULL,UserId int NOT NULL,CurrentAccessJti uniqueidentifier NOT NULL,ExpiresAt datetime2(7),RevokedAt datetime2(7),LastRefreshedAt datetime2(7),UpdatedAt datetime2(7))");
            execute(c,"CREATE TABLE dbo.DesktopRememberCredentials(ShaleClientId int,UserId int,SessionId uniqueidentifier,InstallationId uniqueidentifier,CredentialHash binary(32) UNIQUE,AbsoluteExpiresAt datetime2(7),ConsumedAt datetime2(7),RotatedAt datetime2(7),UpdatedAt datetime2(7))");
            execute(c,"CREATE SCHEMA sec AUTHORIZATION dbo");
            execute(c,"CREATE FUNCTION sec.fn_FilterByTenant(@Tenant int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 AS AccessResult WHERE USER_NAME()=N'dbo' OR IS_MEMBER(N'ShaleAdmins')=1 OR @Tenant=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))");
            // TEST FIXTURE ONLY: simulate the future strict Users policy, never alter the authoritative export.
            execute(c,"CREATE SECURITY POLICY sec.AuthFixturePolicy ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Users, ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.UserSessions WITH(STATE=ON)");
            String modules=Files.readString(Path.of("..","docs","sql","2026-10-14_pretenant_authentication_modules.sql"));
            try {for(String batch:modules.split("(?im)^GO\\s*$"))if(!batch.isBlank())execute(c,batch);}catch(Exception e){execute(c,"IF @@TRANCOUNT>0 ROLLBACK");throw e;}
            execute(c,"CREATE ROLE ShalePreTenantAuthentication AUTHORIZATION dbo");execute(c,"CREATE ROLE ShaleTenantRuntime AUTHORIZATION dbo");
            execute(c,"CREATE USER ["+authLogin+"] FOR LOGIN ["+authLogin+"]");execute(c,"CREATE USER ["+runtimeLogin+"] FOR LOGIN ["+runtimeLogin+"]");
            execute(c,"ALTER ROLE ShalePreTenantAuthentication ADD MEMBER ["+authLogin+"]");execute(c,"ALTER ROLE ShaleTenantRuntime ADD MEMBER ["+runtimeLogin+"]");
            execute(c,"GRANT EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO ShalePreTenantAuthentication");execute(c,"GRANT EXECUTE ON dbo.ResolveDesktopRememberCredentialV2 TO ShalePreTenantAuthentication");
            execute(c,"GRANT SELECT ON dbo.Users TO ShaleTenantRuntime");execute(c,"DENY SELECT ON dbo.Users(password_hash) TO ShaleTenantRuntime");
            execute(c,"GRANT SELECT,UPDATE ON dbo.UserSessions TO ShaleTenantRuntime");execute(c,"GRANT SELECT,INSERT,UPDATE,DELETE ON dbo.DesktopRememberCredentials TO ShaleTenantRuntime");
            String hash=org.mindrot.jbcrypt.BCrypt.hashpw("synthetic-password",org.mindrot.jbcrypt.BCrypt.gensalt(4));
            for(int id=1;id<=9;id++)try(var q=c.prepareStatement("INSERT dbo.Users(Id,ShaleClientId,email,password_hash,is_deleted,IsRemoved,is_admin,is_attorney) VALUES(?,?,?,?,?,?,0,0)")) {
                q.setInt(1,id);if(id==5)q.setNull(2,Types.INTEGER);else q.setInt(2,id==2?22:id==9?33:11);
                q.setString(3,id==7||id==8?"duplicate@test":"owner"+id+"@test");q.setString(4,hash);q.setBoolean(5,id==3);q.setBoolean(6,id==4);q.executeUpdate();
            }
        }
    }
    private void caller(Connection c,String expected)throws Exception {
        try(var q=c.createStatement();var r=q.executeQuery("SELECT USER_NAME(),COALESCE(IS_MEMBER('ShaleAdmins'),0),IS_MEMBER('db_owner'),HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL')")) {
            require(r.next());require(expected.equals(r.getString(1)));require(r.getInt(2)==0&&r.getInt(3)==0&&r.getInt(4)==0);
        }
        for(String sql:List.of("SELECT password_hash FROM dbo.Users","SELECT * FROM dbo.DesktopRememberCredentials")) {
            if(expected.equals(authLogin))assertThrows(SQLException.class,()->execute(c,sql));
        }
        assertThrows(SQLException.class,()->execute(c,"EXECUTE AS USER='dbo'"));
        require(context(c,"ShaleClientId")==null);
    }
    private Object context(Connection c,String key)throws SQLException{try(var q=c.prepareStatement("SELECT SESSION_CONTEXT(?)")){q.setString(1,key);try(var r=q.executeQuery()){require(r.next());return r.getObject(1);}}}
    private void credentials()throws Exception {
        var auth=new AuthServiceImpl(authPool,new BCryptPasswordVerifier());
        assertEquals(11,auth.login("owner1@test","synthetic-password").getShaleClientId());
        assertEquals(22,auth.login("owner2@test","synthetic-password").getShaleClientId());
        for(String email:List.of("missing@test","owner3@test","owner4@test","owner5@test","owner9@test","duplicate@test"))assertThrows(AuthException.class,()->auth.login(email,"synthetic-password"));
        assertThrows(AuthException.class,()->auth.login("owner1@test","wrong"));
        try(var c=authPool.getConnection()){caller(c,authLogin);require(context(c,"PrincipalUserId")==null);}
        // Failure inside the elevated module must restore its caller too.
        try(var c=admin(db)){execute(c,"ALTER AUTHORIZATION ON OBJECT::dbo.LookupAuthenticationCredentialV1 TO ["+authLogin+"]");execute(c,"GRANT EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO ShalePreTenantAuthentication");}
        try {assertThrows(AuthException.class,()->auth.login("owner1@test","synthetic-password"));try(var c=authPool.getConnection()){caller(c,authLogin);}}
        finally{try(var c=admin(db)){execute(c,"ALTER AUTHORIZATION ON OBJECT::dbo.LookupAuthenticationCredentialV1 TO dbo");execute(c,"GRANT EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO ShalePreTenantAuthentication");}}
    }
    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
    private void remembered()throws Exception {
        UUID sid=UUID.randomUUID(),installation=UUID.randomUUID(),jti=UUID.randomUUID();byte[] hash=new byte[32],replacement=new byte[32];replacement[0]=1;
        Instant deadline=Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        try(var c=admin(db)){
            try(var q=c.prepareStatement("INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,CurrentAccessJti,ExpiresAt) VALUES(?,11,1,?,?)")){q.setObject(1,sid);q.setObject(2,jti);q.setTimestamp(3,Timestamp.from(deadline),utc());q.executeUpdate();}
        }
        var runtime=new RuntimeSessionService(runtimePool);
        var store=new SqlRememberCredentialStore(authPool,p->{runtime.initialize(p.shaleClientId(),p.userId());return runtime.getConnection();});
        diagnostics.step(CREATE);
        store.create(new ServerPrincipal(1,11,"owner1@test"),sid,installation,hash,deadline);
        diagnostics.step(CREATED_DEADLINE);
        try(var c=admin(db);var q=c.prepareStatement("SELECT AbsoluteExpiresAt FROM dbo.DesktopRememberCredentials WHERE SessionId=?")) {
            q.setObject(1,sid);
            try(var r=q.executeQuery()){require(r.next());assertEquals(deadline,r.getTimestamp(1,utc()).toInstant(),"created deadline must be stored as UTC");require(!r.next());}
        }
        diagnostics.step(WRONG_INSTALLATION);
        assertTrue(store.rotate(hash,UUID.randomUUID(),replacement,UUID.randomUUID(),Instant.now()).isEmpty());
        diagnostics.step(ROTATE);
        UUID next=UUID.randomUUID();var rotated=store.rotate(hash,installation,replacement,next,Instant.now());
        assertTrue(rotated.isPresent(),"eligible remembered credential must rotate");var result=rotated.orElseThrow();
        diagnostics.step(PRINCIPAL);assertEquals(11,result.principal().shaleClientId());
        diagnostics.step(DEADLINE);assertEquals(deadline,result.absoluteExpiresAt(),"rotation must preserve the exactly representable fixture deadline");
        diagnostics.step(REPLAY);
        assertTrue(store.rotate(hash,installation,new byte[32],UUID.randomUUID(),Instant.now()).isEmpty());
        diagnostics.step(JTI_IDENTITY);
        try(var c=admin(db);var q=c.prepareStatement("SELECT CurrentAccessJti FROM dbo.UserSessions WHERE SessionId=?")){q.setObject(1,sid);try(var r=q.executeQuery()){assertTrue(AuthVerificationSupport.exactlyOneUuid(r,next),"rotated session JTI identity must match exactly one row");}}
        diagnostics.step(REVOKE);
        try(var c=admin(db)){execute(c,"UPDATE dbo.UserSessions SET RevokedAt=SYSUTCDATETIME()");}
        diagnostics.step(REVOKED);
        assertTrue(store.rotate(replacement,installation,hash,UUID.randomUUID(),Instant.now()).isEmpty());
        for(int owner:List.of(3,4,5,6)) {
            diagnostics.step(switch(owner){case 3->DISABLED_SEED;case 4->REMOVED_SEED;case 5->NULL_TENANT_SEED;default->EXPIRED_SEED;});
            byte[] rejected=new byte[32];rejected[0]=(byte)(owner+10);
            UUID rejectedSession=UUID.randomUUID(),device=UUID.randomUUID();
            try(var c=admin(db)) {
                try(var q=c.prepareStatement("INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,CurrentAccessJti,ExpiresAt) VALUES(?,11,?,?,?)")) {
                    q.setObject(1,rejectedSession);q.setInt(2,owner);q.setObject(3,UUID.randomUUID());
                    q.setTimestamp(4,Timestamp.from(owner==6?Instant.now().minusSeconds(60):deadline),utc());q.executeUpdate();
                }
                try(var q=c.prepareStatement("INSERT dbo.DesktopRememberCredentials(ShaleClientId,UserId,SessionId,InstallationId,CredentialHash,AbsoluteExpiresAt) VALUES(11,?,?,?,?,?)")) {
                    q.setInt(1,owner);q.setObject(2,rejectedSession);q.setObject(3,device);q.setBytes(4,rejected);q.setTimestamp(5,Timestamp.from(deadline),utc());q.executeUpdate();
                }
            }
            diagnostics.step(switch(owner){case 3->DISABLED_REJECT;case 4->REMOVED_REJECT;case 5->NULL_TENANT_REJECT;default->EXPIRED_REJECT;});
            assertTrue(store.rotate(rejected,device,new byte[32],UUID.randomUUID(),Instant.now()).isEmpty(),"disabled/removed/NULL-tenant/expired identity must not rotate");
        }
        diagnostics.step(CALLER_RESTORATION);
        try(var c=authPool.getConnection()){caller(c,authLogin);}
    }
    private void runtimeIsolation()throws Exception {
        var runtime=new RuntimeSessionService(runtimePool);Integer spid=null;
        for(int[] identity:List.of(new int[]{11,1},new int[]{11,6},new int[]{22,2},new int[]{11,1})) {
            runtime.initialize(identity[0],identity[1]);
            try(var c=runtime.getConnection()) {
                try(var q=c.createStatement();var r=q.executeQuery("SELECT @@SPID")){require(r.next());if(spid==null)spid=r.getInt(1);else assertEquals(spid.intValue(),r.getInt(1));}
                assertEquals(identity[0],context(c,"ShaleClientId"));assertEquals(identity[1],context(c,"PrincipalUserId"));
                try(var q=c.prepareStatement("SELECT Id FROM dbo.Users WHERE ShaleClientId<>?")){q.setInt(1,identity[0]);try(var r=q.executeQuery()){assertFalse(r.next());}}
                assertThrows(SQLException.class,()->execute(c,"SELECT password_hash FROM dbo.Users"));
                assertThrows(SQLException.class,()->execute(c,"EXEC dbo.LookupAuthenticationCredentialV1 @Email=N'owner1@test'"));
                assertThrows(SQLException.class,()->execute(c,"EXECUTE AS USER='dbo'"));
            }
        }
        try(var c=runtimePool.getConnection()){execute(c,"EXEC sys.sp_set_session_context N'ShaleClientId',NULL");execute(c,"EXEC sys.sp_set_session_context N'PrincipalUserId',NULL");
            try(var q=c.createStatement();var r=q.executeQuery("SELECT Id FROM dbo.Users")){assertFalse(r.next());}}
        runtime.clear();assertThrows(IllegalStateException.class,runtime::getConnection);
    }
    private byte[] loginSid(Connection c,String login)throws SQLException {
        require(login.equals(authLogin)||login.equals(runtimeLogin));
        try(var q=c.prepareStatement("SELECT sid FROM sys.server_principals WHERE name=? AND type='S'")) {
            q.setString(1,login);
            try(var r=q.executeQuery()){require(r.next());byte[] sid=r.getBytes(1);require(sid!=null&&sid.length>0&&!r.next());return sid;}
        }
    }
    private void cleanup()throws Exception {
        if(authPool!=null)authPool.close();if(runtimePool!=null)runtimePool.close();
        if(created){
            diagnostics.step(MARKER_IDENTITY);
            require(marked);try(var c=admin(db);var q=c.createStatement();var r=q.executeQuery("SELECT Marker FROM dbo.AuthVerifierOwner")){require(AuthVerificationSupport.exactlyOneUuid(r,UUID.fromString(marker)));}
            diagnostics.step(DATABASE_DROP);
            try(var c=admin("master")){execute(c,"ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");execute(c,"DROP DATABASE ["+db+"]");}
        }
        if(authCreated||runtimeCreated)try(var c=admin("master")){
            if(authCreated){
                diagnostics.step(AUTH_LOGIN_IDENTITY);require(AuthVerificationSupport.sameSid(authSid,loginSid(c,authLogin)));
                diagnostics.step(AUTH_LOGIN_DROP);execute(c,"DROP LOGIN ["+authLogin+"]");
            }
            if(runtimeCreated){
                diagnostics.step(RUNTIME_LOGIN_IDENTITY);require(AuthVerificationSupport.sameSid(runtimeSid,loginSid(c,runtimeLogin)));
                diagnostics.step(RUNTIME_LOGIN_DROP);execute(c,"DROP LOGIN ["+runtimeLogin+"]");
            }
        }
    }
}
