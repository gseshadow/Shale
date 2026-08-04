package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;

final class CaseDateTypeAdministrationContractTest {
    private static final String DAO = read("src/main/java/com/shale/data/dao/CaseDateDao.java");
    private static final String PORT = read("../shale-core/src/main/java/com/shale/core/service/CaseServicePort.java");
    private static String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @Test void backendExposesAdministrationApiAndKeepsSelectorSeparate(){
        assertTrue(PORT.contains("listCaseDateTypesForAdministration"));
        assertTrue(PORT.contains("CaseDateTypeCommand"));
        assertTrue(DAO.contains("listEffectiveCaseDateTypes"));
        assertTrue(DAO.contains("listCaseDateTypesForAdministration"));
        assertTrue(DAO.contains("WHERE rn = 1 AND IsDeleted = 0 AND IsActive = 1"));
    }
    @Test void mutationsUseTenantOwnedOverlayRowsAndRowVersion(){
        assertTrue(DAO.contains("validateAdminActor"));
        assertTrue(DAO.contains("e.shaleClientId()==null"));
        assertTrue(DAO.contains("upsertOverride"));
        assertTrue(DAO.contains("ShaleClientId=? AND RowVer=?"));
        assertTrue(DAO.contains("UPDATE dbo.CaseDateTypes SET IsDeleted=1,IsActive=0"));
        assertFalse(DAO.contains("DELETE FROM dbo.CaseDateTypes"));
        assertFalse(DAO.contains("UPDATE dbo.CalendarEvents"));
        assertFalse(DAO.contains("INSERT dbo.CalendarEvents"));
    }
    @Test void validationCoversSchemaFields(){
        assertTrue(DAO.contains("DEADLINE"));
        assertTrue(DAO.contains("#[0-9A-Fa-f]{6}"));
        assertTrue(DAO.contains("supportsTime"));
        assertTrue(DAO.contains("SystemKey is invalid"));
    }
}
