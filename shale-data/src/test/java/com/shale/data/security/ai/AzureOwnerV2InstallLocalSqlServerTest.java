package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dedicated, new loopback synthetic databases only; no Azure timing/platform claim. */
class AzureOwnerV2InstallLocalSqlServerTest {
    private final AiCaseDataLocalSqlServerTest local=new AiCaseDataLocalSqlServerTest();
    private String db;
    @BeforeEach void create()throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_V2_INSTALL".equals(System.getenv("SHALE_V2_INSTALL_OPT_IN")),"Dedicated local V2 cancellation fixture required");
        local.port=System.getenv("SHALE_V2_INSTALL_PORT");local.password=System.getenv("SHALE_V2_INSTALL_PASSWORD");
        assertNotNull(local.port);assertTrue(local.port.matches("[0-9]{4,5}")&&Integer.parseInt(local.port)>=1024&&Integer.parseInt(local.port)<=65535);assertNotNull(local.password);
        try(var c=local.owner("master")) {
            assertTrue(Set.of(2,3,4).contains(AiCaseDataLocalSqlServerTest.count(c,"SELECT CONVERT(int,SERVERPROPERTY('EngineEdition'))")),"Boxed dedicated loopback fixture only");
            db="V2Install_"+UUID.randomUUID().toString().replace("-","");sql(c,"CREATE DATABASE ["+db+"]");
        }
    }
    @AfterEach void remove()throws Exception {
        if(db!=null)try(var c=local.owner("master")){sql(c,"ALTER DATABASE ["+db+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE ["+db+"]");}
    }
    private static final String MUTATIONS="CREATE ROLE AiCaseDataRuntime; CREATE TABLE dbo.AiCaseDataInvocation(Id int NOT NULL); INSERT dbo.AiCaseDataInvocation VALUES(1); CREATE INDEX UX_AiDataCredentialOwner ON dbo.AiCaseDataInvocation(Id); CREATE TYPE dbo.AiCaseDataIds AS TABLE(Id int); EXEC sys.sp_addextendedproperty @name=N'Shale.AiCaseDataV2',@value=0x01,@level0type=N'SCHEMA',@level0name=N'dbo',@level1type=N'TABLE',@level1name=N'AiCaseDataInvocation';";
    @Test void successfulBatchDrainsResultsAndCommitsAllSyntheticChanges()throws Exception {
        try(var c=local.owner(db)) {
            c.setNetworkTimeout(null,120_000);
            AzureOwnerV2Install.execute(c,new AzureOwnerV2Install.Plan("SET NOCOUNT ON; SET XACT_ABORT ON; BEGIN TRANSACTION;"+MUTATIONS+AzureOwnerV2Install.checkpoint(1)+"COMMIT; SELECT 1; SELECT 2;",List.of("COMMIT")),20);
            assertEquals(1,AiCaseDataLocalSqlServerTest.count(c,"SELECT COUNT(*) FROM dbo.AiCaseDataInvocation"));
            assertEquals(0,AiCaseDataLocalSqlServerTest.count(c,"SELECT @@TRANCOUNT"));
        }
    }
    @Test @Timeout(40) void attentionDuringActiveTransactionHasCheckpointAndLeavesNoDdlRowsRolesTypeIndexOrReceipt()throws Exception {
        try(var c=local.owner(db)) {
            c.setNetworkTimeout(null,120_000);
            assertTrue(c.getAutoCommit(),"Match the owner's JDBC mode");
            String batch="SET XACT_ABORT ON; BEGIN TRY BEGIN TRANSACTION;"+MUTATIONS+AzureOwnerV2Install.checkpoint(1)+"WAITFOR DELAY '00:00:10'; COMMIT; END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;";
            var failure=assertThrows(SQLException.class,()->AzureOwnerV2Install.execute(c,new AzureOwnerV2Install.Plan(batch,List.of("UNICODE")),1));
            assertTrue(failure.getMessage().contains("lastObserved=1; rollback=CONFIRMED"),"Real driver attention must retain the last fixed warning and verify rollback");
            AzureOwnerV2Install.rollbackAndVerifyAbsent(c);
            // Independent connection proves this is rollback, not merely hidden uncommitted state.
            try(var independent=local.owner(db)){AzureOwnerV2Install.rollbackAndVerifyAbsent(independent);}
        }
    }
    @Test void ordinarySqlFailureRollsBackAndSuppressesPrivateServerText()throws Exception {
        try(var c=local.owner(db)) {
            String batch="SET XACT_ABORT ON; BEGIN TRY BEGIN TRANSACTION;"+MUTATIONS+AzureOwnerV2Install.checkpoint(1)+"THROW 59001,'private synthetic contents',1; COMMIT; END TRY BEGIN CATCH IF XACT_STATE()<>0 ROLLBACK; THROW; END CATCH;";
            var failure=assertThrows(SQLException.class,()->AzureOwnerV2Install.execute(c,new AzureOwnerV2Install.Plan(batch,List.of("RECEIPTS")),20));
            assertTrue(failure.getMessage().contains("rollback=CONFIRMED"));assertFalse(failure.toString().contains("private"));
        }
    }
    @Test @Timeout(40) void commitBeforeLostCompletionCannotBeMisreportedAsRollbackConfirmed()throws Exception {
        try(var c=local.owner(db)) {
            c.setNetworkTimeout(null,120_000);
            String batch="SET XACT_ABORT ON; BEGIN TRANSACTION;"+MUTATIONS+"COMMIT;"+AzureOwnerV2Install.checkpoint(1)+"WAITFOR DELAY '00:00:10';";
            var failure=assertThrows(SQLException.class,()->AzureOwnerV2Install.execute(c,new AzureOwnerV2Install.Plan(batch,List.of("COMMIT")),1));
            assertTrue(failure.getMessage().contains("rollback=UNCONFIRMED"),"Cancellation after commit can leave a complete install; mandatory fixture destruction is still required");
        }
    }
    @Test @Timeout(120) void exactUnicodePopulationRunsLocallyAndIsTransactional()throws Exception {
        var lines=Files.readAllLines(Path.of("../docs/sql/ai-integration/phase3b8-confinement.sql"));
        StringBuilder batch=new StringBuilder("SET XACT_ABORT ON; BEGIN TRANSACTION;\n");int inserts=0;
        for(String line:lines)if(line.startsWith("CREATE TABLE dbo.AiDataUnicode")||line.startsWith("CREATE TABLE dbo.AiDataWordTransition")||line.startsWith("INSERT dbo.AiData")) {
            batch.append(line).append('\n');if(line.startsWith("INSERT"))inserts++;
        }
        assertEquals(4203,inserts);
        long start=System.nanoTime();
        try(var c=local.owner(db);var s=c.createStatement()) {
            s.setQueryTimeout(60);boolean result=s.execute(batch.toString());
            while(true){if(result)try(var r=s.getResultSet()){while(r.next()){} }else if(s.getUpdateCount()==-1)break;result=s.getMoreResults(Statement.CLOSE_CURRENT_RESULT);}
            assertEquals(2310,AiCaseDataLocalSqlServerTest.count(c,"SELECT COUNT(*) FROM dbo.AiDataUnicodeRange"));
            assertEquals(1433,AiCaseDataLocalSqlServerTest.count(c,"SELECT COUNT(*) FROM dbo.AiDataUnicodeLower"));
            assertEquals(460,AiCaseDataLocalSqlServerTest.count(c,"SELECT COUNT(*) FROM dbo.AiDataWordTransition"));
            AzureOwnerV2Install.rollbackAndVerifyAbsent(c);
        }
        System.err.printf(Locale.ROOT,"LOCAL_V2_UNICODE inserts=%d elapsedMillis=%d azureTimingEvidence=false%n",inserts,(System.nanoTime()-start)/1_000_000);
    }
    @Test @Timeout(180) void completeReviewedInstallerWithPrivateAuthoritativePrerequisites()throws Exception {
        try(var c=local.owner(db)) {
            prepareAuthoritative(c);AzureOwnerV2Install.install(c,Files.readAllBytes(Path.of("../docs/sql/ai-integration/phase3b8-confinement.sql")));
            assertEquals(2,AiCaseDataLocalSqlServerTest.count(c,"SELECT COUNT(*) FROM sys.extended_properties WHERE major_id=OBJECT_ID('dbo.AiCaseDataInvocation') AND name IN('Shale.AiCaseDataV2','Shale.AiCaseDataV2.Source')"));
            sql(c,Files.readString(Path.of("../docs/sql/ai-integration/verify.sql")));
        }
    }
    @Test @Timeout(180) void cancellationBeforeRealV2ReceiptRollsBackEntireReviewedInstaller()throws Exception {
        try(var c=local.owner(db)) {
            prepareAuthoritative(c);
            var plan=AzureOwnerV2Install.diagnosticPlan(Files.readAllBytes(Path.of("../docs/sql/ai-integration/phase3b8-confinement.sql")));
            int stage=plan.stages().indexOf("RECEIPTS")+1;assertTrue(stage>0);
            String constrained=plan.sql().replace(AzureOwnerV2Install.checkpoint(stage),AzureOwnerV2Install.checkpoint(stage)+"WAITFOR DELAY '00:00:30';\n");
            var failure=assertThrows(SQLException.class,()->AzureOwnerV2Install.execute(c,new AzureOwnerV2Install.Plan(constrained,plan.stages()),20));
            assertTrue(failure.getMessage().contains("rollback=CONFIRMED"));
            try(var independent=local.owner(db)){AzureOwnerV2Install.rollbackAndVerifyAbsent(independent);}
        }
    }
    private void prepareAuthoritative(Connection c)throws Exception {
        Assumptions.assumeTrue(System.getenv("SHALE_CASE_VERIFY_SCHEMA")!=null,"Private hash-gated authoritative candidate required; skip is not complete-installer acceptance");
        c.setNetworkTimeout(null,120_000);
        var safety=Class.forName("com.shale.data.verification.CaseReadVerificationSafety");
        var parser=safety.getDeclaredMethod("schema",Path.class,String.class);parser.setAccessible(true);
        @SuppressWarnings("unchecked") var schema=(List<String>)parser.invoke(null,Path.of(System.getenv("SHALE_CASE_VERIFY_SCHEMA")),"4b64b50da531ebc6a2eb9dc26c6555ef555621df371fe9d8a5cf994fb6e8417f");
        for(String batch:schema)sql(c,batch);
        for(String migration:List.of("phase3b1.sql","phase3b2b.sql","phase3b2c1.sql","phase3b2c2.sql","revocation.sql","phase3b2d.sql","phase3b3.sql"))sql(c,Files.readString(Path.of("../docs/sql/ai-integration/"+migration)));
        var fixture=new AiCaseDataV2AuthoritativeLocalSqlServerTest();fixture.db=db;fixture.beforeConfinement(c);
    }
    private static void sql(Connection c,String text)throws SQLException {AiCaseDataLocalSqlServerTest.sql(c,text);}
}
