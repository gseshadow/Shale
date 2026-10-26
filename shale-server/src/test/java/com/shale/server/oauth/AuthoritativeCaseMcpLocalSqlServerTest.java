package com.shale.server.oauth;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Maintained OAuth issuance -> fresh ACCESS -> restricted AI SQL -> both unchanged MCP tools.
 * Scoped regression only; the broader Phase3B.7 matrix is not resumed. */
class AuthoritativeCaseMcpLocalSqlServerTest {
    @ParameterizedTest @ValueSource(strings={"ts","python"})
    void bothIndependentClientsUseConfinedSql(String kind,@org.junit.jupiter.api.io.TempDir Path temp)throws Exception {
        Assumptions.assumeTrue("LOCAL_DISPOSABLE_AI".equals(System.getenv("SHALE_AI_SQL_OPT_IN"))&&System.getenv("SHALE_CASE_VERIFY_SCHEMA")!=null);
        var h=new OAuthAuthorizationFlowTest();String ai=null,catalog=null;String aiSecret=UUID.randomUUID()+"Aa9!",catalogSecret=UUID.randomUUID()+"Aa9!";
        try {
            new AuthoritativeCaseMcpFixture().setup(h);
            ai=h.db+"_case_data";catalog=h.db+"_case_catalog";
            try(var c=h.owner("master")){OAuthAuthorizationFlowTest.sql(c,"CREATE LOGIN ["+ai+"] WITH PASSWORD='"+aiSecret+"',CHECK_POLICY=OFF; CREATE LOGIN ["+catalog+"] WITH PASSWORD='"+catalogSecret+"',CHECK_POLICY=OFF; GRANT VIEW ANY DEFINITION TO ["+catalog+"]");}
            try(var c=h.owner(h.db)) {
                OAuthAuthorizationFlowTest.sql(c,"DROP TRIGGER dbo.LocalPhiFault");
                OAuthAuthorizationFlowTest.sql(c,Files.readString(Path.of("../docs/sql/ai-integration/phase3b7-confinement.sql")));
                OAuthAuthorizationFlowTest.sql(c,"CREATE USER ["+ai+"] FOR LOGIN ["+ai+"]; ALTER ROLE AiCaseDataRuntime ADD MEMBER ["+ai+"]; CREATE USER ["+catalog+"] FOR LOGIN ["+catalog+"]; ALTER ROLE AiCaseDataCatalogVerifier ADD MEMBER ["+catalog+"]");
                OAuthAuthorizationFlowTest.sql(c,"INSERT dbo.AiCaseDataDeployment VALUES(1,N'"+OAuthAuthorizationFlowTest.ISSUER+"',N'"+OAuthAuthorizationFlowTest.RESOURCE+"','case-read-v1','"+java.time.ZoneId.systemDefault().getId()+"','UTC')");
            }
            String access=h.freshAccess("cases.search cases.overview.read");
            new AuthoritativeCaseMcpFixture().actor(h,78,7);
            String expiryToken=h.freshAccess("cases.overview.read");UUID expirySession;
            try(var c=h.owner(h.db);var p=c.prepareStatement("SELECT SessionId FROM dbo.AiOAuthCredential WHERE CredentialHash=?")) {
                byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(expiryToken.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                try {p.setBytes(1,digest);try(var r=p.executeQuery()){assertTrue(r.next());expirySession=UUID.fromString(r.getString(1));assertFalse(r.next());}}finally{Arrays.fill(digest,(byte)0);p.clearParameters();}
            }
            try(var drill=new com.shale.server.mcp.ConfinedExpiryDrill(()->h.owner(h.db),expirySession)) {
                com.shale.server.mcp.McpConfinedClientFixture.verify(drill.wrap(source(h,ai,aiSecret)),source(h,catalog,catalogSecret),
                        List.of(source(h,h.login,h.secret),source(h,h.bootstrapLogin,h.bootstrapSecret),source(h,"sa",h.password)),h.db,access,expiryToken,kind,temp);
                drill.verify();
            }
            try(var c=h.owner(h.db);var s=c.createStatement();var r=s.executeQuery("SELECT (SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE Outcome='ALLOWED_READ'),(SELECT COUNT(*) FROM dbo.auditLog WHERE FieldName='Case.Overview.Read'),(SELECT COUNT(*) FROM dbo.IntegrationAccessAuditResource WHERE Relation='RETURNED'),(SELECT SUM(CONVERT(int,RowCost)) FROM dbo.AiQuotaReservation)")) {
                assertTrue(r.next());assertEquals(3,r.getInt(1));assertEquals(2,r.getInt(2));assertEquals(12,r.getInt(3));assertEquals(13,r.getInt(4));
            }
            try(var c=h.owner(h.db);var st=c.createStatement();var r=st.executeQuery("SELECT COUNT(*) FROM dbo.IntegrationAccessAuditLog WHERE UserId=78 AND Stage='TERMINAL' AND Outcome='DENIED'")){assertTrue(r.next());assertEquals(1,r.getInt(1),"Expiry must withhold output after committed READ");}
        } finally {
            try{h.cleanup();}finally{if(ai!=null)try(var c=h.owner("master")){OAuthAuthorizationFlowTest.sql(c,"DROP LOGIN ["+ai+"]; DROP LOGIN ["+catalog+"]");}}
        }
    }
    private static javax.sql.DataSource source(OAuthAuthorizationFlowTest h,String user,String password){var source=new com.microsoft.sqlserver.jdbc.SQLServerDataSource();source.setURL(h.url(h.db));source.setUser(user);source.setPassword(password);return source;}
}
