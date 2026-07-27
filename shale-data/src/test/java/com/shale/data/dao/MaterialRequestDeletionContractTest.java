package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class MaterialRequestDeletionContractTest {
    private static final String DAO=read("src/main/java/com/shale/data/dao/MaterialRequestDao.java");
    @Test void softDeleteIsScopedConcurrentAuditedAndDoesNotCascade(){
        assertTrue(DAO.contains("ShaleClientId=? AND CaseId=? AND Id=? AND IsDeleted=0 AND RowVer=?"));
        assertTrue(DAO.contains("DeletedAt=SYSUTCDATETIME(),DeletedByUserId=?"));
        assertTrue(DAO.contains("phi.auditDelete"));
        assertTrue(DAO.contains("EntityActionAuditEvent.Action.DELETED"));
        assertTrue(DAO.contains("touchCase(con,c.caseId(),c.shaleClientId())"));
        String method=DAO.substring(DAO.indexOf("public void softDelete(DeleteMaterialRequestCommand"),DAO.indexOf("private MaterialRequestDetailDto findForDelete"));
        assertFalse(method.contains("MaterialRequestFollowUps"));
        assertFalse(method.contains("MaterialItems"));
        assertFalse(method.contains("DELETE FROM"));
    }
    @Test void deletedRowsRequireExplicitListOption(){
        assertTrue(DAO.contains("listMaterialRequests(caseId,tenant,false)"));
        assertTrue(DAO.contains("(?=1 OR mr.IsDeleted=0)"));
    }
    private static String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new AssertionError(e);}}
}
