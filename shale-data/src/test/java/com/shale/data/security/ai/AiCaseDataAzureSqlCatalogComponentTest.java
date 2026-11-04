package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Separate diagnostic opt-in, never native acceptance or an application startup path. */
class AiCaseDataAzureSqlCatalogComponentTest {
    enum Stage { OWNER_TARGET_PREFLIGHT, VERIFIER_TARGET_PREFLIGHT, OWNER_COMPONENT_READ, VERIFIER_COMPONENT_READ, REPORT_WRITE }
    @Test @Timeout(60) void compareOwnerAndVerifierComponents() {
        Assumptions.assumeTrue("AZURE_SQL_DATABASE_CATALOG_COMPONENTS".equals(System.getenv("SHALE_AZURE_CATALOG_COMPONENT_OPT_IN")),
                "Separate fresh disposable catalog diagnostic opt-in required; not native acceptance");
        Stage stage=Stage.OWNER_TARGET_PREFLIGHT;
        try {
            AiCaseDataAzureSqlDisposableSetupTest.requireQuietDriverLogging();
            String database=AiCaseDataAzureSqlDisposableSetupTest.validateOwner(System.getenv());
            if(System.getenv("SHALE_AZURE_SETUP_OPT_IN")!=null)throw AzureCatalogComponents.refused();
            var ownerSource=AiCaseDataAzureSqlDisposableSetupTest.ownerSource(System.getenv());
            stage=Stage.VERIFIER_TARGET_PREFLIGHT;
            var verifierSource=AiCaseDataAzureSqlDatabaseReadOnlyTest.source("VERIFIER");
            compare(ownerSource,verifierSource,database,AiCaseDataAzureSqlDatabaseReadOnlyTest.required("VERIFIER_USERNAME"),
                    Path.of("../work/azure-native/catalog-components.tsv"));
        } catch(Exception|AssertionError failure) {
            // compare already sanitizes its own failures, including exact fixed component names.
            if(failure instanceof DiagnosticFailure safe)throw safe;
            throw incomplete(stage,failure);
        }
    }
    static void compare(DataSource ownerSource,DataSource verifierSource,String database,String verifierUser,Path output) {
        List<AzureCatalogComponents.Component> owner,verifier;
        Stage stage=Stage.REPORT_WRITE;
        try {
            Files.deleteIfExists(output);
            stage=Stage.OWNER_TARGET_PREFLIGHT;
            try(var a=AiCaseDataAzureSqlDisposableSetupTest.ownerConnect(ownerSource::getConnection)) {
                stage=Stage.VERIFIER_TARGET_PREFLIGHT;
                try(var b=verifierSource.getConnection()) {
                    stage=Stage.OWNER_TARGET_PREFLIGHT;
                    String server=target(a,database,"dbo");
                    stage=Stage.VERIFIER_TARGET_PREFLIGHT;
                    if(!server.equals(target(b,database,verifierUser)))throw AzureCatalogComponents.refused();
                    stage=Stage.OWNER_COMPONENT_READ;
                    owner=AzureCatalogComponents.read(a);
                    stage=Stage.VERIFIER_COMPONENT_READ;
                    verifier=AzureCatalogComponents.read(b);
                    stage=Stage.VERIFIER_TARGET_PREFLIGHT; // Includes verifier connection cleanup.
                }
                stage=Stage.OWNER_TARGET_PREFLIGHT; // Includes owner connection cleanup.
            }
            stage=Stage.REPORT_WRITE;
            var lines=AzureCatalogComponents.report(owner,verifier);
            Files.createDirectories(output.getParent());
            Files.write(output,lines);
            lines.forEach(System.out::println);
        } catch(Exception|AssertionError failure) {
            // Never expose driver text, SQL, targets, credentials, causes or suppressed close failures.
            try{Files.deleteIfExists(output);}catch(Exception ignored){/* Never emit cleanup details. */}
            throw incomplete(stage,failure);
        }
        var differences=AzureCatalogComponents.differences(owner,verifier);
        if(!differences.isEmpty())throw new DiagnosticFailure("Catalog components differ: "+String.join(",",differences)+"; stop and discard fixture");
        // All 19 equal rows are the result. This does not establish receipt equality or attestation success.
    }
    private static DiagnosticFailure incomplete(Stage stage,Throwable failure){
        String component="";
        if(stage==Stage.OWNER_COMPONENT_READ||stage==Stage.VERIFIER_COMPONENT_READ)
            component="; component="+(failure instanceof AzureCatalogComponents.ReadFailure read?read.componentLabel()+read.phaseLabel():"0:before_first_component; phase="+AzureCatalogComponents.ReadPhase.STATEMENT_CREATE.name());
        return new DiagnosticFailure("Catalog component diagnostic failed before a complete comparison; stage="+stage.name()+component+"; discard fixture; no retry/adoption");
    }
    private static final class DiagnosticFailure extends IllegalStateException {
        DiagnosticFailure(String message){super(message);}
    }
    private static String target(Connection c,String database,String user)throws SQLException {
        try(var s=c.createStatement()) {
            s.setQueryTimeout(AiCaseDataAzureSqlDisposableSetupTest.OWNER_QUERY_SECONDS);
            try(var r=s.executeQuery("SELECT DB_NAME(),USER_NAME(),CONVERT(int,SERVERPROPERTY('EngineEdition')),CONVERT(nvarchar(128),SERVERPROPERTY('ServerName')),HAS_PERMS_BY_NAME(DB_NAME(),'DATABASE','VIEW DEFINITION')")) {
                if(!r.next()||!database.equals(r.getString(1))||!user.equals(r.getString(2))||r.getInt(3)!=5||r.getInt(5)!=1)throw AzureCatalogComponents.refused();
                String server=r.getString(4);
                if(server==null||server.isBlank()||r.next())throw AzureCatalogComponents.refused();
                return server;
            }
        }
    }
}
