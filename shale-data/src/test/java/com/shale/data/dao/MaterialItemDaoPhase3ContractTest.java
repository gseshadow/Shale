package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

final class MaterialItemDaoPhase3ContractTest {
    private static String read(String p) throws Exception { return Files.readString(Path.of(p)); }
    private static final String DAO;
    private static final String PORT;
    private static final String ADAPTER;
    static { try { DAO=read("src/main/java/com/shale/data/dao/MaterialItemDao.java"); PORT=read("../shale-core/src/main/java/com/shale/core/service/MaterialItemServicePort.java"); ADAPTER=read("src/main/java/com/shale/data/service/adapter/MaterialItemServiceAdapter.java"); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }

    @Test void exposesFocusedServiceOperationsWithoutHardDeleteOrUiApiScope(){
        for(String op: new String[]{"listMaterialItems","getMaterialItem","createMaterialItem","updateMaterialItem","changeMaterialItemLocation","linkMaterialItemToRequest","unlinkMaterialItemFromRequest","releaseOrReturnMaterialItem","softDeleteMaterialItem"}) assertTrue(PORT.contains(op));
        for(String command: new String[]{"CreateMaterialItemCommand","UpdateMaterialItemCommand","ChangeMaterialItemLocationCommand","LinkMaterialItemToRequestCommand","UnlinkMaterialItemFromRequestCommand","ReleaseOrReturnMaterialItemCommand","SoftDeleteMaterialItemCommand"}) assertTrue(PORT.contains("record "+command));
        assertFalse(PORT.contains("hardDelete")); assertFalse(DAO.contains("DELETE FROM dbo.MaterialItems"));
        assertFalse(DAO.contains("Controller")); assertFalse(DAO.contains("Timeline")); assertFalse(DAO.contains("upload")); assertFalse(DAO.contains("download")); assertFalse(DAO.contains("OCR"));
    }

    @Test void readsAreTenantCaseScopedSoftDeleteAwareAndDetailAuditedOnlyAfterFound(){
        assertTrue(DAO.contains("mi.ShaleClientId=? AND mi.CaseId=?"));
        assertTrue(DAO.contains("mi.IsDeleted=0"));
        assertTrue(DAO.contains("ORDER BY mi.ReceivedAt DESC, mi.Id DESC"));
        assertTrue(DAO.contains("CAST(NULL AS nvarchar(max)) AS Description"));
        assertTrue(DAO.contains("rs.getBytes(\"RowVer\")"));
        assertTrue(ADAPTER.indexOf("MaterialItemDetailDto item=dao.findMaterialItem") < ADAPTER.indexOf("readAuditSink.auditRead"));
        assertTrue(ADAPTER.contains("CASE_MATERIALS_ITEM_DETAIL"));
    }

    @Test void mutationsUseTenantContextRowVerTransactionsCaseTouchAndAudits(){
        assertTrue(DAO.contains("SESSION_CONTEXT(N'ShaleClientId')"));
        assertTrue(DAO.contains("con.setAutoCommit(false)"));
        assertTrue(DAO.contains("con.commit()"));
        assertTrue(DAO.contains("rollback(con)"));
        assertTrue(DAO.contains("assertRowVer"));
        assertTrue(DAO.contains("expectedRowVer is required"));
        assertTrue(DAO.contains("UPDATE dbo.Cases SET UpdatedAt=SYSUTCDATETIME()"));
        assertTrue(DAO.contains("phi.auditCreate(con,actor,\"MaterialItems\""));
        assertTrue(DAO.contains("EntityActionAuditEvent.EntityType.MATERIAL_ITEM"));
        for(String action: new String[]{"CREATED","UPDATED","DELETED","LINKED","UNLINKED","LOCATION_UPDATED","RELEASED"}) assertTrue(DAO.contains("Action."+action));
    }

    @Test void validatesPhaseOneVocabularyAndRelationships(){
        for(String v: new String[]{"ELECTRONIC_FILE","PAPER","CD_DVD","EMAIL","PORTAL_ACCESS","PHYSICAL_OBJECT","OTHER","COMPLETE","PARTIAL","UNKNOWN","DUPLICATE","UNUSABLE","SUPERSEDED","IN_FIRM_CUSTODY","WITH_REVIEWER","RETURNED","RELEASED","DESTROYED"}) assertTrue(DAO.contains(v));
        assertTrue(DAO.contains("validateMaterialType"));
        assertTrue(DAO.contains("masked by tenant override"));
        assertTrue(DAO.contains("validateRequest"));
        assertTrue(DAO.contains("Material item type is not compatible"));
        assertTrue(DAO.contains("validateExternalLink"));
        assertTrue(DAO.contains("EXISTS (SELECT 1 FROM dbo.CaseLinks"));
        assertTrue(DAO.contains("validateSource"));
    }

    @Test void locationAndCustodyAreMetadataOnlyAndPreserveSingleRowSchemaLimits(){
        assertTrue(DAO.contains("StorageLocation=?,ExternalLinkId=?"));
        assertTrue(DAO.contains("ReturnReleaseNotes"));
        assertTrue(DAO.contains("has already been returned or released"));
        assertFalse(DAO.contains("MaterialCustodyEvents"));
        assertFalse(DAO.contains("Blob"));
        assertFalse(DAO.contains("FileInputStream"));
    }
}
