package com.shale.data.dao;

import com.shale.core.dto.RequestMethodDto;
import com.shale.core.dto.RequestStatusDto;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.data.service.adapter.MaterialRequestServiceAdapter;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestLookupContractTest {
    private static final String DAO = read("shale-data/src/main/java/com/shale/data/dao/MaterialRequestDao.java");
    private static final String PORT = read("shale-core/src/main/java/com/shale/core/service/MaterialRequestServicePort.java");
    private static final String ADAPTER = read("shale-data/src/main/java/com/shale/data/service/adapter/MaterialRequestServiceAdapter.java");

    @Test void requestLookupDtosExposeSelectionDisplayAndLifecycleFields() throws Exception {
        assertTrue(RequestMethodDto.class.isRecord());
        assertTrue(RequestStatusDto.class.isRecord());
        assertEquals(9, RequestMethodDto.class.getRecordComponents().length);
        assertEquals(9, RequestStatusDto.class.getRecordComponents().length);
        assertHasRecordComponent(RequestMethodDto.class, "id");
        assertHasRecordComponent(RequestMethodDto.class, "shaleClientId");
        assertHasRecordComponent(RequestMethodDto.class, "systemKey");
        assertHasRecordComponent(RequestMethodDto.class, "name");
        assertHasRecordComponent(RequestMethodDto.class, "color");
        assertHasRecordComponent(RequestMethodDto.class, "sortOrder");
        assertHasRecordComponent(RequestMethodDto.class, "active");
        assertHasRecordComponent(RequestMethodDto.class, "deleted");
        assertHasRecordComponent(RequestMethodDto.class, "rowVer");
        assertHasRecordComponent(RequestStatusDto.class, "id");
        assertHasRecordComponent(RequestStatusDto.class, "shaleClientId");
        assertHasRecordComponent(RequestStatusDto.class, "systemKey");
        assertHasRecordComponent(RequestStatusDto.class, "name");
        assertHasRecordComponent(RequestStatusDto.class, "color");
        assertHasRecordComponent(RequestStatusDto.class, "sortOrder");
        assertHasRecordComponent(RequestStatusDto.class, "active");
        assertHasRecordComponent(RequestStatusDto.class, "deleted");
        assertHasRecordComponent(RequestStatusDto.class, "rowVer");
    }

    @Test void requestMethodsAndStatusesUseTheSameEffectiveOverlayQueryRules() {
        assertTrue(DAO.contains("effectiveRequestLookupSql(\"dbo.RequestMethods\")"));
        assertTrue(DAO.contains("effectiveRequestLookupSql(\"dbo.RequestStatuses\")"));
        assertTrue(DAO.contains("ROW_NUMBER() OVER (PARTITION BY SystemKey ORDER BY CASE WHEN ShaleClientId = ? THEN 0 ELSE 1 END, Id) AS rn"));
        assertTrue(DAO.contains("WHERE (ShaleClientId = ? OR ShaleClientId IS NULL) AND SystemKey IS NOT NULL"));
        assertTrue(DAO.contains("WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1"));
        assertTrue(DAO.contains("WHERE ShaleClientId = ? AND SystemKey IS NULL AND IsDeleted = 0 AND IsActive = 1"));
        assertTrue(DAO.contains("ORDER BY SortOrder, Name, Id"));
        assertTrue(DAO.contains("SELECT Id,ShaleClientId,SystemKey,Name,Color,SortOrder,IsActive,IsDeleted,RowVer FROM dbo.RequestMethods"));
    }

    @Test void requestLookupsVerifyTenantContextAndDoNotReadOtherTenantRows() {
        assertTrue(DAO.contains("verifyTenant(con, shaleClientId)"));
        assertFalse(DAO.contains("FROM dbo.RequestMethods\n                WHERE SystemKey IS NULL"));
        assertFalse(DAO.contains("FROM dbo.RequestStatuses\n                WHERE SystemKey IS NULL"));
        assertTrue(DAO.contains("ShaleClientId = ? OR ShaleClientId IS NULL"));
        assertTrue(DAO.contains("ShaleClientId = ? AND SystemKey IS NULL"));
    }

    @Test void servicePortAndAdapterExposeBothLookupFamilies() throws Exception {
        Method methods = MaterialRequestServicePort.class.getMethod("listEffectiveRequestMethods", int.class);
        Method statuses = MaterialRequestServicePort.class.getMethod("listEffectiveRequestStatuses", int.class);
        assertEquals(java.util.List.class, methods.getReturnType());
        assertEquals(java.util.List.class, statuses.getReturnType());
        assertTrue(PORT.contains("List<RequestMethodDto> listEffectiveRequestMethods(int shaleClientId)"));
        assertTrue(PORT.contains("List<RequestStatusDto> listEffectiveRequestStatuses(int shaleClientId)"));
        assertTrue(ADAPTER.contains("return dao.listEffectiveRequestMethods(shaleClientId)"));
        assertTrue(ADAPTER.contains("return dao.listEffectiveRequestStatuses(shaleClientId)"));
        assertNotNull(MaterialRequestServiceAdapter.class.getMethod("listEffectiveRequestMethods", int.class));
        assertNotNull(MaterialRequestServiceAdapter.class.getMethod("listEffectiveRequestStatuses", int.class));
    }

    @Test void requestLookupAdministrationMutationsUseSoftDeleteAndServiceLayerOnly() {
        assertTrue(DAO.contains("INSERT dbo.RequestMethods"));
        assertTrue(DAO.contains("INSERT dbo.RequestStatuses"));
        assertTrue(DAO.contains("UPDATE dbo.RequestMethods"));
        assertTrue(DAO.contains("UPDATE dbo.RequestStatuses"));
        assertTrue(DAO.contains("INSERT dbo.RequestMethods(ShaleClientId,SystemKey,Name,Color,SortOrder,IsActive,IsDeleted"));
        assertTrue(DAO.contains("UPDATE dbo.RequestMethods SET Name=?,Color=?,SortOrder=?,IsActive=?"));
        assertTrue(DAO.contains("IsDeleted=1,IsActive=0"));
        assertFalse(DAO.contains("DELETE FROM dbo.RequestMethods"));
        assertFalse(DAO.contains("DELETE FROM dbo.RequestStatuses"));
    }

    private static void assertHasRecordComponent(Class<?> type, String name) {
        assertTrue(java.util.Arrays.stream(type.getRecordComponents()).anyMatch(c -> c.getName().equals(name)), name);
    }

    private static String read(String path) {
        try { return Files.readString(Files.exists(Path.of(path)) ? Path.of(path) : Path.of("..").resolve(path)); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
