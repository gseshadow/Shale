package com.shale.data.security.ai;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/** Offline dispatch guard; actual SQL/receipt behavior is covered by the local SQL fixture. */
class Phase1RDatabaseIdentityContractTest {
    private static String engine()throws Exception{return Files.readString(Path.of("../docs/sql/phase1r/engine.sql")).replace("\r\n","\n");}
    @Test void incarnationQueriesAreFixedDynamicBatchesWithDisjointCatalogRequirements()throws Exception {
        String sql=engine();var queries=new ArrayList<String>();
        var matcher=Pattern.compile("EXEC\\s+sys\\.sp_executesql\\s+N'((?:[^']|'')*)'\\s*,\\s*N'@Identity",Pattern.CASE_INSENSITIVE).matcher(sql);
        while(matcher.find())queries.add(matcher.group(1).replace("''","'"));
        assertEquals(2,queries.size(),"Exactly two separately bound platform identity queries required");
        assertTrue(queries.get(0).contains("physical_database_name"));assertTrue(queries.get(0).contains("FROM sys.databases"));
        assertFalse(queries.get(0).contains("database_recovery_status"),"Azure must never bind the recovery catalog");
        assertTrue(queries.get(1).contains("FROM sys.database_recovery_status"));
        assertFalse(queries.get(1).contains("physical_database_name"),"Older SQL Server must not bind an Azure-specific column");
        // No recovery catalog reference may escape a string literal into the common SQL batch.
        String common=sql.replaceAll("(?s)/\\*.*?\\*/|--[^\\r\\n]*","").replaceAll("N?'(?:[^']|'')*'","''");
        assertFalse(common.contains("sys.database_recovery_status"));
        assertFalse(common.contains("physical_database_name"));
        assertTrue(sql.contains("IF @EngineEdition=5"));assertTrue(sql.contains("ELSE IF @EngineEdition IN(2,3,4,8)"));
        assertTrue(sql.contains("ELSE THROW 58515"),"Unknown engines must not inherit a supported identity contract");
    }
    @Test void receiptIdentityIsResolvedOnceAndUsedForBothValidationAndCreation()throws Exception {
        String sql=engine();
        assertEquals(1,Pattern.compile("FROM sys\\.database_recovery_status").matcher(sql).results().count());
        assertTrue(sql.contains("@ReceiptVersion=1"));assertTrue(sql.contains("@ReceiptVersion=2"));
        assertTrue(sql.contains("N'AZURE_SQL_DATABASE_PHYSICAL:'"));
        assertTrue(sql.contains("@ReceiptVersion AS version"));assertTrue(sql.contains("@DatabaseIdentity AS [database]"));
        assertTrue(sql.contains("JSON_VALUE(@Receipt,'$.version')"));assertTrue(sql.contains("JSON_VALUE(@Receipt,'$.database')"));
        assertTrue(sql.contains("@IdentityRows IS NULL OR @IdentityRows<>1 OR @DatabaseIdentity IS NULL"));
        assertTrue(sql.contains("IF XACT_STATE()<>0 ROLLBACK;\n THROW;"),"SQL errors must roll back and propagate");
    }
}
