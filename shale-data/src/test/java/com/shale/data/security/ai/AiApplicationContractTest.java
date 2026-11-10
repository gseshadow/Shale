package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AiApplicationContractTest {
    @Test void invalidPublicationAndApprovalFailBeforeSql() {
        var registry=new AiApplicationRegistryService(()->{throw new AssertionError("Invalid input must not borrow SQL.");},"https://shale.test/oauth/mcp","https://shale.test/mcp");
        for(String id:List.of("http://vendor.test/client.json","https://vendor.test/","https://vendor.test/client.json#x","https://vendor.test/client.json?tenant=7"))
            assertThrows(IllegalArgumentException.class,()->registry.publishCimd(id,new byte[32],1,false,null));
        assertThrows(IllegalArgumentException.class,()->registry.publishCimd("https://vendor.test/client.json",new byte[31],1,false,null));
        var tenant=new AiTenantApplicationService(()->{throw new AssertionError("Invalid input must not borrow SQL.");});
        assertThrows(IllegalArgumentException.class,()->tenant.request(UUID.randomUUID(),new AiIntegrationSecurityService.RowVersion(new byte[8]),Set.of()));
        assertThrows(IllegalArgumentException.class,()->tenant.decide(UUID.randomUUID(),new AiIntegrationSecurityService.RowVersion(new byte[8]),AiTenantApplicationService.Decision.APPROVE,Set.of(),1));
    }
    @Test void candidateCannotRewriteConfinementOrEnableOAuth() throws Exception {
        String sql=Files.readString(Path.of("../docs/sql/ai-integration/universal-pr3.sql"));
        assertTrue(sql.contains("Confinement successor review required"),"Production compatibility boundary must fail explicitly.");
        assertFalse(sql.contains("sp_updateextendedproperty"),"Historical receipts must never be refreshed.");
        assertFalse(sql.contains("ALTER PROCEDURE"));
        assertFalse(sql.contains("INSERT dbo.AiOAuthCredential"));
        assertFalse(sql.contains("INSERT dbo.AiDelegationGrant"));
        assertTrue(sql.contains("'SUSPENDED'".replace("'","''")),"Approval must leave internal registration dormant.");
        assertTrue(sql.contains("AI_APPLICATION_APPROVAL"));
        for(String name:List.of("AiApplicationRegistryService","AiTenantApplicationService")) {
            String source=Files.readString(Path.of("src/main/java/com/shale/data/security/ai/"+name+".java"));
            assertFalse(source.contains("@Bean"));assertFalse(source.contains("org.springframework"));
        }
    }
}
