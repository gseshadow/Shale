package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Select c2* only. Real authoritative C2 successor, restricted capabilities and shared admission. */
class AiDcrEndpointLocalSqlServerTest extends AiDcrApplicationLocalSqlServerTest {
    boolean successor;
    @BeforeAll void endpointIdentity()throws Exception {
        String secret=UUID.randomUUID()+"aB9!";
        try(var c=owner("master")){sql(c,"CREATE LOGIN ["+prefix+"_v3endpoint] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");}
        secrets.put("v3endpoint",secret);
    }
    @Override @BeforeEach void externalSuccessor()throws Exception {
        successor=false;super.externalSuccessor();
        try(var c=owner(db)) {
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0; UPDATE dbo.AiCimdProofControlV1 SET Enabled=0; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=0; UPDATE dbo.AiExternalControlV2 SET Enabled=0");
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr-c2-dcr-v3.sql")));
            assertEquals(0,count(c,"SELECT CONVERT(int,Enabled) FROM dbo.AiDcrControlV3"));
            sql(c,"ALTER ROLE AiDcrEndpointV3 ADD MEMBER ["+prefix+"_v3endpoint]");
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=1; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=1; UPDATE dbo.AiExternalControlV2 SET Enabled=1; UPDATE dbo.AiDcrControlV3 SET Enabled=1");
        }
        successor=true;externalAttestation().verify();
    }
    @Override AiCaseDataAttestation externalAttestation(){return successor?new AiCaseDataAttestation(source("ai"),source("catalog"),List.of(source("runtime"),source("bootstrap")),db,ISSUER,RESOURCE,false,contract(),AiExternalContract.DCR_V3):super.externalAttestation();}
    @Override AiDcrApplicationStore dcr(String role){return successor?new AiDcrApplicationStore(()->{try{return connection(role.equals("v2registrar")?"v3endpoint":role,null,null);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}},ISSUER,RESOURCE,AiExternalContract.DCR_V3):super.dcr(role);}
    @Override AiCimdOAuthStore external(String role,Integer tenant,Integer user){return new AiCimdOAuthStore(()->{try{return connection(role,tenant,user);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}},ISSUER,RESOURCE,AiExternalContract.DCR_V3);}
    @Override AiCimdCaseData externalCases(){return new AiCimdCaseData(deadline->externalAttestation(),deadline->source("v2case"),deadline->source("cimdreview"),db,ISSUER,RESOURCE,AiExternalContract.DCR_V3);}
    AiDcrAdmissionStore admission(){return new AiDcrAdmissionStore(()->{try{return connection("v3endpoint",null,null);}catch(java.sql.SQLException e){throw new IllegalStateException(e);}});}
    @Test void c2TwoTenantAuthorityAndLegacyIsolation()throws Exception {dcrNeutralCreationTwoTenantConsentAndCredentialSeparation();}
    @Test void c2RefreshRevocationAndRevision()throws Exception {dcrTenantRevocationAndApplicationRevisionInvalidateOnlyTheirAuthority();}
    @Test void c2AuditRollbackAndHistoricalVerification()throws Exception {
        try(var c=owner(db)){sql(c,"EXEC(N'CREATE TRIGGER dbo.C2FailGlobalAudit ON dbo.GlobalControlPlaneAuditLog AFTER INSERT AS THROW 59790,''Synthetic C2 audit failure'',1;')");}
        var failed=assertThrows(IllegalStateException.class,this::register);
        Throwable cause=failed;while(cause.getCause()!=null)cause=cause.getCause();
        assertInstanceOf(java.sql.SQLException.class,cause);assertEquals(59790,((java.sql.SQLException)cause).getErrorCode(),"Must exercise the injected audit fault, not an earlier registration refusal");
        try(var c=owner(db)){assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiExternalApplication WHERE Source='DCR'"));assertEquals(0,count(c,"SELECT COUNT(*) FROM dbo.AiDcrMetadataV2"));sql(c,"DROP TRIGGER dbo.C2FailGlobalAudit");}
        dcrSuccessorRejectsDriftWithoutChangingHistoricalReceipts();
        try(var c=owner(db)) {
            sql(c,"ALTER TABLE dbo.AiDcrRateV3 ADD Unexpected int NULL");assertThrows(IllegalStateException.class,()->externalAttestation().verify());
            sql(c,"ALTER TABLE dbo.AiDcrRateV3 DROP COLUMN Unexpected");
        }
        externalAttestation().verify();
    }
    @Test void c2DurableAdmissionSerializesConcurrentReplicasAndRetainsApprovedMetadata()throws Exception {
        byte[] source=new byte[32];source[0]=1;
        for(int trial=0;trial<2;trial++) {
        int windowBefore;try(var c=owner(db)){windowBefore=count(c,"SELECT DATEDIFF(minute,CONVERT(datetime2,'20000101'),SYSUTCDATETIME())");}
        try(var pool=Executors.newFixedThreadPool(8)) {
            var tasks=new ArrayList<Future<Boolean>>();for(int i=0;i<20;i++)tasks.add(pool.submit(()->admission().admit(source).allowed()));
            int allowed=0;for(var task:tasks)if(task.get())allowed++;
            int windowAfter;try(var c=owner(db)){windowAfter=count(c,"SELECT DATEDIFF(minute,CONVERT(datetime2,'20000101'),SYSUTCDATETIME())");}
            if(windowBefore!=windowAfter){source[1]++;continue;}
            assertEquals(10,allowed,"Two replicas/concurrent leases share one ten-per-source SQL budget");break;
        }
        }
        try(var c=owner(db)){sql(c,"UPDATE dbo.AiDcrRateV3 SET Attempts=100 WHERE Kind=0");}
        byte[] other=new byte[32];other[0]=99;
        assertFalse(admission().admit(other).allowed(),"Global budget applies to independent source addresses");
        try(var c=owner(db)){sql(c,"UPDATE dbo.AiDcrRateV3 SET MinuteWindow=MinuteWindow-2");}
        assertTrue(admission().admit(other).allowed(),"Expired rate rows are reclaimed atomically");
        var approved=register();approve(approved,7,72,71);var pending=register();
        try(var c=owner(db)) {
            sql(c,"DISABLE TRIGGER dbo.AiDcrImmutableMetadataV2 ON dbo.AiDcrMetadataV2");
            try{sql(c,"UPDATE dbo.AiDcrMetadataV2 SET CreatedAt=DATEADD(day,-2,CreatedAt),PendingExpiresAt=DATEADD(day,-2,PendingExpiresAt)");}
            finally{sql(c,"ENABLE TRIGGER dbo.AiDcrImmutableMetadataV2 ON dbo.AiDcrMetadataV2");}
        }
        register(); // bounded private expiry runs in registration transaction.
        assertNotNull(dcr("v2stored").prepare(approved.identifier()));
        assertThrows(IllegalStateException.class,()->dcr("v2stored").prepare(pending.identifier()));
        try(var c=owner(db)) {
            assertEquals(2,count(c,"SELECT COUNT(*) FROM dbo.AiDcrMetadataV2"),"Approved payload retained; expired never-approved payload reclaimed");
            assertEquals(3,count(c,"SELECT COUNT(*) FROM dbo.AiExternalApplication WHERE Source='DCR'"),"Identity/revision/audit history is retained");
            assertTrue(count(c,"SELECT COUNT(*) FROM dbo.AiDcrRateV3")<=1024);
        }
        for(int i=0;i<7;i++)register();
        var limited=assertThrows(IllegalStateException.class,this::register);
        Throwable rate=limited;while(rate.getCause()!=null)rate=rate.getCause();
        assertInstanceOf(java.sql.SQLException.class,rate);assertEquals(59729,((java.sql.SQLException)rate).getErrorCode(),"Repeated identical metadata uses a shared fingerprint budget");
        externalAttestation().verify();
    }
}
