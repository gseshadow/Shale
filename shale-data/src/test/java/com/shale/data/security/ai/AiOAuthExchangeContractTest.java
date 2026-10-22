package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiOAuthExchangeContractTest {
    @Test void additiveMigrationConfinesBootstrapAndSerializesReplayWithConsumeAndAudit()throws Exception {
        String sql=Files.readString(Path.of("../docs/sql/ai-integration/phase3b2c1.sql")).replace("\r\n","\n");
        var receipt=java.util.regex.Pattern.compile("@sourceHash varbinary\\(32\\)=0x([0-9a-f]{64})").matcher(sql);
        assertTrue(receipt.find());
        String normalized=sql.substring(0,receipt.start(1))+"0".repeat(64)+sql.substring(receipt.end(1));
        assertEquals(receipt.group(1),java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8))),"Migration source receipt must change with its reviewed source");
        String resolver=sql.split("CREATE PROCEDURE dbo.ResolveAiOAuthCodeV1",2)[1].split("END;'\\);",2)[0];
        assertTrue(resolver.contains("WITH EXECUTE AS OWNER"));
        assertTrue(resolver.contains("SELECT ShaleClientId,UserId FROM dbo.AiAuthorizationTransaction"));
        assertFalse(resolver.contains("JOIN")); assertFalse(resolver.contains("SELECT *"));
        for(String role:List.of("AiIntegrationRuntime","AiIntegrationBootstrap","AiCapabilityOperator"))assertTrue(sql.contains("ON OBJECT::dbo.AiAssertionReplay TO "+role));
        assertTrue(sql.contains("GRANT EXECUTE ON OBJECT::dbo.ResolveAiOAuthCodeV1 TO AiIntegrationBootstrap"));
        assertFalse(sql.contains("GRANT EXECUTE ON OBJECT::dbo.ResolveAiOAuthCodeV1 TO AiIntegrationRuntime"));
        assertTrue(sql.indexOf("INSERT dbo.AiAssertionReplay(")<sql.indexOf("UPDATE dbo.AiAuthorizationTransaction SET ConsumedAt"));
        assertTrue(sql.contains("PK_AiAssertionReplay PRIMARY KEY(ShaleClientId,IntegrationId,CredentialId,JtiHash)"));
        assertTrue(sql.contains("EXEC dbo.AiLockV1")); assertTrue(sql.contains("ROLLBACK TRANSACTION; THROW"));
        assertTrue(sql.contains("INSERT dbo.EntityActionAuditLog"));
        assertFalse(sql.contains("INSERT dbo.AiOAuthCredential")); assertFalse(sql.contains("INSERT dbo.AiDelegatedSession"));
        assertEquals(3,sql.split("PREDICATE sec.fn_FilterByTenant\\(ShaleClientId\\) ON dbo.AiAssertionReplay",-1).length-1);
    }
    @Test void immutableReceiptContainsOnlyServerVerifiedFutureIssuanceFields() {
        var result=new AiOAuthExchangeStore.ValidatedExchange(7,72,UUID.randomUUID(),"client",UUID.randomUUID(),UUID.randomUUID(),null,com.shale.core.security.ai.AiScope.fromMask(1),"issuer","resource",java.time.Instant.now(),UUID.randomUUID());
        assertEquals("ValidatedExchange[redacted]",result.toString());
        assertThrows(UnsupportedOperationException.class,()->result.approvedScopes().clear());
        for(var component:result.getClass().getRecordComponents())assertFalse(component.getName().matches("(?i).*(code|verifier|assertion|password|token|prompt|case).*"));
    }
}
