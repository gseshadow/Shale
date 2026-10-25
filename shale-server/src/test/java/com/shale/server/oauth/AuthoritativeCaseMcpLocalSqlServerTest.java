package com.shale.server.oauth;

import com.shale.core.dto.UserSessionView;
import com.shale.core.model.ClientType;
import com.shale.data.security.ai.*;
import com.shale.server.runtime.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static com.shale.core.security.ai.AiScope.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reuses the Case import gate, maintained OAuth flow and independent serving clients.
 * Authoritative definitions, synthetic rows; WEB identity store remains a servlet fixture. */
class AuthoritativeCaseMcpLocalSqlServerTest {
    @ParameterizedTest @ValueSource(strings={"ts","python"})
    void recoveredCandidateThroughBothTools(String kind,@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_AI".equals(System.getenv("SHALE_AI_SQL_OPT_IN"))
                && System.getenv("SHALE_CASE_VERIFY_SCHEMA")!=null,"Authoritative local schema and disposable opt-in required");
        var h=new OAuthAuthorizationFlowTest();
        try {
            setup(h);
            Map<String,String> credentials=new HashMap<>();
            for(var entry:Map.of("SEARCH_TOKEN",72,"OVERVIEW_TOKEN",73,"BOTH_TOKEN",74,"FAULT_TOKEN",75,"RACE_REVOKE_TOKEN",77,"RACE_EXPIRY_TOKEN",78).entrySet()) {
                actor(h,entry.getValue(),7);
                credentials.put(entry.getKey(),h.freshAccess((entry.getKey().equals("SEARCH_TOKEN")||entry.getKey().equals("RACE_REVOKE_TOKEN"))?"cases.search":entry.getKey().equals("BOTH_TOKEN")?"cases.search cases.overview.read":"cases.overview.read"));
            }
            actor(h,76,7);String revoked=h.freshAccess("cases.search cases.overview.read");
            assertEquals(200,h.send(h.revokeRequest(revoked)).getStatus());credentials.put("REVOKED_TOKEN",revoked);
            permissions(h,kind);
            var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();source.setURL(h.url(h.db));source.setUser(h.login);source.setPassword(h.secret);
            com.shale.server.mcp.McpOverviewClientFixture.verify(h.url(h.db),h.bootstrapLogin,h.bootstrapSecret,racingSource(h,source,credentials),credentials,"authoritative-"+kind,temp);
            assertNotNull(expiryRelease);expiryRelease.get(5,java.util.concurrent.TimeUnit.SECONDS);
            try(var c=h.owner(h.db)) {
                assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE UserId IN(77,78) AND Stage='TERMINAL' AND Outcome='DENIED'"),"Both post-commit races must fail at the final gate");
                assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE ObjectId=9003"),"Failed PHI append must roll back");
                assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource r JOIN dbo.IntegrationAccessAuditLog a ON a.Id=r.EventId WHERE r.CaseId IN(9001,9002,9003,999) AND a.Outcome='ALLOWED_READ'"));
                assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog a WHERE a.Outcome='ALLOWED_READ' AND a.Tool='get_case_overview' AND (a.ReturnedCount<>1 OR (SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource r WHERE r.EventId=a.Id AND r.Relation='RETURNED')<>1)"));
                assertEquals(count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ' AND Tool='get_case_overview'"),count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read'"));
            }
        } finally {
            try {
                assertNotNull(expiryRelease,"Expiry injection must actually execute");expiryRelease.get(5,java.util.concurrent.TimeUnit.SECONDS);
                try(var c=h.owner(h.db)) {
                    var evidence=new LinkedHashMap<String,Object>();
                    evidence.put("expiryLockWaitObserved",true);evidence.put("databaseUtcExpiryCrossedBeforeUnlock",true);
                    evidence.put("revocationTerminalDenied",count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE UserId=77 AND Stage='TERMINAL' AND Outcome='DENIED'"));
                    evidence.put("expiryTerminalDenied",count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE UserId=78 AND Stage='TERMINAL' AND Outcome='DENIED'"));
                    int phi=count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read'");
                    int parents=count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ' AND Tool='get_case_overview'");
                    assertEquals(phi,parents,"Each committed Overview has one PHI append and one parent");
                    assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog a WHERE a.Outcome='ALLOWED_READ' AND a.Tool='get_case_overview' AND (a.ReturnedCount<>1 OR (SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource r WHERE r.EventId=a.Id AND r.Relation='RETURNED')<>1)"));
                    assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE ObjectId=9003"));
                    evidence.put("phiOverviewRows",phi);evidence.put("allowedOverviewParents",parents);evidence.put("oneReturnedChildPerOverview",true);evidence.put("injectedPhiFailureDurableRows",0);
                    assertEquals(1,evidence.get("revocationTerminalDenied"));
                    Files.writeString(Path.of("../work/mcp-evidence/authoritative-audits-races-"+kind+".json"),new tools.jackson.databind.ObjectMapper().writeValueAsString(evidence));
                }
            } finally {h.cleanup();}
        }
    }

    @SuppressWarnings("unchecked")
    private void setup(OAuthAuthorizationFlowTest h)throws Exception {
        h.port=System.getenv("SHALE_AI_SQL_PORT");h.password=System.getenv("SHALE_AI_SQL_PASSWORD");
        assertTrue(h.port!=null&&h.port.matches("[0-9]{4,5}")&&Integer.parseInt(h.port)>=1024&&Integer.parseInt(h.port)<=65535);
        assertNotNull(h.password);
        // Reuse the existing reviewed importer; no second SQL parser or alternate schema model.
        List<String> schema;
        try(var loader=new URLClassLoader(new URL[]{Path.of("../shale-data/target/test-classes").toUri().toURL()},getClass().getClassLoader())) {
            var gate=Class.forName("com.shale.data.verification.CaseReadVerificationSafety",true,loader);
            var method=gate.getDeclaredMethod("schema",Path.class,String.class);method.setAccessible(true);
            schema=(List<String>)method.invoke(null,Path.of(System.getenv("SHALE_CASE_VERIFY_SCHEMA")),System.getenv("SHALE_CASE_VERIFY_SCHEMA_SHA256"));
        }
        h.db="AiOAuth_"+UUID.randomUUID().toString().replace("-","");h.login=h.db+"_runtime";h.secret=UUID.randomUUID()+"aB9!";
        h.bootstrapLogin=h.db+"_bootstrap";h.bootstrapSecret=UUID.randomUUID()+"aB9!";
        try(var c=h.owner("master")) {
            OAuthAuthorizationFlowTest.sql(c,"CREATE DATABASE ["+h.db+"]");
            OAuthAuthorizationFlowTest.sql(c,"CREATE LOGIN ["+h.login+"] WITH PASSWORD='"+h.secret+"',CHECK_POLICY=OFF");
            OAuthAuthorizationFlowTest.sql(c,"CREATE LOGIN ["+h.bootstrapLogin+"] WITH PASSWORD='"+h.bootstrapSecret+"',CHECK_POLICY=OFF");
        }
        try(var c=h.owner(h.db)) {
            for(String batch:schema)OAuthAuthorizationFlowTest.sql(c,batch);
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.partitions p JOIN sys.tables t ON t.object_id=p.object_id WHERE p.index_id IN(0,1) AND p.rows>0"));
            OAuthAuthorizationFlowTest.sql(c,"SET IDENTITY_INSERT dbo.ShaleClients ON; INSERT dbo.ShaleClients(Id,Code,DisplayName) VALUES(7,N'LOCAL_A',N'Local A'),(8,N'LOCAL_B',N'Local B'); SET IDENTITY_INSERT dbo.ShaleClients OFF");
            OAuthAuthorizationFlowTest.sql(c,"SET IDENTITY_INSERT dbo.Users ON; INSERT dbo.Users(id,ShaleClientId,is_admin,is_deleted,IsRemoved,name_first,name_last,email) VALUES(71,7,1,0,0,N'Synthetic',N'Admin',N'admin@example.invalid'),(72,7,0,0,0,N'Synthetic',N'A72',N'a72@example.invalid'),(73,7,0,0,0,N'Synthetic',N'A73',N'a73@example.invalid'),(74,7,0,0,0,N'Synthetic',N'A74',N'a74@example.invalid'),(75,7,0,0,0,N'Synthetic',N'A75',N'a75@example.invalid'),(76,7,0,0,0,N'Synthetic',N'A76',N'a76@example.invalid'),(77,7,0,0,0,N'Synthetic',N'A77',N'a77@example.invalid'),(78,7,0,0,0,N'Synthetic',N'A78',N'a78@example.invalid'),(82,8,0,0,0,N'Synthetic',N'B82',N'b82@example.invalid'); SET IDENTITY_INSERT dbo.Users OFF");
            for(String migration:List.of("phase3b1.sql","phase3b2b.sql","phase3b2c1.sql","phase3b2c2.sql","revocation.sql","phase3b2d.sql","phase3b3.sql"))
                OAuthAuthorizationFlowTest.sql(c,Files.readString(Path.of("../docs/sql/ai-integration/"+migration)));
            OAuthAuthorizationFlowTest.sql(c,"CREATE USER ["+h.bootstrapLogin+"] FOR LOGIN ["+h.bootstrapLogin+"]; ALTER ROLE AiIntegrationBootstrap ADD MEMBER ["+h.bootstrapLogin+"]");
            OAuthAuthorizationFlowTest.sql(c,"CREATE USER ["+h.login+"] FOR LOGIN ["+h.login+"]; ALTER ROLE AiIntegrationRuntime ADD MEMBER ["+h.login+"]");
            // Same existing shared Case/PHI worker permissions; not dbo or control-plane operator.
            for(String table:List.of("Cases","Users","CaseUsers","CaseStatuses","Statuses","PracticeAreas","UserSessions","ShaleClients"))
                OAuthAuthorizationFlowTest.sql(c,"GRANT SELECT ON dbo.["+table+"] TO ["+h.login+"]");
            OAuthAuthorizationFlowTest.sql(c,"GRANT INSERT ON dbo.auditLog TO ["+h.login+"]");
            seedCases(c);
        }
        OAuthAuthorizationFlowTest.runtime=p->{Connection c=DriverManager.getConnection(h.url(h.db),h.login,h.secret);OAuthAuthorizationFlowTest.sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value="+p.shaleClientId()+"; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value="+p.userId());return c;};
        // Dedicated setup authority for the disabled singleton; never a runtime role membership.
        try(var c=h.owner(h.db)) {OAuthAuthorizationFlowTest.sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1");}
        var controls=new AiIntegrationSecurityService(()->{try{return OAuthAuthorizationFlowTest.runtime.openConnection(new ServerPrincipal(71,7,null));}catch(SQLException e){throw new IllegalStateException(e);}},true,OAuthAuthorizationFlowTest.ISSUER,OAuthAuthorizationFlowTest.RESOURCE);
        controls.setTenantEnabled(true,null);
        h.client=controls.register("Synthetic authoritative client",false,Set.of(CASES_SEARCH,CASES_OVERVIEW_READ),1,List.of(URI.create(OAuthAuthorizationFlowTest.REDIRECT))).clientId();
        OAuthAuthorizationFlowTest.tokens=new ShaleAuthTokenService("synthetic-oauth-flow-secret-at-least-32-bytes",3600,Clock.systemUTC());
        OAuthAuthorizationFlowTest.sessions=new ShaleOAuthHumanTest.FixtureSessions();actor(h,72,7);
        h.context=new AnnotationConfigWebApplicationContext();h.context.setServletContext(new MockServletContext());h.context.getEnvironment().setActiveProfiles("test");
        h.context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("oauth",Map.of("shale.ai.oauth.public-origin","https://shale.test","shale.ai.oauth.identity","TEST","shale.ai.oauth.composition-enabled","true","shale.ai.oauth.authorization-enabled","true","shale.ai.oauth.exchange-enabled","true","shale.ai.oauth.bootstrap.jdbc-url",h.url(h.db),"shale.ai.oauth.bootstrap.username",h.bootstrapLogin,"shale.ai.oauth.bootstrap.password",h.bootstrapSecret)));
        h.context.register(McpOAuthSecurityConfiguration.class,com.shale.server.mcp.McpTransportConfiguration.class,OAuthAuthorizationFlowTest.Fixtures.class);h.context.refresh();
    }

    private void actor(OAuthAuthorizationFlowTest h,int user,int tenant)throws Exception {
        var prepared=OAuthAuthorizationFlowTest.tokens.prepare(new ServerPrincipal(user,tenant,null));h.sid=UUID.randomUUID();h.webToken=OAuthAuthorizationFlowTest.tokens.issueBound(prepared,h.sid);
        OAuthAuthorizationFlowTest.sessions.current=new UserSessionView(user,h.sid,ClientType.WEB,null,prepared.tokenId(),Instant.now(),Instant.now().plusSeconds(3600),null,null,null);
        try(var c=h.owner(h.db)) {OAuthAuthorizationFlowTest.sql(c,"INSERT dbo.UserSessions(SessionId,ShaleClientId,UserId,ClientType,CurrentAccessJti,IssuedAt,ExpiresAt) VALUES('"+h.sid+"',"+tenant+","+user+",'WEB','"+prepared.tokenId()+"',SYSUTCDATETIME(),DATEADD(hour,2,SYSUTCDATETIME()))");}
    }

    private void seedCases(Connection c)throws Exception {
        String columns="Id,ShaleClientId,Name,CaseNumber,IsDeleted,ClientEstate,FollowUpMeetWithClient,FollowUpNurseReview,FollowUpExpertReview,FollowUpCaseTransferred,AcceptedChronology,AcceptedConsultantExpertSearch,AcceptedTestifyingExpertSearch,AcceptedMedicalLiterature,DeniedChronology,FeeAgreementSigned,MedicalRecordsRequested,CreatedAt,UpdatedAt";
        try(var s=c.createStatement()) {s.execute("SET IDENTITY_INSERT dbo.Cases ON");
            try(var p=c.prepareStatement("INSERT dbo.Cases("+columns+") VALUES(?,?,?,N'NUMBER',?,0,0,0,0,0,0,0,0,0,0,0,0,'2026-10-23T11:22:33.1234567','2026-10-23T11:22:33.1234567')")) {
                for(int n=0;n<101;n++){p.setInt(1,1000+n);p.setInt(2,7);p.setString(3,String.format(Locale.ROOT,"MCP literal %03d",n));p.setInt(4,0);p.executeUpdate();}
                for(var row:List.of(new Object[]{100,7,"Direct overview",0},new Object[]{9000,7,"Oversized "+"x".repeat(256),0},new Object[]{9001,8,"MCP literal foreign",0},new Object[]{9002,7,"MCP literal deleted",1},new Object[]{9003,7,"PHI failure",0},new Object[]{9004,7,"literal %_[ match",0},new Object[]{9005,7,"literal decoy match",0})) {
                    for(int i=0;i<4;i++)p.setObject(i+1,row[i]);p.executeUpdate();
                }
            } finally {s.execute("SET IDENTITY_INSERT dbo.Cases OFF");}
        }
        // This deliberate trigger is only a local fault injection, never part of the candidate artifact.
        OAuthAuthorizationFlowTest.sql(c,"CREATE TRIGGER dbo.LocalPhiFault ON dbo.auditLog AFTER INSERT AS BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE ObjectId=9003) THROW 59999,'Local fault',1; END");
    }

    private void permissions(OAuthAuthorizationFlowTest h,String kind)throws Exception {
        var receipt=new LinkedHashMap<String,Object>();
        try(var c=DriverManager.getConnection(h.url(h.db),h.login,h.secret)) {
            assertEquals(0,count(c,"SELECT COALESCE(IS_MEMBER('ShaleAdmins'),0)+IS_MEMBER('db_owner')+IS_SRVROLEMEMBER('sysadmin')+IS_MEMBER('AiCapabilityOperator')"));
            for(String table:List.of("AiProductCapability","TenantAiIntegrationPolicy","AiIntegrationRegistration","AiIntegrationRedirect","AiIntegrationClientKey","AiDelegationGrant","AiConsentIntent","AiDelegatedSession","AiOAuthCredential","AiQuotaReservation","AiAuthorizationTransaction","AiAssertionReplay","IntegrationAccessAuditLog","IntegrationAccessAuditResource"))
                assertEquals(229,assertThrows(SQLException.class,()->OAuthAuthorizationFlowTest.sql(c,"SELECT * FROM dbo."+table)).getErrorCode(),table);
            assertEquals(229,assertThrows(SQLException.class,()->OAuthAuthorizationFlowTest.sql(c,"INSERT dbo.IntegrationAccessAuditLog DEFAULT VALUES")).getErrorCode());
            assertEquals(229,assertThrows(SQLException.class,()->OAuthAuthorizationFlowTest.sql(c,"INSERT dbo.IntegrationAccessAuditResource DEFAULT VALUES")).getErrorCode());
            assertEquals(229,assertThrows(SQLException.class,()->OAuthAuthorizationFlowTest.sql(c,"EXEC dbo.AiSetProductCapabilityV1 @Enabled=1,@ExpectedRowVer=0x0000000000000000,@CorrelationId=NULL")).getErrorCode());
            assertThrows(SQLException.class,()->OAuthAuthorizationFlowTest.sql(c,"EXECUTE AS USER='dbo'"));
            assertEquals(0,count(c,"SELECT HAS_PERMS_BY_NAME('sec.TenantFilter','OBJECT','ALTER')"));
            for(String audit:List.of("EntityActionAuditLog","GlobalControlPlaneAuditLog"))
                assertEquals(229,assertThrows(SQLException.class,()->OAuthAuthorizationFlowTest.sql(c,"INSERT dbo."+audit+" DEFAULT VALUES")).getErrorCode());
            assertThrows(SQLException.class,()->OAuthAuthorizationFlowTest.sql(c,"EXEC dbo.ResolveAiClientV1 'x',N'x',N'x'"));
            assertThrows(SQLException.class,()->OAuthAuthorizationFlowTest.sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.Cases"),"NULL context must see no Cases");
            OAuthAuthorizationFlowTest.sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=72");
            assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.Cases WHERE Id=9001"));
            receipt.put("nonBypass",true);
            receipt.put("protectedAiTablesDirectSelect","DENIED SQL 229");
            receipt.put("integrationAuditDirectInsert","DENIED SQL 229 (parent and child)");
            receipt.put("globalControlPlaneExecute","DENIED SQL 229");
            receipt.put("dboImpersonationAndBootstrapExecute","DENIED");
            receipt.put("foreignCaseWithAContext",0);
            receipt.put("readGrants",List.of("Cases","Users","CaseUsers","CaseStatuses","Statuses","PracticeAreas","UserSessions","ShaleClients"));
            receipt.put("phiAuditInsertGrant",true);
            receipt.put("tenantControlExecuteGrant",count(c,"SELECT HAS_PERMS_BY_NAME('dbo.AiSetTenantPolicyV1','OBJECT','EXECUTE')"));
            receipt.put("schemaPolicyAlter",false);
            // Compromise drills: record actual capabilities, never turn an expected gap into a pass.
            c.setAutoCommit(false);
            try(var insert=c.createStatement()) {
                assertEquals(1,insert.executeUpdate("INSERT dbo.auditLog(ShaleClientId,userId,objectTypeId,objectId,fieldName,fieldCode,stringValue) VALUES(8,82,1,9001,'Case.Overview.Read',4,'action=READ;screen=Case.Overview')"));
                receipt.put("foreignPhiAuditDirectInsert","ALLOWED (rolled back): INSERT grant plus FILTER-only policy does not guard writes");
            }finally {c.rollback();c.setAutoCommit(true);}
            OAuthAuthorizationFlowTest.sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=8; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=82");
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.Cases WHERE Id=9001"));
            receipt.put("arbitrarySqlContextSwitch","ALLOWED: same runtime login can stamp B/82 and read B Case; MCP arguments cannot do this");
            receipt.put("requestedPermissionAcceptance","FAILED: shared SQL credential does not confine tenant/user stamping or arbitrary PHI audit INSERT");
        }
        Path evidence=Path.of("../work/mcp-evidence");Files.createDirectories(evidence);
        Files.writeString(evidence.resolve("authoritative-permissions-"+kind+".json"),new tools.jackson.databind.ObjectMapper().writeValueAsString(receipt));
    }

    private javax.sql.DataSource racingSource(OAuthAuthorizationFlowTest h,javax.sql.DataSource source,Map<String,String> credentials) {
        var fired=java.util.concurrent.ConcurrentHashMap.<Integer>newKeySet();
        return (javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(proxy,method,args)->{
            try {
                Object result=method.invoke(source,args);
                if(!method.getName().equals("getConnection"))return result;
                var c=(Connection)result;
                return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
                    try {
                        Object value=m.invoke(c,a);
                        if(m.getName().equals("prepareCall")&&a[0].toString().contains("AiGateCaseReadV1")) {
                            var statement=(CallableStatement)value;
                            var finalGate=new java.util.concurrent.atomic.AtomicBoolean();
                            return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{CallableStatement.class},(q,sm,sa)->{
                                try {
                                    if(sm.getName().equals("setObject")&&Integer.valueOf(17).equals(sa[0])){finalGate.set(Boolean.TRUE.equals(sa[1]));}
                                    if(sm.getName().equals("execute")&&finalGate.get()&&count(c,"SELECT COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),0)")==78&&fired.add(78))startExpiry(h);
                                    return sm.invoke(statement,sa);
                                }catch(java.lang.reflect.InvocationTargetException ex){throw ex.getCause();}
                            });
                        }
                        if(m.getName().equals("commit")&&count(c,"SELECT COALESCE(TRY_CONVERT(int,SESSION_CONTEXT(N'PrincipalUserId')),0)")==77&&!fired.contains(77))
                            try(var owner=h.owner(h.db)) {
                                if(count(owner,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ' AND UserId=77")>0&&fired.add(77))
                                    assertEquals(200,h.send(h.revokeRequest(credentials.get("RACE_REVOKE_TOKEN"))).getStatus());
                            }
                        return value;
                    }catch(java.lang.reflect.InvocationTargetException ex){throw ex.getCause();}
                });
            }catch(java.lang.reflect.InvocationTargetException ex){throw ex.getCause();}
        });
    }
    private void startExpiry(OAuthAuthorizationFlowTest h)throws Exception {
        Connection held=h.owner(h.db);held.setAutoCommit(false);
        try(var statement=held.createStatement()) {statement.execute("BEGIN TRANSACTION; UPDATE dbo.AiDelegatedSession SET IdleExpiresAt=DATEADD(second,1,SYSUTCDATETIME()) WHERE UserId=78; SELECT 1 FROM dbo.AiDelegatedSession WITH(TABLOCKX,HOLDLOCK)");}
        assertTrue(count(held,"SELECT @@TRANCOUNT")>0);
        assertEquals(1,count(held,"SELECT COUNT(*) FROM dbo.AiDelegatedSession WHERE UserId=78"));
        var release=new java.util.concurrent.CompletableFuture<Void>();expiryRelease=release;
        new Thread(()->{
            try(var observer=h.owner(h.db)) {
                long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(2);boolean waiting=false;
                while(System.nanoTime()<until) {
                    if(count(observer,"SELECT COUNT(*) FROM sys.dm_exec_requests WHERE database_id=DB_ID() AND wait_type LIKE 'LCK_M_%' AND session_id<>@@SPID")>0){waiting=true;break;}
                    Thread.sleep(20);
                }
                assertTrue(waiting,"Serving final gate must positively wait on a SQL lock");
                OAuthAuthorizationFlowTest.sql(observer,"WAITFOR DELAY '00:00:02'");
                assertEquals(1,count(held,"SELECT COUNT(*) FROM dbo.AiDelegatedSession WHERE UserId=78 AND IdleExpiresAt<=SYSUTCDATETIME()"),"Expiry must actually cross database UTC before the lock releases");
                held.commit();release.complete(null);
            }catch(Throwable error){release.completeExceptionally(error);}
            finally {try{held.close();}catch(SQLException ignored){}}
        },"authoritative-expiry-release").start();
    }
    private java.util.concurrent.CompletableFuture<Void> expiryRelease;
    private static int count(Connection c,String query)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(query)){assertTrue(r.next());return r.getInt(1);}}
}
