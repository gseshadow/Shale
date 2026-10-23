package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IntegrationAccessContractTest {
    @Test void migrationIsAdditiveReceiptedAndHasOnlyBoundedAuditColumns()throws Exception {
        String sql=Files.readString(Path.of("../docs/sql/ai-integration/phase3b3.sql")).replace("\r\n","\n");
        var m=java.util.regex.Pattern.compile("@sourceHash varbinary\\(32\\)=0x([0-9a-f]{64})").matcher(sql);assertTrue(m.find());
        String normalized=sql.substring(0,m.start(1))+"0".repeat(64)+sql.substring(m.end(1));
        assertEquals(m.group(1),HexFormat.of().formatHex(AiSql.hash(normalized.getBytes(StandardCharsets.UTF_8))));
        assertFalse(sql.contains("ALTER PROCEDURE"));assertFalse(sql.contains("DROP TABLE"));assertFalse(sql.contains("ON DELETE CASCADE"));
        for(String table:List.of("IntegrationAccessAuditLog","IntegrationAccessAuditResource")) {
            assertTrue(sql.contains("FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId) ON dbo."+table));
            for(String operation:List.of("AFTER INSERT","AFTER UPDATE"))assertTrue(sql.contains("ON dbo."+table+" "+operation));
            assertTrue(sql.contains("DENY SELECT,INSERT,UPDATE,DELETE,ALTER,CONTROL ON OBJECT::dbo."+table+" TO AiIntegrationRuntime"));
        }
        assertFalse(sql.contains("REFERENCES dbo.Cases"));
        assertTrue(sql.contains("UNIQUE(InvocationId,Stage)"));assertTrue(sql.contains("@Size NOT BETWEEN 1 AND 100"));
        assertTrue(sql.contains("EXEC dbo.AiActorV1 @Admin=1"));
        String columns=sql.substring(sql.indexOf("CREATE TABLE dbo.IntegrationAccessAuditLog"),sql.indexOf("CREATE INDEX IX_IntegrationAccess_Review"));
        for(String forbidden:List.of("nvarchar(max)","Query","Hash","Prompt","Token","Jti","Email","Exception","Url","Json"))assertFalse(columns.contains(forbidden),forbidden);
        assertTrue(sql.indexOf("SELECT @leaseExpiry=")<sql.indexOf("DECLARE @now datetime2(7)=SYSUTCDATETIME()"));
        assertTrue(sql.contains("@InvocationDeadline<=@now"));assertTrue(sql.contains("@leaseExpiry<=@now"));
    }
    @Test void reviewBoundsFailBeforeAnyBorrow() {
        var service=new IntegrationAccessReview(()->{fail("Invalid review must not borrow SQL");return null;});
        for(int size:List.of(-1,0,101,10000))assertThrows(IllegalArgumentException.class,()->service.page(0,size));
        assertThrows(IllegalArgumentException.class,()->service.page(-1,10));
    }
}
