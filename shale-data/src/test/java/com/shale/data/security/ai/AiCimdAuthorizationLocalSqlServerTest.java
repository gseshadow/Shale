package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Selected real SQL authorization + production Case routing, including admission verification.
 * Uses the unchanged hash-gated authoritative schema; owner creates only fixture state. */
class AiCimdAuthorizationLocalSqlServerTest extends AiCimdOAuthLocalSqlServerTest {
    @BeforeAll void reviewerIdentity()throws Exception {
        String secret=UUID.randomUUID()+"aB9!";
        try(var c=owner("master")) {
            sql(c,"CREATE LOGIN ["+prefix+"_cimdreview] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");
            sql(c,"GRANT VIEW ANY DEFINITION TO ["+prefix+"_cimdreview]");
        }
        secrets.put("cimdreview",secret);
    }
    @BeforeEach void reviewerRole(TestInfo info)throws Exception {
        try(var c=owner(db)) {
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0; UPDATE dbo.AiCimdProofControlV1 SET Enabled=0; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=0");
            String candidate=Files.readString(Path.of("../docs/sql/ai-integration/universal-pr-a-reviewer.sql"));
            if(info.getTestMethod().orElseThrow().getName().equals("reviewerInstallerRollsBackLatePrivilegeFault")) {
                String fault=candidate.replace("/* PR_A_REVIEWER_FINAL_CHECK */","GRANT SELECT ON dbo.Cases TO AiCimdAuthorizationCatalogV1;\n/* PR_A_REVIEWER_FINAL_CHECK */");
                assertNotEquals(candidate,fault);
                assertThrows(SQLException.class,()->installSql(c,fault));
                assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.database_principals WHERE name='AiCimdAuthorizationCatalogV1'"));
                assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.procedures WHERE name='AiCimdReadAuthorizationReceiptsV1'"));
                assertEquals(historical,receipts(c));assertEquals(immutableBinding,binding(c));
            }
            installSql(c,candidate);
            sql(c,"ALTER ROLE AiCimdAuthorizationCatalogV1 ADD MEMBER ["+prefix+"_cimdreview]");
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=1; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1");
        }
    }
    AiCimdCaseData composition() {
        return new AiCimdCaseData(deadline->attestation(),deadline->source("cimd"),
                deadline->source("cimdreview"),db,ISSUER,RESOURCE);
    }
    void audit(UUID invocation,int tenant,int user,long caseId,int expectedPhi)throws Exception {
        try(var c=owner(db)) {
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE InvocationId='"+invocation+"' AND ShaleClientId="+tenant+" AND UserId="+user+" AND Stage='READ' AND Outcome='ALLOWED_READ'"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource r JOIN dbo.IntegrationAccessAuditLog a ON a.ShaleClientId=r.ShaleClientId AND a.Id=r.EventId WHERE a.InvocationId='"+invocation+"' AND r.Relation='RETURNED' AND r.CaseId="+caseId));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiCimdInvocationV1 WHERE InvocationId='"+invocation+"' AND ReadEventId IS NOT NULL AND HandoffConsumed=1"));
            assertEquals(1,count(c,"SELECT COUNT(*) FROM dbo.AiQuotaReservation WHERE InvocationId='"+invocation+"' AND RowCost=1 AND ReleasedAt IS NOT NULL"));
            assertEquals(expectedPhi,phiCount(c,tenant,user,caseId),"Each returned Overview must commit its own PHI read audit.");
        }
    }
    int phiCount(Connection c,int tenant,int user,long caseId)throws SQLException {
        return count(c,"SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read' AND ShaleClientId="+tenant+" AND UserId="+user+" AND ObjectId="+caseId);
    }
    UUID disclosure(AiCimdCaseData composition,String access,int tenant,int user,long id)throws Exception {
        int expectedPhi;
        try(var c=owner(db)) { expectedPhi=phiCount(c,tenant,user,id)+1; }
        long start=System.nanoTime(),deadline=start+8_000_000_000L;
        UUID invocation;
        try(var request=composition.authenticate(access,deadline)) {
            invocation=request.authority().invocationId();
            var result=composition.reader(request,deadline).overview(
                    IntegrationInvocation.overview(request.authority(),deadline),id,DisclosureBuffer::size);
            assertTrue(StandardCharsets.UTF_8.decode(result.bytes()).toString().contains("\"caseId\":"+id));
            assertTrue(System.nanoTime()<deadline,"Complete verified admission/read/encoding/handoff must fit the original eight seconds.");
        }
        System.err.println("PR_A_COMPLETE_DISCLOSURE_MS="+(System.nanoTime()-start)/1_000_000);
        audit(invocation,tenant,user,id,expectedPhi);
        return invocation;
    }
    @Test void completeOAuthAndAuditedDisclosureWithinOriginalDeadline()throws Exception {
        var first=redeem(consent(7,72));var second=redeem(consent(8,82));
        try(var composition=composition()) {
            disclosure(composition,first.accessToken(),7,72,100);
            disclosure(composition,second.accessToken(),8,82,200);
            var rotated=issuer(7,72).refresh(first.refreshToken(),EXTERNAL,null,null,null,null);
            disclosure(composition,rotated.accessToken(),7,72,100);
            issuer(7,72).revoke(rotated.accessToken(),EXTERNAL,new byte[32],"none",null,null,null,null);
            assertThrows(RuntimeException.class,()->composition.authenticate(rotated.accessToken(),System.nanoTime()+8_000_000_000L));
            assertThrows(RuntimeException.class,()->issuer(7,72).refresh(rotated.refreshToken(),EXTERNAL,null,null,null,null));
            disclosure(composition,second.accessToken(),8,82,200);
        }
        manualRead();
    }
    @Test void revocationAfterReadWithholdsDisclosureAndPreservesOtherTenant()throws Exception {
        var first=redeem(consent(7,72));var second=redeem(consent(8,82));
        try(var composition=composition()) {
            long deadline=System.nanoTime()+8_000_000_000L;
            try(var request=composition.authenticate(first.accessToken(),deadline)) {
                assertThrows(IntegrationReadException.class,()->composition.reader(request,deadline).overview(
                        IntegrationInvocation.overview(request.authority(),deadline),100L,buffer->{
                            new AiTenantApplicationService(provider("tenant",7,71)).decide(approvalA.approvalId(),approvalA.rowVersion(),AiTenantApplicationService.Decision.REVOKE,Set.of(),0);
                            return buffer.size();
                        }));
                try(var c=owner(db)) { assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiCimdInvocationV1 WHERE InvocationId='"+request.authority().invocationId()+"' AND HandoffConsumed=1")); }
            }
            assertThrows(RuntimeException.class,()->issuer(7,72).refresh(first.refreshToken(),EXTERNAL,null,null,null,null));
            disclosure(composition,second.accessToken(),8,82,200);
        }
    }
    @Test void admissionRejectsAdditiveDriftWithoutChangingHistoricalVerification()throws Exception {
        var first=redeem(consent(7,72));
        try(var composition=composition()) {
            try(var c=owner(db)) { sql(c,"GRANT SELECT ON dbo.Cases(Name) TO ["+prefix+"_cimd]"); }
            try { assertThrows(IllegalStateException.class,()->composition.authenticate(first.accessToken(),System.nanoTime()+8_000_000_000L)); }
            finally { try(var c=owner(db)) { sql(c,"REVOKE SELECT ON dbo.Cases(Name) FROM ["+prefix+"_cimd]"); } }
            assertThrows(IllegalStateException.class,()->composition.ready(System.nanoTime()+8_000_000_000L),"Detected drift withdraws readiness until reconstruction.");
        }
        try(var composition=composition()) { disclosure(composition,first.accessToken(),7,72,100); }
    }
    @Test void metadataAccessorAndOffControlsCannotGrantCaseOrCredentialAuthority()throws Exception {
        var first=redeem(consent(7,72));
        try(var review=source("cimdreview").getConnection()) {
            for(String table:List.of("Cases","AiCimdCredentialV1","AiCimdProofReceiptV1","AiCaseDataDeployment"))
                assertEquals(229,assertThrows(SQLException.class,()->sql(review,"SELECT * FROM dbo."+table)).getErrorCode());
            assertDoesNotThrow(()->sql(review,"EXEC dbo.AiCimdReadAuthorizationReceiptsV1"));
        }
        try(var caller=source("cimd").getConnection()) {
            assertEquals(229,assertThrows(SQLException.class,()->sql(caller,"EXEC dbo.AiCimdReadAuthorizationReceiptsV1")).getErrorCode());
        }
        try(var composition=composition()) {
            try(var c=owner(db)) { sql(c,"UPDATE dbo.AiCimdProofControlV1 SET Enabled=0; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=0"); }
            assertThrows(RuntimeException.class,()->composition.authenticate(first.accessToken(),System.nanoTime()+8_000_000_000L));
            assertThrows(RuntimeException.class,()->issuer(7,72).refresh(first.refreshToken(),EXTERNAL,null,null,null,null));
        }
    }
    @Test void reviewerInstallerRollsBackLatePrivilegeFault()throws Exception {
        var first=redeem(consent(7,72));
        try(var composition=composition()) { disclosure(composition,first.accessToken(),7,72,100); }
        manualRead();
    }
}
