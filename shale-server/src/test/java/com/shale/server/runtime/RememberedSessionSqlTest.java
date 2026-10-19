package com.shale.server.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.fasterxml.jackson.databind.*;
import com.shale.core.model.User;
import com.shale.core.result.Result;
import com.shale.core.service.AuthServicePort;
import com.shale.server.controller.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Opt-in, disposable loopback SQL only. Retains the database as failure evidence; never accepts a URL. */
@EnabledIfEnvironmentVariable(named="SHALE_SESSION_TEST_SQL_PASSWORD", matches=".+")
class RememberedSessionSqlTest {
    /** Also runnable against the exact pinned classpath; never uses production credentials or arbitrary URLs. */
    public static void main(String[] args)throws Exception{
        if(args.length!=1 || !Set.of("legacy","fixed").contains(args[0]) || System.getenv("SHALE_SESSION_TEST_SQL_PASSWORD")==null)
            throw new IllegalArgumentException("Explicit legacy/fixed mode and isolated SQL test password required");
        var test=new RememberedSessionSqlTest();test.pinnedCompatibility=true;test.provision();
        if(args[0].equals("fixed")){test.servingLoopbackApiExecutesTheSameJdbcLifecycle();return;}
        try{test.servingLoopbackApiExecutesTheSameJdbcLifecycle();throw new AssertionError("Legacy build unexpectedly passed");}
        catch(org.opentest4j.AssertionFailedError failure){
            if(!failure.getMessage().contains("Serving API status for /api/auth/desktop-session/restore") || !failure.getActual().getValue().equals(500))throw failure;
            System.out.println("REPRODUCED legacy serving API/JDBC restore HTTP 500; JVM="+TimeZone.getDefault().getID());
        }
    }
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final ServerPrincipal owner=new ServerPrincipal(1,11,"synthetic@example.invalid");
    private boolean pinnedCompatibility;
    private DataSource admin,auth,runtime;
    private RuntimeConnectionProvider connections;
    private SqlDurableSessionStore durable;
    private ServerAuthSessionService service;
    private MockMvc api;
    private String database;
    private DesktopSessionController desktopController;
    private AuthController authController;
    private String servingOrigin;
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @org.springframework.web.servlet.config.annotation.EnableWebMvc
    static class WebConfig implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @Override public void configureMessageConverters(java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters){
            converters.add(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)));
        }
    }

    private static SQLServerDataSource source(String db,String user,String password){
        var ds=new SQLServerDataSource();ds.setURL("jdbc:sqlserver://127.0.0.1:14439;databaseName="+db+";encrypt=true;trustServerCertificate=true");
        ds.setUser(user);ds.setPassword(password);return ds;
    }
    @BeforeEach void provision()throws Exception{
        database="ShaleSessionUtcTest_"+UUID.randomUUID().toString().replace("-","");
        String password=System.getenv("SHALE_SESSION_TEST_SQL_PASSWORD");
        try(var c=source("master","sa",password).getConnection()){sql(c,"CREATE DATABASE "+database);}
        admin=source(database,"sa",password);
        String appLogin=database+"_app",rtLogin=database+"_rt";
        String localSecret=UUID.randomUUID()+"Aa1!";
        try(var c=admin.getConnection()){
            sql(c,"CREATE LOGIN "+appLogin+" WITH PASSWORD='"+localSecret+"'; CREATE LOGIN "+rtLogin+" WITH PASSWORD='"+localSecret+"'; CREATE USER shale_app FOR LOGIN "+appLogin+"; CREATE USER shale_runtime FOR LOGIN "+rtLogin);
            sql(c,"CREATE TABLE dbo.ShaleClients(Id int PRIMARY KEY); INSERT dbo.ShaleClients VALUES(11),(22); CREATE TABLE dbo.Users(Id int PRIMARY KEY,ShaleClientId int NOT NULL,Email nvarchar(510),is_deleted bit,IsRemoved bit,is_admin bit,is_attorney bit,name_first nvarchar(100),name_last nvarchar(100)); CREATE UNIQUE INDEX UX_Users_ShaleClientId_Id ON dbo.Users(ShaleClientId,Id); INSERT dbo.Users VALUES(1,11,'synthetic@example.invalid',0,0,0,0,'Synthetic','Owner'),(2,22,'other@example.invalid',0,0,0,0,'Other','Owner'); CREATE TABLE dbo.ApplicationInstances(Id bigint PRIMARY KEY,ShaleClientId int NOT NULL,UserId int NOT NULL)");
            sql(c,"CREATE ROLE ShaleAdmins; ALTER TABLE dbo.Users ADD password_hash nvarchar(510),color nvarchar(100),default_organization nvarchar(200),organization_id int,initials nvarchar(20)");
            sql(c,"CREATE SCHEMA sec");
            sql(c,"CREATE FUNCTION sec.fn_FilterByTenant(@Tenant int) RETURNS TABLE WITH SCHEMABINDING AS RETURN SELECT 1 Allowed WHERE USER_NAME()=N'dbo' OR IS_MEMBER(N'ShaleAdmins')=1 OR @Tenant=TRY_CONVERT(int,SESSION_CONTEXT(N'ShaleClientId'))");
            sql(c,"CREATE SECURITY POLICY sec.TenantFilter ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo.ApplicationInstances WITH(STATE=ON)");
            migration(c,"2026-09-29_user_sessions_foundation_phase7a.sql");
            migration(c,"2026-10-05_desktop_remember_credentials.sql");
            migration(c,"2026-10-06_desktop_remember_credentials_permissions.sql");
            if(!pinnedCompatibility){
                migration(c,"2026-10-14_pretenant_authentication_modules.sql");
                sql(c,"GRANT EXECUTE ON dbo.ResolveDesktopRememberCredentialV2 TO shale_app; DENY EXECUTE ON dbo.ResolveDesktopRememberCredentialV2 TO shale_runtime; DENY EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO shale_runtime");
            }
            sql(c,"GRANT SELECT ON dbo.Users TO shale_runtime; GRANT SELECT ON dbo.ApplicationInstances TO shale_runtime; GRANT SELECT,INSERT,UPDATE ON dbo.UserSessions TO shale_runtime");
        }
        auth=source(database,appLogin,localSecret);runtime=source(database,rtLogin,localSecret);
        connections=p->{Connection c=runtime.getConnection();try(var q=c.prepareCall("EXEC sys.sp_set_session_context N'ShaleClientId',?; EXEC sys.sp_set_session_context N'PrincipalUserId',?")){q.setInt(1,p.shaleClientId());q.setInt(2,p.userId());q.execute();}return c;};
        durable=new SqlDurableSessionStore(connections);
        var tokens=new ShaleAuthTokenService("isolated-test-token-secret-at-least-32-characters",3600,Clock.systemUTC());
        var revocations=new InMemoryTokenRevocationStore();
        service=new ServerAuthSessionService(tokens,durable,new DurableSessionTokenValidator(durable),new LegacyTokenCompatibilityPolicy(Instant.parse("2020-01-01T00:00:00Z"),3600,Clock.systemUTC()),revocations);
        // Credential validation is a synthetic fixture. Controllers, services, JWTs, JDBC and migrations are real.
        AuthServicePort credentials=(email,pw)->Result.ok(User.builder().id(1).shaleClientId(11).email(owner.email()).build());
        var remembers=new SqlRememberCredentialStore(auth,connections);
        desktopController=new DesktopSessionController(credentials,service,new DesktopApplicationInstanceVerifier(connections),remembers);
        authController=new AuthController(credentials,tokens,revocations,new ServerRuntimeSessionState(),p->Optional.empty(),Optional.of(service));
        api=MockMvcBuilders.standaloneSetup(desktopController,authController)
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(json)).setControllerAdvice(new ApiExceptionHandler()).build();
        System.out.println("Retained isolated SQL fixture: "+database+" JVM="+TimeZone.getDefault().getID());
    }
    private static void migration(Connection c,String name)throws Exception{
        String text=Files.readString(Path.of("..","docs","sql",name));
        for(String batch:text.split("(?im)^GO\\s*$"))if(!batch.isBlank())sql(c,batch);
    }
    private static void sql(Connection c,String text)throws SQLException{try(var s=c.createStatement()){s.execute(text);}}
    private JsonNode call(String path,Object body,String bearer,int expected)throws Exception{
        if(servingOrigin!=null){
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(servingOrigin+path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)));
            if(bearer!=null)request.header("Authorization","Bearer "+bearer);
            var response=java.net.http.HttpClient.newHttpClient().send(request.build(),java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(expected,response.statusCode(),"Serving API status for "+path);return json.readTree(response.body());
        }
        var request=post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if(bearer!=null)request.header("Authorization","Bearer "+bearer);
        var result=api.perform(request).andReturn();
        if(result.getResponse().getStatus()==500 && result.getResolvedException()!=null){
            Throwable cause=result.getResolvedException();
            while(cause.getCause()!=null)cause=cause.getCause();
            if(cause instanceof SQLException failure){
                assertEquals(547,failure.getErrorCode());
                assertTrue(failure.getMessage().contains("CK_UserSessions_Refresh"));
                System.out.println("Actual API/JDBC failure: SQLState="+failure.getSQLState()+" vendorCode="+failure.getErrorCode()+" constraint=CK_UserSessions_Refresh");
            }
        }
        assertEquals(expected,result.getResponse().getStatus(),"Unexpected status for "+path);
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
    private JsonNode enroll(UUID installation)throws Exception{
        return call("/api/auth/desktop-session",Map.of("email",owner.email(),"password","synthetic fixture","remember",true,"installationId",installation),null,200);
    }
    private Map<String,Object> restore(UUID installation,String old,String replacement){return Map.of("installationId",installation,"credential",old,"replacementCredential",replacement);}
    private static String secret(){return ServerAuthSessionService.RememberedSession.newSecret();}

    @Test void apiEnrollmentRotationReplayRefreshAndLogoutKeepUtcAuthority()throws Exception{
        UUID installation=UUID.randomUUID();var enrolled=enroll(installation);
        UUID sid=UUID.fromString(enrolled.get("sessionId").asText());
        String initial=enrolled.get("rememberCredential").asText(), replacement=secret();
        Instant expected=Instant.parse(enrolled.get("rememberExpiresAt").asText());
        assertInstant(expected,durable.find(owner,sid).orElseThrow().expiresAt());
        call("/api/auth/desktop-session/restore",restore(UUID.randomUUID(),initial,secret()),null,401);
        var restored=call("/api/auth/desktop-session/restore",restore(installation,initial,replacement),null,200);
        try(var c=connections.openConnection(owner);var q=c.createStatement();var r=q.executeQuery("SELECT IssuedAt,SYSUTCDATETIME(),ExpiresAt FROM dbo.UserSessions")){
            assertTrue(r.next());assertInstant(expected,r.getObject(3,LocalDateTime.class).toInstant(ZoneOffset.UTC));assertTrue(Duration.between(r.getObject(1,java.time.LocalDateTime.class).toInstant(ZoneOffset.UTC),r.getObject(2,java.time.LocalDateTime.class).toInstant(ZoneOffset.UTC)).abs().compareTo(Duration.ofSeconds(10))<0,"IssuedAt must remain database UTC");
        }
        assertInstant(expected,Instant.parse(restored.get("rememberExpiresAt").asText()));
        call("/api/auth/desktop-session/restore",restore(installation,initial,secret()),null,401);
        String token=restored.get("accessToken").asText();
        var refreshed=call("/api/auth/refresh",Map.of(),token,200);
        call("/api/auth/refresh",Map.of(),token,401);
        assertInstant(expected,durable.find(owner,sid).orElseThrow().expiresAt());
        var listed=new SessionManagementService(connections).listOwnSessions(owner,sid).stream().filter(s->s.sessionId().equals(sid)).findFirst().orElseThrow();
        assertInstant(expected,listed.expiresAt());
        assertEquals(durable.find(owner,sid).orElseThrow().issuedAt(),listed.issuedAt());
        var view=durable.find(owner,sid).orElseThrow();
        assertFalse(view.lastRefreshedAt().isBefore(view.issuedAt()));
        call("/api/auth/logout",Map.of(),refreshed.get("accessToken").asText(),200);
        assertEquals("USER_LOGOUT",durable.find(owner,sid).orElseThrow().revocationReason());
        assertFalse(durable.find(owner,sid).orElseThrow().revokedAt().isBefore(view.issuedAt()));
        call("/api/auth/desktop-session/restore",restore(installation,replacement,secret()),null,401);
        try(var c=connections.openConnection(new ServerPrincipal(2,22,"other@example.invalid"));var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM dbo.UserSessions")){r.next();assertEquals(0,r.getInt(1),"RLS must remain enabled");}
    }
    @Test void utcJdbcSurvivesJvmZoneChangesAndDenverDstGapAndOverlap()throws Exception{
        TimeZone original=TimeZone.getDefault();
        Instant[] instants={Instant.parse("2026-03-08T08:59:59.123456700Z"),Instant.parse("2026-03-08T09:00:00Z"),
            Instant.parse("2026-11-01T07:30:00Z"),Instant.parse("2026-11-01T08:30:00Z")};
        try(var c=admin.getConnection()){
            sql(c,"CREATE TABLE #UtcProbe(Value datetime2(7) NULL)");
            for(Instant instant:instants)for(String writeZone:List.of("UTC","America/Denver")){
                TimeZone.setDefault(TimeZone.getTimeZone(writeZone));
                sql(c,"DELETE FROM #UtcProbe");
                try(var q=c.prepareStatement("INSERT #UtcProbe VALUES(?)")){com.shale.data.util.UtcJdbcTimestamp.set(q,1,instant);q.executeUpdate();}
                for(String readZone:List.of("America/Denver","UTC")){
                    TimeZone.setDefault(TimeZone.getTimeZone(readZone));
                    try(var q=c.createStatement();var r=q.executeQuery("SELECT Value FROM #UtcProbe")){
                        assertTrue(r.next());assertInstant(instant,com.shale.data.util.UtcJdbcTimestamp.get(r,1).toInstant());
                        assertEquals(instant,r.getObject(1,LocalDateTime.class).toInstant(ZoneOffset.UTC),"stored fields must be UTC even during DST overlap/gap");
                    }
                }
            }
        }finally{TimeZone.setDefault(original);}
    }
    @Test void shortSessionExpiryAndExpiredRememberCredentialFailClosedWithoutTimestampRepair()throws Exception{
        call("/api/auth/desktop-session",Map.of("email",owner.email(),"password","synthetic fixture"),null,200);
        UUID installation=UUID.randomUUID();String credential=secret();
        var session=durable.create(owner,com.shale.core.model.ClientType.DESKTOP,null,UUID.randomUUID(),Instant.now().plusSeconds(3600));
        var remembers=new SqlRememberCredentialStore(auth,connections);
        remembers.create(owner,session.sessionId(),installation,ServerAuthSessionService.RememberedSession.hash(credential),Instant.now().minusSeconds(1));
        call("/api/auth/desktop-session/restore",restore(installation,credential,secret()),null,401);
        // A newly inserted synthetic credential/session, checked at/after its deadline. No existing timestamp is updated.
        Instant deadline=Instant.now().plusSeconds(3600);
        var expiring=durable.create(owner,com.shale.core.model.ClientType.DESKTOP,null,UUID.randomUUID(),deadline);
        String current=secret();remembers.create(owner,expiring.sessionId(),installation,ServerAuthSessionService.RememberedSession.hash(current),deadline);
        assertTrue(remembers.rotate(ServerAuthSessionService.RememberedSession.hash(current),installation,new byte[32],UUID.randomUUID(),deadline).isEmpty(),"deadline equality must reject");
        assertTrue(remembers.rotate(ServerAuthSessionService.RememberedSession.hash(current),installation,new byte[32],UUID.randomUUID(),deadline.plusSeconds(1)).isEmpty(),"expired session/credential must reject");
    }
    @Test void servingLoopbackApiExecutesTheSameJdbcLifecycle()throws Exception{
        var context=new org.springframework.web.context.support.GenericWebApplicationContext();
        org.springframework.context.annotation.AnnotationConfigUtils.registerAnnotationConfigProcessors(context);
        context.registerBean(WebConfig.class);
        context.getBeanFactory().registerSingleton("desktopSessionController",desktopController);
        context.getBeanFactory().registerSingleton("authController",authController);
        context.getBeanFactory().registerSingleton("apiExceptionHandler",new ApiExceptionHandler());
        var factory=new org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory(0);
        factory.setAddress(java.net.InetAddress.getByName("127.0.0.1"));
        Path work=Path.of("..","work");Files.createDirectories(work);
        factory.setBaseDirectory(Files.createTempDirectory(work,"session-api-").toFile());
        var server=factory.getWebServer(servletContext->{
            context.setServletContext(servletContext);
            var servlet=servletContext.addServlet("dispatcher",new org.springframework.web.servlet.DispatcherServlet(context));
            servlet.setLoadOnStartup(1);servlet.addMapping("/");
        });
        try{
            server.start();servingOrigin="http://127.0.0.1:"+server.getPort();
            apiEnrollmentRotationReplayRefreshAndLogoutKeepUtcAuthority();
            System.out.println("PASS serving loopback Tomcat API/JDBC lifecycle JVM="+TimeZone.getDefault().getID());
        }finally{servingOrigin=null;server.stop();server.destroy();context.close();}
    }
    private static void assertInstant(Instant expected,Instant actual){assertTrue(Duration.between(expected,actual).abs().toNanos()<=100,"SQL datetime2(7) must preserve UTC instant within 100ns");}

    @Test void legacyBindingEstablishesTheNamedConstraintAndRollback()throws Exception{
        Assumptions.assumeTrue(TimeZone.getDefault().getID().equals("America/Denver"));
        UUID installation=UUID.randomUUID();var enrolled=enroll(installation);
        UUID sid=UUID.fromString(enrolled.get("sessionId").asText());var before=durable.find(owner,sid).orElseThrow();
        try(var c=connections.openConnection(owner)){
            c.setAutoCommit(false);
            try(var q=c.prepareStatement("UPDATE dbo.UserSessions SET LastRefreshedAt=?,UpdatedAt=? WHERE SessionId=?")){
                q.setTimestamp(1,Timestamp.from(Instant.now()));q.setTimestamp(2,Timestamp.from(Instant.now()));q.setObject(3,sid);
                SQLException failure=assertThrows(SQLException.class,q::executeUpdate);
                assertEquals(547,failure.getErrorCode());assertEquals("23000",failure.getSQLState());
                assertTrue(failure.getMessage().contains("CK_UserSessions_Refresh"),"Capture the actual constraint from isolated SQL, never assume expiry");
                System.out.println("Legacy JDBC reproduction: SQLState=23000 vendorCode=547 constraint=CK_UserSessions_Refresh");
            }finally{c.rollback();}
        }
        assertEquals(before.currentAccessJti(),durable.find(owner,sid).orElseThrow().currentAccessJti());
        assertNull(durable.find(owner,sid).orElseThrow().lastRefreshedAt());
    }
}
