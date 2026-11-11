package com.shale.data.security.ai;

import org.junit.jupiter.api.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Focused PR4C checks on the unchanged hash-gated authoritative candidate.
 * Owner installation is distinct from restricted runtime readiness; no HTTP or activation. */
class AiCimdRuntimeAuthoritativeSqlServerTest extends AiCimdOAuthLocalSqlServerTest {
    @BeforeAll void runtimeCatalogIdentity()throws Exception {
        String secret=UUID.randomUUID()+"aB9!";
        try(var c=owner("master")) {
            sql(c,"CREATE LOGIN ["+prefix+"_runtimecatalog] WITH PASSWORD='"+secret+"',CHECK_POLICY=OFF");
            secrets.put("runtimecatalog",secret);
            sql(c,"GRANT VIEW ANY DEFINITION TO ["+prefix+"_runtimecatalog]");
        }
    }
    private Map<AiCimdIdentity,String> roles() {
        return Map.of(AiCimdIdentity.CASE,"cimd",AiCimdIdentity.BROWSER,"cimdbrowser",AiCimdIdentity.ISSUER,"cimdissuer",
                AiCimdIdentity.RESOLVER,"cimdresolver",AiCimdIdentity.METADATA,"cimdmetadata",AiCimdIdentity.NEUTRAL,"cimdneutral",
                AiCimdIdentity.REGISTRY_PUBLISHER,"publisher",AiCimdIdentity.REGISTRY_TENANT,"tenant",AiCimdIdentity.CATALOG,"runtimecatalog");
    }
    private void off(Connection c)throws SQLException {
        sql(c,"UPDATE dbo.AiProductCapability SET Enabled=0 WHERE Id=1; UPDATE dbo.AiCimdProofControlV1 SET Enabled=0 WHERE Id=1; UPDATE dbo.AiCimdOAuthControlV1 SET Enabled=0 WHERE Id=1");
    }
    @Test void runtimeAuthoritativeCatalogInstallAndRestrictedRefusal()throws Exception {
        Map<String,String> before;String bindingBefore;
        try(var c=owner(db)) {
            off(c);before=receipts(c);bindingBefore=binding(c);
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4c-runtime.sql")));
            sql(c,"ALTER ROLE AiCimdCatalogV1 ADD MEMBER ["+prefix+"_runtimecatalog]");
            assertEquals(before,receipts(c));assertEquals(bindingBefore,binding(c));
        }
        var sources=new EnumMap<AiCimdIdentity,DataSource>(AiCimdIdentity.class);
        var users=new EnumMap<AiCimdIdentity,String>(AiCimdIdentity.class);
        var endpoints=new EnumMap<AiCimdIdentity,AiCimdRuntime.Endpoint>(AiCimdIdentity.class);
        try(var review=source("runtimecatalog").getConnection()) {
            SQLException catalogRefusal=assertThrows(SQLException.class,()->installSql(review,"EXEC dbo.AiCimdAttestCatalogV1"));
            assertEquals(59310,catalogRefusal.getErrorCode(),"The unchanged #1948 verifier must reject missing server visibility in owner context.");
            assertTrue(catalogRefusal.getMessage().contains("Registry server catalog visibility required."));
            for(var entry:roles().entrySet()) {
                var role=entry.getKey();String key=entry.getValue();sources.put(role,source(key));users.put(role,prefix+"_"+key);
                endpoints.put(role,new AiCimdRuntime.Endpoint("jdbc:sqlserver://127.0.0.1:"+port+";databaseName="+db,prefix+"_"+key,secrets.get(key)));
                try(var caller=source(key).getConnection()) {
                    AiCimdRuntimeAttestation.verifyIdentity(caller,review,AiCimdRuntimeAttestation.identity(caller),role,AiCaseDataAttestation.Platform.SQL_SERVER_OR_MI);
                }
            }
        }
        var forbidden=List.of(source("runtime"),source("runtime"),source("bootstrap"),source("ai"));
        var attestor=new AiCimdRuntimeAttestation(sources,users,forbidden,db,ISSUER,RESOURCE,contract());
        var refused=assertThrows(IllegalStateException.class,attestor::verify,"Boxed owner-context verification must not invent server visibility.");
        assertTrue(refused.getMessage().endsWith("RECEIPTS_GRAPH_BINDING"),"All identity, effective permission, source and selected-contract checks must pass before the known boxed catalog blocker: "+refused.getMessage());
        assertThrows(IllegalStateException.class,()->AiCimdRuntime.open(new AiCimdRuntime.Configuration(endpoints,db,ISSUER,RESOURCE,contract()),forbidden));
        try(var c=owner(db)) {
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.dm_exec_sessions WHERE database_id=DB_ID() AND program_name LIKE 'ShaleCimd-%'"));
            assertEquals(before,receipts(c));assertEquals(bindingBefore,binding(c));
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4b-oauth-verify.sql")));
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1 WHERE Id=1");
        }
        manualRead();
    }
    @Test void runtimeAuthoritativeLateFaultRollsBackNewCatalogOnly()throws Exception {
        try(var c=owner(db)) {
            off(c);var before=receipts(c);String bindingBefore=binding(c);
            String sql=Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4c-runtime.sql"));
            String injected=sql.replace("CREATE ROLE AiCimdCatalogV1 AUTHORIZATION dbo;","CREATE ROLE AiCimdCatalogV1 AUTHORIZATION dbo;\nEXEC(N'CREATE TRIGGER dbo.Pr4cLateAuditBridge ON dbo.EntityActionAuditLog AFTER INSERT AS SELECT 1 AS UnreviewedResult');");
            assertNotEquals(sql,injected);
            assertThrows(SQLException.class,()->installSql(c,injected),"A late terminal-audit bridge must roll back the new catalog gate, role and injected trigger.");
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.objects WHERE name IN('AiCimdAttestCatalogV1','Pr4cLateAuditBridge')"));
            assertEquals(0,count(c,"SELECT COUNT(*) FROM sys.database_principals WHERE name='AiCimdCatalogV1'"));
            assertEquals(before,receipts(c));assertEquals(bindingBefore,binding(c));
            installSql(c,Files.readString(Path.of("../docs/sql/ai-integration/universal-pr4b-oauth-verify.sql")));
            sql(c,"UPDATE dbo.AiProductCapability SET Enabled=1 WHERE Id=1");
        }
        manualRead();
    }
}
