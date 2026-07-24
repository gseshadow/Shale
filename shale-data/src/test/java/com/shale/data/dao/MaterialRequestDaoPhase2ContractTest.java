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

    @Test void materialRequestListUsesSchemaCompatibleDisplayExpressionsAndMapperAliases() {
        assertFalse(DAO.contains("rbu.DisplayName"));
        assertFalse(DAO.contains("au.DisplayName"));
        assertFalse(DAO.contains("u.DisplayName AS AttemptedByDisplayName"));
        assertTrue(DAO.contains("mt.Name AS MaterialTypeName"));
        assertTrue(DAO.contains("mt.SystemKey AS MaterialTypeSystemKey"));
        assertTrue(DAO.contains("org.Name AS RequestedFromOrganizationName"));
        assertTrue(DAO.contains("name_first"));
        assertTrue(DAO.contains("name_last"));
        assertTrue(DAO.contains("AS RequestedByDisplayName"));
        assertTrue(DAO.contains("AS AssignedToDisplayName"));
        assertTrue(DAO.contains("COALESCE(NULLIF(LTRIM(RTRIM("));
        assertTrue(DAO.contains(".Name)), '')"));
        assertTrue(DAO.contains("CONCAT("));
        assertTrue(DAO.contains(".FirstName"));
        assertTrue(DAO.contains(".LastName"));
        assertTrue(DAO.contains(".WorkName"));
        for (String label : new String[]{"Id","ShaleClientId","CaseId","MaterialTypeId","MaterialTypeName","MaterialTypeSystemKey","Status","RequestedByDisplayName","AssignedToDisplayName","RequestedFromContactDisplayName","RequestedFromOrganizationName","RequestedAt","ExpectedResponseDate","NextFollowUpAt","LastFollowUpAt","UpdatedAt","RowVer"}) {
            assertTrue(DAO.contains("\"" + label + "\""), label);
        }
        assertTrue(DAO.contains("mr.ShaleClientId=? AND mr.CaseId=? AND mr.IsDeleted=0"));
        assertTrue(DAO.contains("ISNULL(c.IsDeleted,0)=0"));
    }

    @Test void requestMutationCommandsExposeFocusedCreateAndAuditedUpdate() {
        String port = read("shale-core/src/main/java/com/shale/core/service/MaterialRequestServicePort.java");
        String adapter = read("shale-data/src/main/java/com/shale/data/service/adapter/MaterialRequestServiceAdapter.java");
        assertTrue(port.contains("CreateMaterialRequestCommand"));
        assertTrue(port.contains("UpdateMaterialRequestCommand"));
        assertFalse(port.contains("ChangeMaterialRequestStatusCommand"));
        assertFalse(port.contains("DeleteMaterialRequestCommand"));
        assertFalse(port.contains("RecordMaterialRequestFollowUpCommand"));
        assertTrue(adapter.contains("createMaterialRequest"));
        assertTrue(adapter.contains("updateMaterialRequest"));
        assertFalse(adapter.contains("changeMaterialRequestStatus"));
        assertFalse(adapter.contains("deleteMaterialRequest"));
        assertFalse(adapter.contains("recordFollowUp"));
        assertTrue(DAO.contains("INSERT dbo.MaterialRequests"));
        assertTrue(DAO.contains("UPDATE dbo.MaterialRequests SET MaterialTypeId"));
        assertFalse(DAO.contains("UPDATE dbo.MaterialRequests SET Status"));
        assertFalse(DAO.contains("UPDATE dbo.MaterialRequests SET IsDeleted=1"));
        assertFalse(DAO.contains("INSERT dbo.MaterialRequestFollowUps"));
    }

    @Test void followUpsRemainAppendOnlyReadHistoryInTheApplicationPort() {
        assertFalse(DAO.contains("UPDATE dbo.MaterialRequestFollowUps"));
        assertFalse(DAO.contains("DELETE FROM dbo.MaterialRequestFollowUps"));
        assertTrue(DAO.contains("ORDER BY f.AttemptedAt, f.Id"));
        assertEquals(0, Arrays.stream(MaterialRequestServicePort.class.getMethods()).filter(m -> m.getName().toLowerCase().contains("followup") && (m.getName().toLowerCase().contains("update") || m.getName().toLowerCase().contains("delete") || m.getName().toLowerCase().contains("record"))).count());
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
