package com.shale.server.oauth;

import com.shale.core.security.ai.AiScope;
import com.shale.data.security.ai.AiIntegrationSecurityService;
import com.shale.server.mcp.McpConfinedClientFixture;
import com.shale.server.runtime.ServerPrincipal;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import static org.junit.jupiter.api.Assertions.*;

/** Shared finished-server corpus, maintained OAuth issuance, actual restricted AI login.
 * Fixture controls travel over child stdin/stdout, never a production endpoint or MCP tool.
 * No quota/history reset: each vector uses a fresh registration and rotating synthetic owner. */
class Phase3b7AcceptanceLocalSqlServerTest {
    private final tools.jackson.databind.ObjectMapper mapper=new tools.jackson.databind.ObjectMapper();
    static java.util.stream.Stream<String> clients() {
        String client=System.getProperty("shale.acceptance.client");
        if(client==null)return java.util.stream.Stream.of("ts","python");
        if(!Set.of("ts","python").contains(client))throw new IllegalArgumentException("Unknown independent client selection");
        return java.util.stream.Stream.of(client);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("clients") @Timeout(240)
    void identicalFinishedCorpus(String kind,@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {
        finishedChecks(kind,temp,false);
    }
    @Test @Timeout(180)
    void finishedConformanceTlsAndPerformance(@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {
        finishedChecks("protocol",temp,true);
    }
    @Test @Timeout(90)
    void finishedResponseBoundaries(@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {finishedChecks("boundaries",temp,true);}
    @Test @Timeout(180)
    void finishedModernConformance(@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {finishedChecks("modern",temp,true);}
    @Test @Timeout(300)
    void finishedFrozenConformance(@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {
        finishedChecks("frozen",temp,true);
    }
    @Test @Timeout(180)
    void productionTlsProxy(@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {
        Assumptions.assumeTrue(OAuthAuthorizationFlowTest.PRODUCTION);finishedChecks("tls",temp,true);
    }
    @Test @Timeout(180)
    void productionCatalogPublicGrantDeniesServing(@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {
        Assumptions.assumeTrue(OAuthAuthorizationFlowTest.PRODUCTION);finishedChecks("catalog-public",temp,true);
    }
    private void finishedChecks(String kind,Path temp,boolean protocol)throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_AI".equals(System.getenv("SHALE_AI_SQL_OPT_IN"))&&System.getenv("SHALE_CASE_VERIFY_SCHEMA")!=null);
        var h=new OAuthAuthorizationFlowTest();String ai=null,catalog=null;
        String aiSecret=UUID.randomUUID()+"Aa9!",catalogSecret=UUID.randomUUID()+"Aa9!";
        try {
            new AuthoritativeCaseMcpFixture().setup(h);ai=h.db+"_accept_ai";catalog=h.db+"_accept_catalog";
            try(var c=h.owner("master")){sql(c,"CREATE LOGIN ["+ai+"] WITH PASSWORD='"+aiSecret+"',CHECK_POLICY=OFF; CREATE LOGIN ["+catalog+"] WITH PASSWORD='"+catalogSecret+"',CHECK_POLICY=OFF; GRANT VIEW ANY DEFINITION TO ["+catalog+"]");}
            try(var c=h.owner(h.db)) {
                sql(c,"DROP TRIGGER dbo.LocalPhiFault");sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b7-confinement.sql")));
                sql(c,"CREATE USER ["+ai+"] FOR LOGIN ["+ai+"]; ALTER ROLE AiCaseDataRuntime ADD MEMBER ["+ai+"]; CREATE USER ["+catalog+"] FOR LOGIN ["+catalog+"]; ALTER ROLE AiCaseDataCatalogVerifier ADD MEMBER ["+catalog+"]");
                sql(c,"INSERT dbo.AiCaseDataDeployment VALUES(1,N'"+OAuthAuthorizationFlowTest.ISSUER+"',N'"+OAuthAuthorizationFlowTest.RESOURCE+"','case-read-v1','"+java.time.ZoneId.systemDefault().getId()+"','UTC')");
                sql(c,"SET IDENTITY_INSERT dbo.Cases ON; INSERT dbo.Cases(Id,ShaleClientId,Name,CaseNumber,IsDeleted,ClientEstate,FollowUpMeetWithClient,FollowUpNurseReview,FollowUpExpertReview,FollowUpCaseTransferred,AcceptedChronology,AcceptedConsultantExpertSearch,AcceptedTestifyingExpertSearch,AcceptedMedicalLiterature,DeniedChronology,FeeAgreementSigned,MedicalRecordsRequested,CreatedAt,UpdatedAt) VALUES(9006,7,N'É漢𐐀 unicode',N'NUMBER',0,0,0,0,0,0,0,0,0,0,0,0,0,SYSUTCDATETIME(),SYSUTCDATETIME()); SET IDENTITY_INSERT dbo.Cases OFF");
            }
            var faults=new com.shale.server.mcp.Phase3b7FaultSource(source(h,ai,aiSecret),()->h.owner(h.db));
            var controls=new Controls(h,faults);
            McpConfinedClientFixture.withServer(source(h,ai,aiSecret), source(h,catalog,catalogSecret),
                    List.of(source(h,h.login,h.secret), source(h,h.bootstrapLogin,h.bootstrapSecret),
                            source(h,"sa",h.password)), h.db, temp, faults::overPool, url -> {
                if (protocol && kind.equals("catalog-public")) {
                    com.shale.server.mcp.McpCatalogVerifierServingChecks.run(url,controls.prepare("catalog-public","prepare"),()->h.owner(h.db));
                } else if (protocol && kind.equals("tls")) {
                    com.shale.server.mcp.Phase3b7ServingChecks.tls(url,controls.prepare("tls","prepare"),temp);
                } else if (protocol && kind.equals("boundaries")) {
                    com.shale.server.mcp.Phase3b7BoundaryServingChecks.run(url,
                            controls.prepare("boundary","prepare"), () -> h.owner(h.db), () -> {
                        try { return OAuthAuthorizationFlowTest.runtime.openConnection(new ServerPrincipal(72,7,null)); }
                        catch (SQLException e) { throw new IllegalStateException(e); }
                    });
                } else if (protocol && kind.equals("frozen")) {
                    com.shale.server.mcp.Phase3b7ServingChecks.frozen(url,
                            controls.prepare("conformance","prepare"));
                } else if (protocol && kind.equals("modern")) {
                    com.shale.server.mcp.Phase3b7ServingChecks.modern(url,
                            controls.prepare("conformance","prepare"));
                } else if (protocol) {
                    com.shale.server.mcp.Phase3b7ServingChecks.run(url,
                            controls.prepare("performance","prepare"), controls.prepare("conformance","prepare"),
                            temp, () -> h.owner(h.db));
                } else {
                    runClient(kind,url,controls);
                    controls.validatePrevious();
                    Files.writeString(Path.of("../work/phase3b7/serving-faults-"+kind+".json"),
                            mapper.writeValueAsString(Map.of("readCalls",faults.calls(),
                                    "positiveSqlWaits",faults.waits()))+"\n");
                }
            });
            // Inspect every persisted value; schema and typed historical IDs remain the audit authority.
            try(var c=h.owner(h.db)) {
                for(String table:List.of("IntegrationAccessAuditLog","IntegrationAccessAuditResource"))try(var st=c.createStatement();var r=st.executeQuery("SELECT * FROM dbo."+table)) {
                    for(int n=1;n<=r.getMetaData().getColumnCount();n++)assertFalse(Set.of("query","casename","casenumber","bearer","proofdigest","oauthassertion","jsonrpcid","exceptiontext","sqltext","metadata").contains(r.getMetaData().getColumnName(n).toLowerCase(Locale.ROOT)),"Audit projection must have no payload or arbitrary metadata column");
                    while(r.next())for(int n=1;n<=r.getMetaData().getColumnCount();n++) {
                        String value=String.valueOf(r.getObject(n));
                        for(String sentinel:List.of("MCP literal","Direct overview","NUMBER","É漢𐐀","Bearer ","ProofDigest","acceptance","SELECT ","exception text","arbitrary metadata","OAuth.assertion","JSON-RPC-ID","Synthetic serving fault"))assertFalse(value.contains(sentinel),"Persisted audit field must not contain protected/request payload");
                        for(String secret:controls.secrets)assertFalse(value.contains(secret),"Credential must not appear in persisted audit");
                    }
                }
            }
        } finally {
            try{h.cleanup();}finally{if(ai!=null)try(var c=h.owner("master")){sql(c,"DROP LOGIN ["+ai+"]; DROP LOGIN ["+catalog+"]");}}
        }
    }
    private void runClient(String kind,String url,Controls controls)throws Exception {
        var command=kind.equals("ts")?List.of("node","../build/mcp-transport/acceptance-ts.mjs"):List.of("../work/mcp-clients/bin/python","../build/mcp-transport/acceptance-python.py");
        var builder=new ProcessBuilder(command).redirectErrorStream(true);builder.environment().put("MCP_URL",url);String group=System.getProperty("shale.acceptance.group","all");builder.environment().put("MCP_ACCEPTANCE_GROUP",group);
        var process=builder.start();String complete=null;var transcript=new StringBuilder();
        try(var input=new BufferedReader(new InputStreamReader(process.getInputStream(),StandardCharsets.UTF_8));var output=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8))) {
            String line;while((line=input.readLine())!=null) {
                if(line.startsWith("{")) {
                    var message=mapper.readTree(line);
                    if(message.has("control")) {String token=controls.prepare(message.path("id").asText(),message.path("control").asText());output.write(mapper.writeValueAsString(Map.of("token",token)));output.newLine();output.flush();continue;}
                    if(message.path("complete").asBoolean())complete=line;
                }
                transcript.append(line).append('\n');
                String progress=transcript.toString();for(String secret:controls.secrets)progress=progress.replace(secret,"[REDACTED]");
                Files.writeString(Path.of("../work/phase3b7/resumed-client-"+kind+".log"),progress);
            }
            assertTrue(process.waitFor(10,TimeUnit.SECONDS),"Independent corpus process must terminate");
            String safe=transcript.toString();for(String secret:controls.secrets)safe=safe.replace(secret,"[REDACTED]");
            Files.createDirectories(Path.of("../work/phase3b7"));Files.writeString(Path.of("../work/phase3b7/resumed-client-"+kind+".log"),safe);
            assertEquals(0,process.exitValue(),"Independent corpus failed; inspect sanitized acceptance transcript");assertNotNull(complete,"Every vector must complete");
            var result=mapper.readTree(complete);int expected=0;for(var v:mapper.readTree(Files.readString(Path.of("../build/mcp-transport/acceptance-corpus.json"))).path("vectors"))if(group.equals("all")||group.equals("faults")== (v.path("action").asText().equals("fault")||v.path("id").asText().equals("reuse-after-failures")))expected++;assertEquals(expected,result.path("records").size());
            Files.writeString(Path.of("../work/phase3b7/resumed-client-"+kind+".json"),complete+"\n");
        } finally {if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
    }
    private static final class Controls {
        final OAuthAuthorizationFlowTest h;final List<String> secrets=new ArrayList<>();int sequence;
        final com.shale.server.mcp.Phase3b7FaultSource faults;String previous="";int allowedBefore,phiBefore;
        AiIntegrationSecurityService.Registration registration;
        Controls(OAuthAuthorizationFlowTest h,com.shale.server.mcp.Phase3b7FaultSource faults){this.h=h;this.faults=faults;}
        void validatePrevious()throws Exception {
            if(previous.isEmpty())return;
            boolean committed=Set.of("lost-ack","close","interruption","cleanup").contains(previous);
            try(var c=h.owner(h.db)){assertEquals(allowedBefore+(committed?1:0),count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'"),previous+" committed history");assertEquals(phiBefore+(committed?1:0),count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read'"),previous+" PHI atomicity");}
            assertEquals(1,faults.calls().get(previous),"No automatic protected reread for "+previous);
            assertEquals(0,faults.calls().getOrDefault(previous+":handoff",0),"No authorized protected handoff under fault");
            if(Set.of("commit","cancel-during").contains(previous))assertEquals(1,faults.waits().get(previous),"Actual SQL wait must be observed");
            previous="";
        }
        AiIntegrationSecurityService admin(){return new AiIntegrationSecurityService(()->{try{return OAuthAuthorizationFlowTest.runtime.openConnection(new ServerPrincipal(71,7,null));}catch(SQLException e){throw new IllegalStateException(e);}},true,OAuthAuthorizationFlowTest.ISSUER,OAuthAuthorizationFlowTest.RESOURCE);}
        String prepare(String id,String stage)throws Exception {
            if(stage.equals("reduce")){admin().changeIntegration(registration.integrationId(),AiScope.fromMask(1),false,registration.rowVersion());return "ack";}
            validatePrevious();faults.mode("");
            try(var c=h.owner(h.db)){sql(c,"UPDATE dbo.Users SET is_deleted=0 WHERE ShaleClientId=7 AND is_deleted=1");}
            var policy=admin().inspectTenantPolicy();if(!policy.enabled())admin().setTenantEnabled(true,policy.rowVersion());
            registration=admin().register("Synthetic corpus "+sequence,false,AiScope.fromMask(3),1,List.of(URI.create(OAuthAuthorizationFlowTest.REDIRECT)));h.client=registration.clientId();
            new AuthoritativeCaseMcpFixture().actor(h,72+(sequence++%7),7);
            String scope=id.equals("catalog-search")?"cases.search":id.equals("catalog-overview")?"cases.overview.read":"cases.search cases.overview.read";
            String token=h.freshAccess(scope);secrets.add(token);
            if(id.startsWith("fault-")){previous=id.substring(6);try(var c=h.owner(h.db)){allowedBefore=count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'");phiBefore=count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read'");}faults.mode(previous);return token;}
            if(id.equals("auth-random"))return "a".repeat(43);
            if(id.equals("auth-web")){secrets.add(h.webToken);return h.webToken;}
            if(id.equals("auth-code")){String code=h.freshCode();secrets.add(code);return code;}
            if(id.equals("auth-refresh")) {var response=h.send(h.exchangeRequest(h.freshCode()));assertEquals(200,response.getStatus());String refresh=new tools.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).path("refresh_token").asText();secrets.add(refresh);return refresh;}
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));UUID session;
            try(var c=h.owner(h.db);var p=c.prepareStatement("SELECT SessionId FROM dbo.AiOAuthCredential WHERE CredentialHash=?")){p.setBytes(1,digest);try(var r=p.executeQuery()){assertTrue(r.next());session=UUID.fromString(r.getString(1));}}finally{Arrays.fill(digest,(byte)0);}
            try(var c=h.owner(h.db)) {
                if(id.equals("auth-revoked")||id.equals("live-credential-revoke"))sql(c,"UPDATE dbo.AiOAuthCredential SET RevokedAt=SYSUTCDATETIME() WHERE Kind='ACCESS' AND SessionId='"+session+"'");
                if(id.equals("auth-expired")||id.equals("live-credential-expiry")) {
                    // Owner-only expiry injection; restore the unchanged immutable trigger before serving.
                    sql(c,"DISABLE TRIGGER dbo.AiImmutable_AiOAuthCredential ON dbo.AiOAuthCredential");
                    try{sql(c,"DECLARE @now datetime2(7)=SYSUTCDATETIME(); UPDATE dbo.AiOAuthCredential SET IssuedAt=DATEADD(minute,-6,@now),ExpiresAt=DATEADD(minute,-1,@now) WHERE Kind='ACCESS' AND SessionId='"+session+"'");}finally{sql(c,"ENABLE TRIGGER dbo.AiImmutable_AiOAuthCredential ON dbo.AiOAuthCredential");}
                }
                if(id.startsWith("auth-wrong-")) {
                    String assignment=switch(id){case "auth-wrong-issuer"->"Issuer=N'https://wrong.invalid/oauth/mcp'";case "auth-wrong-resource"->"Resource=N'https://wrong.invalid/mcp'";default->"ClientId='wrong-client'";};
                    sql(c,"DISABLE TRIGGER dbo.AiImmutable_AiDelegatedSession,dbo.AiLifecycle_Session ON dbo.AiDelegatedSession");
                    try{sql(c,"UPDATE dbo.AiDelegatedSession SET "+assignment+" WHERE SessionId='"+session+"'");}finally{sql(c,"ENABLE TRIGGER dbo.AiImmutable_AiDelegatedSession,dbo.AiLifecycle_Session ON dbo.AiDelegatedSession");}
                }
                if(id.equals("catalog-neither")||id.equals("live-grant-revoke"))sql(c,"UPDATE g SET RevokedAt=SYSUTCDATETIME(),RevokedByUserId=71,RevocationReason='ADMIN',Revision=Revision+1 FROM dbo.AiDelegationGrant g JOIN dbo.AiDelegatedSession s ON s.GrantId=g.GrantId WHERE s.SessionId='"+session+"'");
                if(id.equals("live-session-revoke"))sql(c,"UPDATE dbo.AiDelegatedSession SET RevokedAt=SYSUTCDATETIME(),RevocationReason='SELF' WHERE SessionId='"+session+"'");
                if(id.equals("live-user-disable"))sql(c,"UPDATE u SET is_deleted=1 FROM dbo.Users u JOIN dbo.AiDelegatedSession s ON s.UserId=u.id AND s.ShaleClientId=u.ShaleClientId WHERE s.SessionId='"+session+"'");
            }
            if(id.equals("live-tenant-off"))admin().setTenantEnabled(false,admin().inspectTenantPolicy().rowVersion());
            if(id.equals("live-integration-revoke"))admin().changeIntegration(registration.integrationId(),Set.of(),true,registration.rowVersion());
            return token;
        }
    }
    private static javax.sql.DataSource source(OAuthAuthorizationFlowTest h,String user,String password){var s=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();s.setURL(h.url(h.db));s.setUser(user);s.setPassword(password);return s;}
    private static int count(Connection c,String text)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(text)){assertTrue(r.next());return r.getInt(1);}}
    private static void sql(Connection c,String text)throws SQLException{OAuthAuthorizationFlowTest.sql(c,text);}
}
