package com.shale.data.dao;

import com.shale.core.dto.MaterialRequestFollowUpDto;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.data.service.adapter.MaterialRequestServiceAdapter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestDaoPhase2ContractTest {
    private static final String DAO = read("shale-data/src/main/java/com/shale/data/dao/MaterialRequestDao.java");

    @Test void effectiveMaterialTypesUseOverlayResetMarkerOrderingAndTenantContext() {
        assertTrue(DAO.contains("ROW_NUMBER() OVER (PARTITION BY SystemKey"));
        assertTrue(DAO.contains("CASE WHEN ShaleClientId = ? THEN 0 ELSE 1 END"));
        assertTrue(DAO.contains("rn = 1 AND IsDeleted = 0 AND IsActive = 1"));
        assertTrue(DAO.contains("ShaleClientId = ? AND SystemKey IS NULL AND IsDeleted = 0 AND IsActive = 1"));
        assertTrue(DAO.contains("ORDER BY SortOrder, Name, Id"));
        assertTrue(DAO.contains("verifyTenant(con, shaleClientId)"));
    }

    @Test void requestReadsAreTenantCaseScopedAndSummaryDoesNotSelectPhiDescription() {
        assertTrue(DAO.contains("mr.ShaleClientId=? AND mr.CaseId=? AND mr.IsDeleted=0"));
        assertTrue(DAO.contains("JOIN dbo.Cases c ON c.Id=mr.CaseId AND c.ShaleClientId=mr.ShaleClientId"));
        assertTrue(DAO.contains("CAST(NULL AS nvarchar(max)) AS Description"));
        assertTrue(DAO.contains("mt.ShaleClientId=mr.ShaleClientId OR mt.ShaleClientId IS NULL"));
        assertTrue(DAO.contains("rs.getBytes(\"RowVer\")"));
    }

    @Test void mutationsOwnTransactionTouchCaseAndAppendEntityAudit() {
        assertTrue(DAO.contains("con.setAutoCommit(false)"));
        assertTrue(DAO.contains("rollback(con)"));
        assertTrue(DAO.contains("touchCase(con"));
        assertTrue(DAO.contains("entityActionAuditDao.append(con"));
        assertTrue(DAO.contains("EntityActionAuditEvent.Action.CREATED"));
        assertTrue(DAO.contains("EntityActionAuditEvent.Action.UPDATED"));
        assertTrue(DAO.contains("EntityActionAuditEvent.Action.STATUS_CHANGED"));
        assertTrue(DAO.contains("EntityActionAuditEvent.Action.DELETED"));
        assertTrue(DAO.contains("EntityActionAuditEvent.Action.FOLLOW_UP_ADDED"));
    }

    @Test void validationCoversRelationshipsLifecycleConcurrencyAndPhi() {
        assertTrue(DAO.contains("validateMaterialType"));
        assertTrue(DAO.contains("masked by tenant override"));
        assertTrue(DAO.contains("validateUser"));
        assertTrue(DAO.contains("dbo.Contacts"));
        assertTrue(DAO.contains("dbo.Organizations"));
        assertTrue(DAO.contains("assertRowVer"));
        assertTrue(DAO.contains("Closed material requests cannot be reopened"));
        assertTrue(DAO.contains("phiAuditService.auditCreate"));
        assertTrue(DAO.contains("phiAuditService.auditUpdate"));
    }

    @Test void followUpsAreAppendOnlyAndNoApplicationPortExposesUpdateOrDelete() {
        assertTrue(DAO.contains("INSERT dbo.MaterialRequestFollowUps"));
        assertFalse(DAO.contains("UPDATE dbo.MaterialRequestFollowUps"));
        assertFalse(DAO.contains("DELETE FROM dbo.MaterialRequestFollowUps"));
        assertTrue(DAO.contains("ORDER BY f.AttemptedAt, f.Id"));
        assertEquals(0, Arrays.stream(MaterialRequestServicePort.class.getMethods()).filter(m -> m.getName().toLowerCase().contains("followup") && (m.getName().toLowerCase().contains("update") || m.getName().toLowerCase().contains("delete"))).count());
    }

    @Test void sensitiveDetailAndHistoryReadsUseEstablishedReadAuditSink() throws Exception {
        String adapter = read("shale-data/src/main/java/com/shale/data/service/adapter/MaterialRequestServiceAdapter.java");
        assertTrue(adapter.contains("SensitiveReadAuditSink"));
        assertTrue(adapter.contains("CASE_MATERIALS_REQUEST_DETAIL"));
        assertTrue(adapter.contains("CASE_MATERIALS_FOLLOW_UP_HISTORY"));
        Method detail = MaterialRequestServiceAdapter.class.getMethod("getMaterialRequest", long.class, long.class, int.class, int.class);
        Method history = MaterialRequestServiceAdapter.class.getMethod("listFollowUps", long.class, long.class, int.class, int.class);
        assertNotNull(detail); assertNotNull(history);
    }

    @Test void scopeGuardsNoMaterialItemApplicationOrUiApiStorage() {
        assertFalse(DAO.contains("MaterialItemDao"));
        assertFalse(DAO.contains("CaseTimeline"));
        assertFalse(DAO.contains("Notification"));
        assertFalse(DAO.contains("ExternalLinks"));
        assertFalse(DAO.contains("Calendar"));
        assertTrue(MaterialRequestFollowUpDto.class.isRecord());
    }

    private static String read(String path) { try { return Files.readString(Files.exists(Path.of(path)) ? Path.of(path) : Path.of("..").resolve(path)); } catch (Exception e) { throw new AssertionError(e); } }
}
