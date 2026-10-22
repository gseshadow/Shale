package com.shale.data.security.ai;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiOAuthRevocationContractTest {
    @Test void opaqueAndMalformedPresentationsUseOnlyBoundedLocalDigests() {
        String opaque=AiOAuthCredentialStore.opaque();
        assertArrayEquals(AiOAuthCredentialStore.digest(opaque),AiOAuthCredentialStore.revocationDigest(opaque));
        for(String malformed:List.of("short","invalid.jwt","é",""))assertEquals(32,AiOAuthCredentialStore.revocationDigest(malformed).length);
        assertThrows(IllegalArgumentException.class,()->AiOAuthCredentialStore.revocationDigest(null));
        assertThrows(IllegalArgumentException.class,()->AiOAuthCredentialStore.revocationDigest("a".repeat(2049)));
    }
    @Test void manualMigrationRetainsReceiptsRlsSharedReplayAndAtomicFamilyAudit()throws Exception {
        String sql=Files.readString(Path.of("../docs/sql/ai-integration/revocation.sql")).replace("\r\n","\n");
        var m=java.util.regex.Pattern.compile("@sourceHash varbinary\\(32\\)=0x([0-9a-f]{64})").matcher(sql);assertTrue(m.find());
        String normalized=sql.substring(0,m.start(1))+"0".repeat(64)+sql.substring(m.end(1));
        assertEquals(m.group(1),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))));
        for(String required:List.of("Shale.AiCredentialsV1","Shale.AiRevocationV1","EXEC dbo.AiLockV1","WITH EXECUTE AS OWNER","INSERT dbo.AiAssertionReplay","INSERT dbo.EntityActionAuditLog","RevokedAt IS NULL","ROLLBACK TRANSACTION; THROW"))assertTrue(sql.contains(required),required);
        assertFalse(sql.contains("CREATE TABLE"));assertFalse(sql.contains("ALTER PROCEDURE"));assertFalse(sql.contains("SELECT *"));
        String resolver=sql.split("CREATE PROCEDURE dbo.ResolveAiRevocationV1",2)[1].split("END;'\\);",2)[0];
        assertTrue(resolver.contains("c.CredentialHash=@Hash"));assertTrue(resolver.contains("DATALENGTH(i.ClientId)=DATALENGTH(@ClientId)"));
        assertFalse(resolver.contains("SELECT c.CredentialHash"));
        assertTrue(sql.contains("GRANT EXECUTE ON OBJECT::dbo.ResolveAiRevocationV1 TO AiIntegrationBootstrap"));
        assertFalse(sql.contains("GRANT EXECUTE ON OBJECT::dbo.ResolveAiRevocationV1 TO AiIntegrationRuntime"));
    }
    @Test void productionAdapterCallsConfinedProceduresWithDigestsAndClosesFreshLeases() {
        var calls=new ArrayList<String>();var bindings=new HashMap<Integer,Object>();var closes=new java.util.concurrent.atomic.AtomicInteger();
        var statement=(java.sql.CallableStatement)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.CallableStatement.class},(o,m,a)-> {
            if(m.getName().startsWith("set") && a!=null && a.length==2)bindings.put((Integer)a[0],a[1]);
            return switch(m.getName()) {case "execute" -> false;case "getUpdateCount" -> -1;default -> null;};
        });
        var connection=(java.sql.Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{java.sql.Connection.class},(o,m,a)-> {
            return switch(m.getName()) {case "getAutoCommit" -> true;case "prepareCall" -> {calls.add((String)a[0]);yield statement;}case "close" -> {closes.incrementAndGet();yield null;}default -> null;};
        });
        var store=new AiOAuthCredentialStore(()->connection,"https://shale.test/oauth/mcp","https://shale.test/mcp");
        String token=AiOAuthCredentialStore.opaque();assertTrue(store.resolveRevocation(token,"client").isEmpty());
        assertEquals("{call dbo.ResolveAiRevocationV1(?,?,?,?)}",calls.getLast());
        assertArrayEquals(AiOAuthCredentialStore.digest(token),(byte[])bindings.get(1));
        store.revoke(token,"client",null,null,null,null);
        assertEquals("{call dbo.AiRevokeOAuthCredentialV1(?,?,?,?,?,?,?,?,?)}",calls.getLast());
        assertArrayEquals(AiOAuthCredentialStore.digest(token),(byte[])bindings.get(1));assertEquals(9,bindings.size());
        var key=UUID.randomUUID();var now=java.time.Instant.now();store.revoke(token,"client",key,"private-jti",now,now.plusSeconds(60));
        assertEquals(key,bindings.get(5));assertArrayEquals(AiSql.hash("private-jti".getBytes(StandardCharsets.UTF_8)),(byte[])bindings.get(6));
        assertFalse(bindings.containsValue(token));assertFalse(bindings.containsValue("private-jti"));
        assertTrue(new AiOAuthTransactionStore(()->connection,true,"https://shale.test/oauth/mcp","https://shale.test/mcp").loadRevocationClient("client").isEmpty());
        assertEquals("{call dbo.AiLoadRevocationClientV1(?,?,?)}",calls.getLast());assertEquals(4,closes.get());
    }

}
