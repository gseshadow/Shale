package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit live read-only platform gate AFTER separately authorized migration/provisioning.
 * No installer, fault injection, protected procedure call or application-row SELECT is performed.
 * Stage 0 BEFORE provisioning uses azure-sql-stage0-verify.sql under existing operator visibility. */
class AiCaseDataAzureSqlDatabaseReadOnlyTest {
    @Test @Timeout(60) void dedicatedContainedIdentitiesSatisfyLiveContract()throws Exception {
        Assumptions.assumeTrue("AZURE_SQL_DATABASE_READ_ONLY".equals(System.getenv("SHALE_AZURE_ATTESTATION_OPT_IN")),"Absent live Azure opt-in is NOT acceptance");
        DataSource ai=source("AI"),verifier=source("VERIFIER");
        try(var c=ai.getConnection()) {assertEquals(AiCaseDataAttestation.Platform.AZURE_SQL_DATABASE,AiCaseDataAttestation.platform(c));}
        new AiCaseDataAttestation(ai,verifier,List.of(source("AUTH"),source("RUNTIME"),source("BOOTSTRAP")),
                required("DATABASE"),required("ISSUER"),required("RESOURCE"),true,AiCaseDataAttestation.Contract.valueOf(required("CONTRACT_VERSION"))).verify();
    }
    private static DataSource source(String identity) {
        String url=required("JDBC_URL");
        // Explicit database; no auth/TLS overrides or alternate transport.
        assertTrue(url.matches("jdbc:sqlserver://[A-Za-z0-9.-]+(?::[0-9]+)?;databaseName=[A-Za-z0-9_-]+;?"),"Dedicated SQL-auth URL with explicit target database required");
        var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();
        source.setURL(url);source.setUser(required(identity+"_USERNAME"));source.setPassword(required(identity+"_PASSWORD"));
        source.setEncrypt("true");source.setTrustServerCertificate(false);source.setLoginTimeout(3);source.setSocketTimeout(5000);
        return source;
    }
    private static String required(String suffix) {
        String value=System.getenv("SHALE_AZURE_"+suffix);
        if(value==null||value.isBlank())throw new IllegalStateException("Missing read-only Azure acceptance setting: "+suffix);
        return value;
    }
}
