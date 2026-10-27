package com.shale.server.oauth;
import com.shale.core.dto.UserSessionView;
import com.shale.data.security.ai.AiIntegrationSecurityService;
import static com.shale.core.security.ai.AiScope.*;
import com.shale.core.model.ClientType;
import com.shale.server.runtime.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
/** Hash-gated authoritative schema fixture reused by confined SQL/client regressions. */
final class AuthoritativeCaseMcpFixture {
    @SuppressWarnings("unchecked")
    void setup(OAuthAuthorizationFlowTest h)throws Exception {
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
        h.context=new AnnotationConfigWebApplicationContext();h.context.setServletContext(new MockServletContext());h.context.getEnvironment().setActiveProfiles(OAuthAuthorizationFlowTest.PRODUCTION?"azure":"test");
        h.context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("oauth",Map.of("shale.ai.oauth.public-origin",OAuthAuthorizationFlowTest.ORIGIN,"shale.ai.oauth.identity",OAuthAuthorizationFlowTest.PRODUCTION?"PRODUCTION":"TEST","shale.ai.oauth.composition-enabled","true","shale.ai.oauth.authorization-enabled","true","shale.ai.oauth.exchange-enabled","true","shale.ai.oauth.bootstrap.jdbc-url",h.url(h.db),"shale.ai.oauth.bootstrap.username",h.bootstrapLogin,"shale.ai.oauth.bootstrap.password",h.bootstrapSecret)));
        h.context.register(McpOAuthSecurityConfiguration.class,com.shale.server.mcp.McpTransportConfiguration.class,OAuthAuthorizationFlowTest.Fixtures.class);h.context.refresh();
    }

    void actor(OAuthAuthorizationFlowTest h,int user,int tenant)throws Exception {
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

    private static int count(Connection c,String text)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(text)){assertTrue(r.next());return r.getInt(1);}}
}
