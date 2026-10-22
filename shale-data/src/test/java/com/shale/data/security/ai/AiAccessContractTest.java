package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiAccessContractTest {
    @Test void parserAcceptsOnlyOneIssuedOpaqueHeader() {
        String token=AiOAuthCredentialStore.opaque();
        assertEquals(token,AiBearerParser.extract(List.of("Bearer "+token)));
        assertEquals(token,AiBearerParser.extract(List.of("bEaReR "+token)));
        for(String value:List.of(token,"Basic "+token,"Bearer  "+token," Bearer "+token,"Bearer\t"+token,
                "Bearer "+token+" ","Bearer "+token+",x","Bearer "+token+"\r\n","Bearer "+"x".repeat(8192),"Bearer invalid.jwt","Bearer "+"é".repeat(43)))
            assertThrows(SecurityException.class,()->AiBearerParser.extract(List.of(value)));
        assertThrows(SecurityException.class,()->AiBearerParser.extract(List.of()));
        assertThrows(SecurityException.class,()->AiBearerParser.extract(List.of("Bearer "+token,"Bearer "+token)));
        assertThrows(SecurityException.class,()->AiBearerParser.extract(null));
    }
    @Test void migrationIsReceiptedAdditiveAndProcedureConfined()throws Exception {
        String s=Files.readString(Path.of("../docs/sql/ai-integration/phase3b2d.sql")).replace("\r\n","\n");
        var m=java.util.regex.Pattern.compile("@sourceHash varbinary\\(32\\)=0x([0-9a-f]{64})").matcher(s);assertTrue(m.find());
        String normalized=s.substring(0,m.start(1))+"0".repeat(64)+s.substring(m.end(1));
        assertEquals(m.group(1),HexFormat.of().formatHex(AiSql.hash(normalized.getBytes(StandardCharsets.UTF_8))));
        assertFalse(s.contains("CREATE TABLE"));assertFalse(s.contains("ALTER PROCEDURE"));assertFalse(s.contains("SELECT *"));
        assertTrue(s.contains("c.Kind=''ACCESS''"));assertFalse(s.contains("c.Kind=''REFRESH''"));
        assertTrue(s.contains("GRANT EXECUTE ON OBJECT::dbo.ResolveAiAccessV1 TO AiIntegrationBootstrap"));
        assertFalse(s.contains("GRANT EXECUTE ON OBJECT::dbo.ResolveAiAccessV1 TO AiIntegrationRuntime"));
        for(String authority:List.of("c.RevokedAt IS NULL","s.RevokedAt IS NULL","g.RevokedAt IS NULL","i.RevokedAt IS NULL","k.RevokedAt IS NULL",
                "s.CapabilityRevision=@revision","@deadline<=@now","s.ConsentAt=g.ConsentedAt","c.Generation<=s.RefreshGeneration","ROLLBACK TRANSACTION; THROW"))assertTrue(s.contains(authority),authority);
    }
    @Test void validatorIsExplicitAndHasNoOrdinaryAuthorityFallbackOrRoute()throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/data/security/ai/AiAccessTokenValidator.java"));
        for(String forbidden:List.of("ThreadLocal","getSession(","SecurityContextHolder","@RequestMapping","CaseDao","ServerPrincipal","getEmail"))assertFalse(source.contains(forbidden),forbidden);
        assertTrue(source.contains("new RuntimeSessionService(runtime)"));assertTrue(source.contains("SESSION_CONTEXT(N'PrincipalUserId')"));
        assertTrue(source.contains("ResolveAiAccessV1"));assertTrue(source.contains("AiValidateAccessV1"));
        assertTrue(source.contains("Arrays.fill(digest"));
    }
    @Test void accessProvenanceHasNoCredentialOrMutableCarrierField() throws Exception {
        var fields=VerifiedIntegrationContext.class.getDeclaredFields();
        for(var field:fields) {
            assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()),field.getName());
            assertNotEquals(byte[].class,field.getType(),"No digest or credential bytes retained");
            assertFalse(field.getName().toLowerCase().matches(".*(token|password|email|prompt|assertion|code).*"),field.getName());
        }
        var scopes=new HashSet<>(com.shale.core.security.ai.AiScope.fromMask(3));
        var context=new VerifiedIntegrationContext(7,72,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),scopes,null,
                "bounded-client",null,1L,"https://shale.test/oauth/mcp","https://shale.test/mcp",1,1,1,1,
                java.time.Instant.parse("2030-01-01T00:00:00Z"),UUID.randomUUID(),null);
        scopes.clear();assertEquals(2,context.effectiveScopes().size());
        assertEquals("VerifiedIntegrationContext[redacted]",context.toString());
    }

}
