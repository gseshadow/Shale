package com.shale.server.oauth;

import com.shale.server.ShaleServerApplication;
import com.shale.server.mcp.McpCaseReadiness;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.core.env.MapPropertySource;
import static org.junit.jupiter.api.Assertions.*;

/** Full production Boot and production-created pools: real TLS, authoritative schema, OFF controls.
 * Owner connections only provision/inject faults in the nonce-owned disposable database. */
class McpProductionReadinessLocalSqlServerTest {
    @Test @Timeout(180)
    void freshReadinessAttestsRestrictedIdentitiesWithoutActivatingAnyDisclosure()throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_AI".equals(System.getenv("SHALE_AI_SQL_OPT_IN"))&&OAuthAuthorizationFlowTest.PRODUCTION);
        var h=new OAuthAuthorizationFlowTest();String ai=null,catalog=null,crossDatabase=null;
        String aiSecret=UUID.randomUUID()+"Aa9!",catalogSecret=UUID.randomUUID()+"Aa9!";
        var previous=new LinkedHashMap<String,String>();
        try {
            new AuthoritativeCaseMcpFixture().setup(h);h.context.close();h.context=null;
            ai=h.db+"_readiness_ai";catalog=h.db+"_readiness_catalog";
            try(var c=h.owner("master")){sql(c,"CREATE LOGIN ["+ai+"] WITH PASSWORD='"+aiSecret+"',CHECK_POLICY=OFF; CREATE LOGIN ["+catalog+"] WITH PASSWORD='"+catalogSecret+"',CHECK_POLICY=OFF; GRANT VIEW ANY DEFINITION TO ["+catalog+"]");}
            try(var c=h.owner(h.db)) {
                sql(c,"DROP TRIGGER dbo.LocalPhiFault");sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b7-confinement.sql")));
                sql(c,"CREATE USER ["+ai+"] FOR LOGIN ["+ai+"]; ALTER ROLE AiCaseDataRuntime ADD MEMBER ["+ai+"]; CREATE USER ["+catalog+"] FOR LOGIN ["+catalog+"]; ALTER ROLE AiCaseDataCatalogVerifier ADD MEMBER ["+catalog+"]");
                sql(c,"INSERT dbo.AiCaseDataDeployment VALUES(1,N'"+OAuthAuthorizationFlowTest.ISSUER+"',N'"+OAuthAuthorizationFlowTest.RESOURCE+"','case-read-v1','"+java.time.ZoneId.systemDefault().getId()+"','UTC')");
                sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0; UPDATE dbo.TenantAiIntegrationPolicy SET Enabled=0");
                for(String batch:Files.readString(Path.of("../docs/sql/2026-10-14_pretenant_authentication_modules.sql")).split("(?im)^GO\s*$"))if(!batch.isBlank())sql(c,batch);
                sql(c,"GRANT EXECUTE ON dbo.LookupAuthenticationCredentialV1 TO ["+h.login+"]; GRANT EXECUTE ON dbo.ResolveDesktopRememberCredentialV2 TO ["+h.login+"]");
                for(String table:List.of("UserSessions","DesktopRememberCredentials"))sql(c,"GRANT SELECT,INSERT,UPDATE,DELETE ON dbo."+table+" TO ["+h.login+"]");
                sql(c,"GRANT SELECT ON dbo.ApplicationInstances TO ["+h.login+"]");
                try(var p=c.prepareStatement("UPDATE dbo.Users SET password_hash=? WHERE Id=72")){p.setString(1,new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("synthetic-password"));p.executeUpdate();}
            }
            crossDatabase=h.db+"_cross_database_probe";
            try(var c=h.owner("master")){sql(c,"CREATE DATABASE ["+crossDatabase+"]");}
            try(var c=h.owner(crossDatabase)){sql(c,"CREATE TABLE dbo.AuthorityProbe(Marker int NOT NULL); INSERT dbo.AuthorityProbe VALUES(1)");}
            String url="jdbc:sqlserver://localhost:"+h.port+";databaseName="+h.db;
            Map<String,String> ordinary=Map.of("SHALE_APP_DB_URL",url,"SHALE_APP_DB_USER",h.login,"SHALE_APP_DB_PASSWORD",h.secret,
                    "SHALE_RT_DB_URL",url,"SHALE_RT_DB_USER",h.login,"SHALE_RT_DB_PASSWORD",h.secret,
                    "SHALE_AUTH_TOKEN_SECRET","synthetic-boot-test-secret-at-least-thirty-two-characters","SHALE_AUTH_SESSION_BINDING_CUTOVER_AT","2026-09-29T18:00:00Z");
            ordinary.forEach((key,value)->{assertNull(System.getenv(key),"Inherited ordinary database configuration forbidden");previous.put(key,System.getProperty(key));System.setProperty(key,value);});
            // Verify the fixture certificate and hostname before composing production pools.
            try(var c=DriverManager.getConnection(url+";encrypt=true;trustServerCertificate=false",ai,aiSecret)){assertTrue(c.isValid(2));}
            var properties=new HashMap<String,Object>();
            properties.put("shale.ai.oauth.public-origin",OAuthAuthorizationFlowTest.ORIGIN);properties.put("shale.ai.oauth.identity","PRODUCTION");
            properties.put("shale.ai.mcp.case-data-composition-enabled",true);
            for(String flag:List.of("shale.ai.enabled","shale.ai.mcp.enabled","shale.ai.mcp.search-cases-enabled","shale.ai.mcp.get-case-overview-enabled","shale.ai.oauth.composition-enabled","shale.ai.oauth.authorization-enabled","shale.ai.oauth.exchange-enabled"))properties.put(flag,false);
            properties.put("shale.ai.data.jdbc-url",url);properties.put("shale.ai.data.username",ai);properties.put("shale.ai.data.password",aiSecret);properties.put("shale.ai.data.database",h.db);
            properties.put("shale.ai.data.verifier.jdbc-url",url);properties.put("shale.ai.data.verifier.username",catalog);properties.put("shale.ai.data.verifier.password",catalogSecret);
            properties.put("shale.ai.oauth.bootstrap.jdbc-url",url);properties.put("shale.ai.oauth.bootstrap.username",h.bootstrapLogin);properties.put("shale.ai.oauth.bootstrap.password",h.bootstrapSecret);
            properties.put("shale.ai.mcp.case-data-composition-enabled",false);
            int offChecks;
            try(var off=new SpringApplicationBuilder(ShaleServerApplication.class).profiles("azure").initializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-production-off",properties))).run("--server.port=0","--server.address=127.0.0.1")) {
                assertEquals("disabled",off.getBean(McpCaseReadiness.class).state());
                offChecks=ProductionOrdinarySmoke.run(((WebServerApplicationContext)off).getWebServer().getPort());
            }
            properties.put("shale.ai.mcp.case-data-composition-enabled",true);
            try(var context=new SpringApplicationBuilder(ShaleServerApplication.class).profiles("azure").initializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-production-readiness",properties))).run("--server.port=0","--server.address=127.0.0.1")) {
                assertEquals(List.of("azure"),List.of(context.getEnvironment().getActiveProfiles()));
                var readiness=context.getBean(McpCaseReadiness.class);assertEquals("ready",readiness.state());
                for(String bean:List.of("mcpServlet","mcpSearchHandler","mcpOverviewHandler","oauthCodeBootstrap","mcpAccessAuthenticator"))assertFalse(context.containsBean(bean),bean);
                // Effective denial tests use the actual dedicated AI SQL login, not owner/EXECUTE AS.
                try(var c=DriverManager.getConnection(url+";encrypt=true;trustServerCertificate=false",ai,aiSecret)) {
                    for(String table:List.of("Cases","Users","auditLog","AiOAuthCredential","AiDelegatedSession","AiProductCapability"))denied(c,"SELECT * FROM dbo."+table);
                    sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=8; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=82");
                    denied(c,"SELECT Name FROM dbo.Cases WHERE Id=9001");
                    assertEquals(0,count(c,"SELECT HAS_DBACCESS('"+crossDatabase+"')"));
                    String crossRead="SELECT Marker FROM ["+crossDatabase+"].dbo.AuthorityProbe";
                    assertEquals(916,assertThrows(SQLException.class,()->sql(c,crossRead)).getErrorCode(),"Actual cross-database read must be denied");
                    denied(c,"INSERT dbo.auditLog(ShaleClientId,userId,objectTypeId,objectId,fieldName,fieldCode,stringValue) VALUES(8,82,1,9001,'Case.Overview.Read',4,'action=READ;screen=Case.Overview')");
                    var names=new TreeSet<String>();try(var files=Files.list(Path.of("../docs/sql/ai-integration"))){for(Path path:files.filter(p->p.toString().endsWith(".sql")&&!p.getFileName().toString().contains("confinement")).toList()){var matcher=java.util.regex.Pattern.compile("GRANT EXECUTE ON OBJECT::dbo\\.(\\w+) TO (?:AiIntegrationRuntime|AiIntegrationBootstrap|AiCapabilityOperator)").matcher(Files.readString(path));while(matcher.find())names.add(matcher.group(1));}}
                    assertEquals(42,names.size());for(String name:names)denied(c,"EXEC dbo."+name);
                    for(String statement:List.of("EXECUTE AS USER='dbo'","EXECUTE AS LOGIN='sa'","CREATE TABLE dbo.AiEscape(Id int)","ALTER SECURITY POLICY sec.TenantFilter WITH(STATE=OFF)","ALTER ROLE db_datareader ADD MEMBER ["+ai+"]","CREATE DATABASE AiEscape"))assertThrows(SQLException.class,()->sql(c,statement),statement);
                }
                try(var c=DriverManager.getConnection(url+";encrypt=true;trustServerCertificate=false",catalog,catalogSecret)) {
                    for(String table:List.of("Cases","Users","auditLog","AiOAuthCredential","AiDelegatedSession","AiProductCapability"))denied(c,"SELECT * FROM dbo."+table);
                    denied(c,"EXEC dbo.AiGetTenantPolicyV1");assertThrows(SQLException.class,()->sql(c,"EXECUTE AS USER='dbo'"));
                }
                var aiSource=source(url,ai,aiSecret);var catalogSource=source(url,catalog,catalogSecret);
                var ordinarySource=source(url,h.login,h.secret);var bootstrapSource=source(url,h.bootstrapLogin,h.bootstrapSecret);
                var forbidden=List.of(ordinarySource,ordinarySource,bootstrapSource);
                assertThrows(IllegalStateException.class,()->new com.shale.data.security.ai.AiCaseDataAttestation(aiSource,catalogSource,forbidden,"wrong_database",OAuthAuthorizationFlowTest.ISSUER,OAuthAuthorizationFlowTest.RESOURCE,true).verify());
                assertThrows(IllegalStateException.class,()->new com.shale.data.security.ai.AiCaseDataAttestation(aiSource,catalogSource,forbidden,h.db,"https://wrong.example/oauth/mcp",OAuthAuthorizationFlowTest.RESOURCE,true).verify());
                assertThrows(IllegalStateException.class,()->new com.shale.data.security.ai.AiCaseDataAttestation(aiSource,catalogSource,List.of(aiSource,ordinarySource,bootstrapSource),h.db,OAuthAuthorizationFlowTest.ISSUER,OAuthAuthorizationFlowTest.RESOURCE,true).verify());
                assertThrows(IllegalStateException.class,()->new com.shale.data.security.ai.AiCaseDataAttestation(aiSource,catalogSource,List.of(ordinarySource,ordinarySource,catalogSource),h.db,OAuthAuthorizationFlowTest.ISSUER,OAuthAuthorizationFlowTest.RESOURCE,true).verify());
                try(var c=h.owner(h.db)){sql(c,"GRANT SELECT ON dbo.Cases(Name) TO ["+catalog+"]");}
                assertEquals("not-ready",readiness.state(),"Catalog verifier must lose readiness on excess privilege");
                try(var c=h.owner(h.db)){sql(c,"REVOKE SELECT ON dbo.Cases(Name) FROM ["+catalog+"]");}
                assertEquals("ready",readiness.state());
                try(var c=h.owner(h.db)){sql(c,"GRANT SELECT ON dbo.Cases TO public");}
                try {
                    try(var c=catalogSource.getConnection()) {
                        sql(c,"EXEC sys.sp_set_session_context @key=N'ShaleClientId',@value=7; EXEC sys.sp_set_session_context @key=N'PrincipalUserId',@value=72");
                        assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.Cases WHERE Id=100"),"Fault injection must create real excess verifier authority");
                    }
                    assertEquals("not-ready",readiness.state(),"Catalog verifier must reject inherited public Case read authority");
                } finally {try(var c=h.owner(h.db)){sql(c,"REVOKE SELECT ON dbo.Cases FROM public");}}
                assertEquals("ready",readiness.state());
                for(String[] fault:com.shale.server.mcp.McpCatalogVerifierServingChecks.authorityFaults(ai,catalog)) {
                    try(var c=h.owner(h.db)){sql(c,fault[0]);}
                    try{assertEquals("not-ready",readiness.state(),fault[0]);}
                    finally{try(var c=h.owner(h.db)){sql(c,fault[1]);}}
                    assertEquals("ready",readiness.state(),"Each removed privilege fault requires fresh attestation");
                }
                // Live changes invalidate the previous success; restoration requires a new attestation.
                try(var c=h.owner(h.db)){sql(c,"GRANT SELECT ON dbo.Cases(Name) TO ["+ai+"]");}
                assertEquals("not-ready",readiness.state());
                try(var c=h.owner(h.db)){sql(c,"REVOKE SELECT ON dbo.Cases(Name) FROM ["+ai+"]");}
                assertEquals("ready",readiness.state());
                int port=((WebServerApplicationContext)context).getWebServer().getPort();
                try(var client=HttpClient.newHttpClient()) {
                    for(String path:List.of("/mcp","/oauth/mcp/token","/oauth/mcp/authorize")){var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());assertEquals(404,response.statusCode());assertFalse(response.body().contains("access_token"));}
                    var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/health")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,response.statusCode());
                }
                try(var c=h.owner(h.db)) {
                    assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiProductCapability WHERE Enabled=1"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.TenantAiIntegrationPolicy WHERE Enabled=1"));
                    assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiOAuthCredential"));
                }
                int readyChecks=ProductionOrdinarySmoke.run(port);
                Files.createDirectories(Path.of("../work/phase3b7"));
                Files.writeString(Path.of("../work/phase3b7/production-ordinary.json"),new tools.jackson.databind.ObjectMapper().writeValueAsString(Map.of("offHttpChecks",offChecks,"readinessHttpChecks",readyChecks,"realBoot",true,"realSql",true))+"\n");
            }
        } finally {
            previous.forEach((key,value)->{if(value==null)System.clearProperty(key);else System.setProperty(key,value);});
            try{h.cleanup();}finally{try(var c=h.owner("master")) {
                if(crossDatabase!=null)sql(c,"DROP DATABASE ["+crossDatabase+"]");
                if(ai!=null)sql(c,"DROP LOGIN ["+ai+"]; DROP LOGIN ["+catalog+"]");
            }}
        }
    }
    private static javax.sql.DataSource source(String url,String user,String password) {
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();source.setURL(url);source.setUser(user);source.setPassword(password);source.setEncrypt("true");source.setTrustServerCertificate(false);return source;
    }
    private static void denied(Connection c,String text){assertEquals(229,assertThrows(SQLException.class,()->sql(c,text),text).getErrorCode(),text);}
    private static int count(Connection c,String text)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(text)){assertTrue(r.next());return r.getInt(1);}}
    private static void sql(Connection c,String text)throws SQLException{OAuthAuthorizationFlowTest.sql(c,text);}
}
