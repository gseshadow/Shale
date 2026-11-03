package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Separate diagnostic opt-in, never native acceptance or an application startup path. */
class AiCaseDataAzureSqlCatalogComponentTest {
    @Test @Timeout(60) void compareOwnerAndVerifierComponents() {
        Assumptions.assumeTrue("AZURE_SQL_DATABASE_CATALOG_COMPONENTS".equals(System.getenv("SHALE_AZURE_CATALOG_COMPONENT_OPT_IN")),
                "Separate fresh disposable catalog diagnostic opt-in required; not native acceptance");
        try {
            AiCaseDataAzureSqlDisposableSetupTest.requireQuietDriverLogging();
            String database=AiCaseDataAzureSqlDisposableSetupTest.validateOwner(System.getenv());
            if(System.getenv("SHALE_AZURE_SETUP_OPT_IN")!=null)throw AzureCatalogComponents.refused();
            var ownerSource=AiCaseDataAzureSqlDisposableSetupTest.ownerSource(System.getenv());
            var verifierSource=AiCaseDataAzureSqlDatabaseReadOnlyTest.source("VERIFIER");
            compare(ownerSource,verifierSource,database,AiCaseDataAzureSqlDatabaseReadOnlyTest.required("VERIFIER_USERNAME"),
                    Path.of("../work/azure-native/catalog-components.tsv"));
        } catch(Exception|AssertionError failure) {
            // compare already sanitizes its own failures, including exact fixed component names.
            if(failure instanceof DiagnosticFailure safe)throw safe;
            throw incomplete();
        }
    }
    static void compare(DataSource ownerSource,DataSource verifierSource,String database,String verifierUser,Path output) {
        List<AzureCatalogComponents.Component> owner,verifier;
        try {
            Files.deleteIfExists(output);
            try(var a=AiCaseDataAzureSqlDisposableSetupTest.ownerConnect(ownerSource::getConnection);var b=verifierSource.getConnection()) {
                String server=target(a,database,"dbo");
                if(!server.equals(target(b,database,verifierUser)))throw AzureCatalogComponents.refused();
                owner=AzureCatalogComponents.read(a);
                verifier=AzureCatalogComponents.read(b);
            }
            var lines=AzureCatalogComponents.report(owner,verifier);
            Files.createDirectories(output.getParent());
            Files.write(output,lines);
            lines.forEach(System.out::println);
        } catch(Exception|AssertionError failure) {
            // Never expose driver text, SQL, targets, credentials, causes or suppressed close failures.
            throw incomplete();
        }
        var differences=AzureCatalogComponents.differences(owner,verifier);
        if(!differences.isEmpty())throw new DiagnosticFailure("Catalog components differ: "+String.join(",",differences)+"; stop and discard fixture");
        // All 19 equal rows are the result. This does not establish receipt equality or attestation success.
    }
    private static DiagnosticFailure incomplete(){return new DiagnosticFailure("Catalog component diagnostic failed before a complete comparison; discard fixture; no retry/adoption");}
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
