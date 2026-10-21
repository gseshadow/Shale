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
class Phase1RLocalSqlServerTest {
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

    @Test void candidateRollbackAndAuthenticationCompatibility() {
        assumeTrue(System.getenv("SHALE_AUTH_VERIFY_OPT_IN")!=null,
                "NOT RUN: use build/phase1r-verification/run.py on dedicated disposable loopback SQL");
        diagnostics.verify(()->{
            validateConfiguration();
            diagnostics.at(SETUP,RESOURCES);setup();
            diagnostics.step(POOLS);
            authPool=pool(authLogin,authSecret);runtimePool=pool(runtimeLogin,runtimeSecret);
            diagnostics.at(CREDENTIAL_IDENTITY,CREDENTIALS);credentials();
            apiLifecycle();
            System.out.println("PHASE1R PASS api_lifecycle");
            diagnostics.at(REMEMBERED_ROTATION,SESSION_SEED);remembered();
            diagnostics.at(RUNTIME_ISOLATION,ISOLATION);runtimeIsolation();setupFailure();
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
            try(var q=c.createStatement();var r=q.executeQuery("SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")){require(r.next());require(Set.of(2,3,4).contains(r.getInt(1)));}
            try(var q=c.prepareStatement("SELECT DB_ID(?),SUSER_ID(?),SUSER_ID(?)")){q.setString(1,db);q.setString(2,authLogin);q.setString(3,runtimeLogin);try(var r=q.executeQuery()){require(r.next());require(r.getObject(1)==null&&r.getObject(2)==null&&r.getObject(3)==null);}}
            execute(c,"CREATE DATABASE ["+db+"] COLLATE SQL_Latin1_General_CP1_CI_AS");created=true;
            execute(c,"CREATE LOGIN ["+authLogin+"] WITH PASSWORD='"+authSecret+"', CHECK_POLICY=OFF");authCreated=true;authSid=loginSid(c,authLogin);
            execute(c,"CREATE LOGIN ["+runtimeLogin+"] WITH PASSWORD='"+runtimeSecret+"', CHECK_POLICY=OFF");runtimeCreated=true;runtimeSid=loginSid(c,runtimeLogin);
        }
        try(var c=admin(db)) {
            execute(c,"CREATE TABLE dbo.AuthVerifierOwner(Marker uniqueidentifier NOT NULL)");
            try(var q=c.prepareStatement("INSERT dbo.AuthVerifierOwner VALUES(?)")){q.setObject(1,UUID.fromString(marker));q.executeUpdate();}marked=true;
            execute(c,"CREATE TABLE dbo.ShaleClients(Id int PRIMARY KEY)");execute(c,"INSERT dbo.ShaleClients VALUES(11),(22)");
            execute(c,"CREATE TABLE dbo.Users(Id int PRIMARY KEY,ShaleClientId int NULL,email nvarchar(255),password_hash nvarchar(255),password_alg varchar(20),name_first nvarchar(50),name_last nvarchar(50),color nvarchar(20),is_attorney bit,is_admin bit,is_deleted bit NULL,IsRemoved bit,default_organization int,organization_id int,initials nvarchar(20),RowVer rowversion)");
            execute(c,"CREATE UNIQUE INDEX UX_Users_ShaleClientId_Id ON dbo.Users(ShaleClientId,Id)");
            execute(c,"CREATE TABLE dbo.ApplicationInstances(Id bigint PRIMARY KEY,ShaleClientId int NOT NULL,UserId int NOT NULL)");
            execute(c,"CREATE TABLE dbo.auditLog(Id int,ShaleClientId int NOT NULL,EntryCode int)");
            execute(c,"CREATE TABLE dbo.Statuses(Id int,ShaleClientId int NULL)");
            execute(c,"CREATE SCHEMA sec AUTHORIZATION dbo");
            execute(c,Phase1RCandidateFixture.STRICT);
            execute(c,Phase1RCandidateFixture.OVERLAY);
            execute(c,"CREATE SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.ApplicationInstances WITH(STATE=ON)");
            execute(c,"CREATE TABLE dbo.Phase1RUnrelated(ShaleClientId int NOT NULL)");
            execute(c,"CREATE SECURITY POLICY sec.UnrelatedPolicy ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.Phase1RUnrelated WITH(STATE=ON)");
            migration(c,"2026-09-29_user_sessions_foundation_phase7a.sql");
            migration(c,"2026-10-05_desktop_remember_credentials.sql");
            migration(c,"2026-10-14_pretenant_authentication_modules.sql");
            execute(c,"CREATE ROLE shale_runtime_role AUTHORIZATION dbo");
            execute(c,"CREATE USER shale_app FOR LOGIN ["+authLogin+"]");
            execute(c,"CREATE USER shale_runtime FOR LOGIN ["+runtimeLogin+"]");
            execute(c,"ALTER ROLE shale_runtime_role ADD MEMBER shale_runtime");
            execute(c,"GRANT SELECT ON dbo.Users TO shale_runtime; DENY SELECT ON dbo.Users(password_hash) TO shale_runtime_role");
            execute(c,"GRANT UPDATE ON dbo.Users(password_hash,password_alg) TO shale_runtime");
            execute(c,"GRANT SELECT ON dbo.ApplicationInstances TO shale_runtime");
            execute(c,"GRANT SELECT,INSERT,UPDATE ON dbo.UserSessions TO shale_runtime");
            execute(c,"GRANT SELECT,INSERT,UPDATE,DELETE ON dbo.DesktopRememberCredentials TO shale_runtime");
            execute(c,"GRANT SELECT ON dbo.Statuses TO shale_runtime; GRANT SELECT,INSERT ON dbo.auditLog TO shale_runtime");
            for(String module:List.of("LookupAuthenticationCredentialV1","ResolveDesktopRememberCredentialV2")) {
                execute(c,"GRANT EXECUTE ON dbo."+module+" TO shale_app");
                execute(c,"DENY EXECUTE ON dbo."+module+" TO shale_runtime");
                execute(c,"DENY EXECUTE ON dbo."+module+" TO shale_runtime_role");
            }
            // Synthetic least-capability fixture only; no old operator packet or retirement/rehearsal.
            execute(c,"DENY SELECT ON dbo.Users TO shale_app; DENY SELECT ON dbo.DesktopRememberCredentials TO shale_app");
            execute(c,"INSERT dbo.auditLog VALUES(1,11,4),(2,22,4); INSERT dbo.Statuses VALUES(1,NULL),(2,11),(3,22)");
            new Phase1RCandidateFixture(c,db).verify();
            System.out.println("PHASE1R PASS migration_rollback");
            String hash=org.mindrot.jbcrypt.BCrypt.hashpw("synthetic-password",org.mindrot.jbcrypt.BCrypt.gensalt(4));
            for(int id=1;id<=9;id++)try(var q=c.prepareStatement("INSERT dbo.Users(Id,ShaleClientId,email,password_hash,is_deleted,IsRemoved,is_admin,is_attorney) VALUES(?,?,?,?,?,?,0,0)")) {
                q.setInt(1,id);if(id==5)q.setNull(2,Types.INTEGER);else q.setInt(2,id==2?22:id==9?33:11);
                q.setString(3,id==7||id==8?"duplicate@test":"owner"+id+"@test");q.setString(4,hash);q.setBoolean(5,id==3);q.setBoolean(6,id==4);q.executeUpdate();
            }
            execute(c,"UPDATE dbo.Users SET name_first=N'Synthetic',name_last=N'Owner' WHERE Id=1");
        }
    }
    private void nonBypass(Connection c,String expected)throws Exception {
        try(var q=c.createStatement();var r=q.executeQuery("SELECT USER_NAME(),COALESCE(IS_MEMBER('ShaleAdmins'),0),IS_MEMBER('db_owner'),HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','CONTROL'),HAS_PERMS_BY_NAME('dbo.LookupAuthenticationCredentialV1','OBJECT','ALTER'),HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','ALTER ANY SECURITY POLICY'),IS_SRVROLEMEMBER('sysadmin')")) {
            require(r.next());require(expected.equals(r.getString(1)));require(r.getInt(2)==0&&r.getInt(3)==0&&r.getInt(4)==0&&r.getInt(5)==0&&r.getInt(6)==0&&r.getInt(7)==0);
        }
        for(String sql:List.of("SELECT password_hash FROM dbo.Users","SELECT * FROM dbo.DesktopRememberCredentials")) {
            if(expected.equals("shale_app"))assertThrows(SQLException.class,()->execute(c,sql));
        }
        assertThrows(SQLException.class,()->execute(c,"EXECUTE AS USER='dbo'"));
    }
    private void caller(Connection c,String expected)throws Exception {
        nonBypass(c,expected);
        require(context(c,"ShaleClientId")==null);require(context(c,"PrincipalUserId")==null);
    }
    private Object context(Connection c,String key)throws SQLException{try(var q=c.prepareStatement("SELECT SESSION_CONTEXT(?)")){q.setString(1,key);try(var r=q.executeQuery()){require(r.next());return r.getObject(1);}}}
    private static void migration(Connection c,String file)throws Exception {
        for(String batch:Files.readString(Path.of("..","docs","sql",file)).split("(?im)^GO\\s*$"))if(!batch.isBlank())execute(c,batch);
    }
    private void credentials()throws Exception {
        var auth=new AuthServiceImpl(authPool,new BCryptPasswordVerifier());
        assertEquals(11,auth.login("owner1@test","synthetic-password").getShaleClientId());
        assertEquals(22,auth.login("owner2@test","synthetic-password").getShaleClientId());
        for(String email:List.of("missing@test","owner3@test","owner4@test","owner5@test","owner9@test","duplicate@test"))assertThrows(AuthException.class,()->auth.login(email,"synthetic-password"));
        assertThrows(AuthException.class,()->auth.login("owner1@test","wrong"));
        try(var c=authPool.getConnection()){caller(c,"shale_app");require(context(c,"PrincipalUserId")==null);}
        // Failure inside the elevated module must restore its caller too.
        try(var c=admin(db)){execute(c,"CREATE USER WrongModuleOwner WITHOUT LOGIN");execute(c,"ALTER AUTHORIZATION ON OBJECT::dbo.LookupAuthenticationCredentialV1 TO WrongModuleOwner");execute(c,"GRANT EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO shale_app");}
        try {assertThrows(AuthException.class,()->auth.login("owner1@test","synthetic-password"));try(var c=authPool.getConnection()){caller(c,"shale_app");}}
        finally{try(var c=admin(db)){
            execute(c,"ALTER AUTHORIZATION ON OBJECT::dbo.LookupAuthenticationCredentialV1 TO dbo");execute(c,"DROP USER WrongModuleOwner");
            execute(c,"GRANT EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO shale_app");
            execute(c,"DENY EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO shale_runtime");
            execute(c,"DENY EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO shale_runtime_role");
        }}
    }
    private static Calendar utc(){return Calendar.getInstance(TimeZone.getTimeZone("UTC"));}
    private void remembered()throws Exception {
        UUID sid=UUID.randomUUID(),installation=UUID.randomUUID(),jti=UUID.randomUUID();byte[] hash=new byte[32],replacement=new byte[32];replacement[0]=1;
        Instant deadline=Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        try(var c=admin(db)){
            try(var q=c.prepareStatement("INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,CurrentAccessJti,ExpiresAt,ClientType) VALUES(?,11,1,?,?,'DESKTOP')")){q.setObject(1,sid);q.setObject(2,jti);q.setTimestamp(3,Timestamp.from(deadline),utc());q.executeUpdate();}
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
        try(var c=admin(db)){execute(c,"UPDATE dbo.UserSessions SET RevokedAt=SYSUTCDATETIME(),RevocationReason='SECURITY'");}
        diagnostics.step(REVOKED);
        assertTrue(store.rotate(replacement,installation,hash,UUID.randomUUID(),Instant.now()).isEmpty());
        diagnostics.step(CALLER_RESTORATION);
        try(var c=authPool.getConnection()){caller(c,"shale_app");}
    }
    private void runtimeIsolation()throws Exception {
        var runtime=new RuntimeSessionService(runtimePool);Integer spid=null;
        for(int[] identity:List.of(new int[]{11,1},new int[]{11,6},new int[]{22,2},new int[]{11,1})) {
            runtime.initialize(identity[0],identity[1]);
            try(var c=runtime.getConnection()) {
                nonBypass(c,"shale_runtime");
                try(var q=c.createStatement();var r=q.executeQuery("SELECT @@SPID")){require(r.next());if(spid==null)spid=r.getInt(1);else assertEquals(spid.intValue(),r.getInt(1));}
                assertEquals(identity[0],context(c,"ShaleClientId"));assertEquals(identity[1],context(c,"PrincipalUserId"));
                try(var q=c.createStatement();var r=q.executeQuery("SELECT Id FROM dbo.Statuses ORDER BY Id")) {
                    assertTrue(r.next());assertEquals(1,r.getInt(1));assertTrue(r.next());assertEquals(identity[0]==11?2:3,r.getInt(1));assertFalse(r.next());
                }
                try(var q=c.createStatement();var r=q.executeQuery("SELECT Id FROM dbo.auditLog")) {
                    assertTrue(r.next());assertEquals(identity[0]==11?1:2,r.getInt(1));assertFalse(r.next());
                }
                try(var q=c.prepareStatement("SELECT Id FROM dbo.Users WHERE ShaleClientId<>?")){q.setInt(1,identity[0]);try(var r=q.executeQuery()){assertFalse(r.next());}}
                assertThrows(SQLException.class,()->execute(c,"SELECT password_hash FROM dbo.Users"));
                assertThrows(SQLException.class,()->execute(c,"EXEC dbo.LookupAuthenticationCredentialV1 @Email=N'owner1@test'"));
                assertThrows(SQLException.class,()->execute(c,"EXEC dbo.ResolveDesktopRememberCredentialV2 0x00,NULL,NULL"));
                assertThrows(SQLException.class,()->execute(c,"EXECUTE AS USER='dbo'"));
                // Preserve direct tenant User projection and actual password writes without hash SELECT.
                try(var q=c.prepareStatement("SELECT id,name_first,name_last,email,color,is_admin,is_attorney,ShaleClientId FROM dbo.Users WHERE id=? AND ShaleClientId=?")) {
                    q.setInt(1,identity[1]);q.setInt(2,identity[0]);try(var r=q.executeQuery()){assertTrue(r.next());}
                }
                c.setAutoCommit(false);
                try(var q=c.prepareStatement("UPDATE dbo.Users SET password_hash=?,password_alg='bcrypt' WHERE id=? AND ShaleClientId=?")) {
                    q.setString(1,org.mindrot.jbcrypt.BCrypt.hashpw("synthetic-replacement",org.mindrot.jbcrypt.BCrypt.gensalt(4)));
                    q.setInt(2,identity[1]);q.setInt(3,identity[0]);assertEquals(1,q.executeUpdate());
                }finally{c.rollback();c.setAutoCommit(true);}
            }
        }
        try(var c=runtimePool.getConnection()){execute(c,"EXEC sys.sp_set_session_context N'ShaleClientId',NULL");execute(c,"EXEC sys.sp_set_session_context N'PrincipalUserId',NULL");
            try(var q=c.createStatement();var r=q.executeQuery("SELECT Id FROM dbo.Users")){assertFalse(r.next());}
            try(var q=c.createStatement();var r=q.executeQuery("SELECT Id FROM dbo.auditLog")){assertFalse(r.next());}
            try(var q=c.createStatement();var r=q.executeQuery("SELECT Id FROM dbo.Statuses")){assertTrue(r.next());assertEquals(1,r.getInt(1));assertFalse(r.next());}}
        runtime.clear();assertThrows(IllegalStateException.class,runtime::getConnection);
    }
    /** MockMvc executes production HTTP controllers, JWT validation and real SQL persistence. */
    private void apiLifecycle()throws Exception {
        var connections=new RuntimeSessionServiceConnectionProvider(runtimePool);
        var durable=new SqlDurableSessionStore(connections);
        var clock=java.time.Clock.systemUTC();
        var tokens=new ShaleAuthTokenService("phase1r-synthetic-signing-secret-at-least-32-characters",3600,clock);
        var revoked=new InMemoryTokenRevocationStore();
        var sessions=new ServerAuthSessionService(tokens,durable,new DurableSessionTokenValidator(durable),
                new LegacyTokenCompatibilityPolicy(Instant.parse("2020-01-01T00:00:00Z"),3600,clock),revoked);
        var credentials=new com.shale.data.service.adapter.AuthServiceAdapter(new AuthServiceImpl(authPool,new BCryptPasswordVerifier()));
        var resolver=new BearerTokenServerSessionResolver(tokens,sessions);
        try(var context=new org.springframework.context.support.GenericApplicationContext()) {
            context.registerBean(jakarta.servlet.http.HttpServletRequest.class,()->
                    ((org.springframework.web.context.request.ServletRequestAttributes)org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getRequest());
            // Prototype ensures each /me resolves the current request, never a cached token.
            context.getBeanFactory().getBeanDefinition("jakarta.servlet.http.HttpServletRequest").setScope("prototype");
            context.refresh();
            var state=new ServerRuntimeSessionState(resolver,context.getBeanProvider(jakarta.servlet.http.HttpServletRequest.class));
            var profile=new com.shale.server.auth.UserDaoCurrentUserProfileService(new com.shale.data.dao.UserDao(()->{
                try{return connections.openConnection(state.requirePrincipal());}catch(SQLException e){throw new IllegalStateException("Local runtime unavailable");}
            }));
            var auth=new com.shale.server.controller.AuthController(credentials,tokens,revoked,state,profile,Optional.of(sessions));
            var remembers=new SqlRememberCredentialStore(authPool,connections);
            var desktop=new com.shale.server.controller.DesktopSessionController(credentials,sessions,new DesktopApplicationInstanceVerifier(connections),remembers);
            var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
            var api=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(auth,desktop)
                .setControllerAdvice(new com.shale.server.controller.ApiExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(json)).build();
            var login=post(api,json,"/api/auth/login",Map.of("email","owner1@test","password","synthetic-password"),null,200);
            String access=login.get("accessToken").asText();
            var me=api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+access)).andReturn();
            assertEquals(200,me.getResponse().getStatus());assertEquals(11,json.readTree(me.getResponse().getContentAsString()).get("shaleClientId").asInt());
            assertEquals(1,json.readTree(me.getResponse().getContentAsString()).get("userId").asInt());
            assertEquals("Synthetic Owner",json.readTree(me.getResponse().getContentAsString()).get("displayName").asText(),"/me must execute the actual UserDao profile join, not its principal-only fallback");
            String next=post(api,json,"/api/auth/refresh",Map.of(),access,200).get("accessToken").asText();
            post(api,json,"/api/auth/refresh",Map.of(),access,401);
            assertEquals(401,api.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me").header("Authorization","Bearer "+access)).andReturn().getResponse().getStatus());
            post(api,json,"/api/auth/logout",Map.of(),next,200);
            post(api,json,"/api/auth/refresh",Map.of(),next,401);
            UUID installation=UUID.randomUUID();
            var enrolled=post(api,json,"/api/auth/desktop-session",Map.of("email","owner1@test","password","synthetic-password","remember",true,"installationId",installation),null,200);
            String secret=enrolled.get("rememberCredential").asText(),replacement=ServerAuthSessionService.RememberedSession.newSecret();
            var restored=post(api,json,"/api/auth/desktop-session/restore",Map.of("installationId",installation,"credential",secret,"replacementCredential",replacement),null,200);
            post(api,json,"/api/auth/desktop-session/restore",Map.of("installationId",installation,"credential",secret,"replacementCredential",ServerAuthSessionService.RememberedSession.newSecret()),null,401);
            String restoredAccess=restored.get("accessToken").asText();
            assertFalse(sessions.validate(tokens.verifyToken(enrolled.get("accessToken").asText()).orElseThrow()),"remembered rotation must reject old session JTI");
            String refreshed=post(api,json,"/api/auth/refresh",Map.of(),restoredAccess,200).get("accessToken").asText();
            post(api,json,"/api/auth/refresh",Map.of(),restoredAccess,401);
            post(api,json,"/api/auth/logout",Map.of(),refreshed,200);
            post(api,json,"/api/auth/desktop-session/restore",Map.of("installationId",installation,"credential",replacement,"replacementCredential",ServerAuthSessionService.RememberedSession.newSecret()),null,401);
            try(var c=authPool.getConnection()){caller(c,"shale_app");require(context(c,"PrincipalUserId")==null);}
        }
    }
    private static com.fasterxml.jackson.databind.JsonNode post(org.springframework.test.web.servlet.MockMvc api,com.fasterxml.jackson.databind.ObjectMapper json,String path,Object body,String token,int status)throws Exception {
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if(token!=null)request.header("Authorization","Bearer "+token);
        var result=api.perform(request).andReturn();assertEquals(status,result.getResponse().getStatus(),"HTTP lifecycle status must match");
        return json.readTree(result.getResponse().getContentAsString());
    }
    private void setupFailure()throws Exception {
        var closed=new java.util.concurrent.atomic.AtomicBoolean();
        javax.sql.DataSource failing=(javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(p,m,a)->{
            if(!m.getName().equals("getConnection"))throw new UnsupportedOperationException();
            Connection lease=runtimePool.getConnection();
            return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p2,m2,a2)->{
                if(m2.getName().equals("prepareStatement") && ((String)a2[0]).contains("PrincipalUserId"))throw new SQLException("Injected setup failure");
                if(m2.getName().equals("close"))closed.set(true);
                try{return m2.invoke(lease,a2);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
            });
        });
        var runtime=new RuntimeSessionService(failing);runtime.initialize(22,2);
        assertThrows(SQLException.class,runtime::getConnection);assertTrue(closed.get(),"failed setup must release the lease");
        var safe=new RuntimeSessionService(runtimePool);safe.initialize(11,1);
        try(var c=safe.getConnection()){assertEquals(11,context(c,"ShaleClientId"));assertEquals(1,context(c,"PrincipalUserId"));}
        System.out.println("PHASE1R PASS injected_setup_failure");
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
