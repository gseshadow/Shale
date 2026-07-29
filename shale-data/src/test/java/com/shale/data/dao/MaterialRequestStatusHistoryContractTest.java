package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestStatusHistoryContractTest {
    private static final Path DAO=Path.of("src/main/java/com/shale/data/dao/MaterialRequestDao.java");
    private static final Path MIGRATION=Path.of("../docs/sql/2026-07-29_material_request_updates_status_history.sql");

    @Test void migrationAddsIdentityAndConservativeSingleOccurrenceBackfill() throws Exception {
        String sql=Files.readString(MIGRATION);
        assertTrue(sql.contains("StatusSystemKey"));assertTrue(sql.contains("StatusDisplayValue"));
        assertTrue(sql.contains("NOT EXISTS"));assertTrue(sql.contains("STATUS_INITIAL"));
        assertFalse(sql.contains("SortOrder"));
    }

    @Test void createAndChangedStatusAppendInsideExistingTransactions() throws Exception {
        String source=Files.readString(DAO);
        assertTrue(source.contains("appendStatusUpdate(con,c.shaleClientId(),c.caseId(),id,\"SYSTEM_EVENT\",\"CREATED\""));
        assertTrue(source.contains("if(statusChanged)appendStatusUpdate"));
        assertTrue(source.contains("if(!meaningful&&!explicitScheduleChange){con.rollback();return prior;}"));
        assertTrue(source.contains("if(!Arrays.equals(prior.rowVer(),c.rowVer()))throw"));
        assertTrue(source.contains("con.commit()"));
    }

    @Test void historiesAreOneBulkChronologicalTenantScopedQuery() throws Exception {
        String source=Files.readString(DAO);
        assertTrue(source.contains("MaterialRequestId IN ("));
        assertTrue(source.contains("mu.ShaleClientId=? AND mu.CaseId=?"));
        assertTrue(source.contains("ORDER BY mu.CreatedAt,mu.Id"));
    }
}
