package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiOAuthCredentialContractTest {
    @Test void credentialsHave256BitsAndOnlyHashesCrossTheSqlBoundary() {
        var tokens=new HashSet<String>();
        for(int i=0;i<1000;i++) {
            String raw=AiOAuthCredentialStore.opaque();assertTrue(raw.matches("[A-Za-z0-9_-]{43}"));
            assertEquals(32,Base64.getUrlDecoder().decode(raw).length);assertTrue(tokens.add(raw));
            assertEquals(32,AiOAuthCredentialStore.digest(raw).length);
            assertFalse(Arrays.equals(raw.getBytes(StandardCharsets.US_ASCII),AiOAuthCredentialStore.digest(raw)));
        }
    }
    @Test void migrationIsReceiptedAdditiveAndRetainsSharedAuthority()throws Exception {
        String sql=Files.readString(Path.of("../docs/sql/ai-integration/phase3b2c2.sql")).replace("\r\n","\n");
        var m=java.util.regex.Pattern.compile("@sourceHash varbinary\\(32\\)=0x([0-9a-f]{64})").matcher(sql);assertTrue(m.find());
        String normalized=sql.substring(0,m.start(1))+"0".repeat(64)+sql.substring(m.end(1));
        assertEquals(m.group(1),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))));
        assertTrue(Files.readString(Path.of("../docs/sql/ai-integration/verify.sql")).contains("<>0x"+m.group(1)),"The cumulative verifier must pin the reviewed lifecycle source receipt");
        assertFalse(sql.contains("CREATE TABLE"));assertFalse(sql.contains("DROP TABLE"));
        for(String contract:List.of("UX_AiSession_Transaction","FK_AiSession_Authorization","DATEADD(hour,24","DATEADD(hour,8","DATEADD(minute,5","AiLockV1","ConsumedAt IS NOT NULL","RevocationReason","INSERT dbo.EntityActionAuditLog","COMMIT TRANSACTION;","SELECT CAST(1 AS bit) AS Denied"))assertTrue(sql.contains(contract),contract);
        assertTrue(sql.contains("c.Kind=''REFRESH''"));
    }
}
