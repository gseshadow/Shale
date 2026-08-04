package com.shale.data.dao;

import com.shale.core.service.CaseServicePort.DeleteCaseDateCommand;
import com.shale.core.service.CaseServicePort.RestoreCaseDateCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseDateCommand;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaseDateDaoMutationContractTest {
    private static final String DAO = "src/main/java/com/shale/data/dao/CaseDateDao.java";
    private static final String PORT = "../shale-core/src/main/java/com/shale/core/service/CaseServicePort.java";

    @Test void commandsDefensivelyCopyExpectedRowVersions() {
        byte[] rv = {1, 2, 3};
        UpdateCaseDateCommand update = new UpdateCaseDateCommand(7, 9, 11, 13, 17, java.time.LocalDateTime.now(), null, true, null, rv);
        DeleteCaseDateCommand delete = new DeleteCaseDateCommand(7, 9, 11, 13, rv);
        RestoreCaseDateCommand restore = new RestoreCaseDateCommand(7, 9, 11, 13, rv);
        rv[0] = 99;
        assertEquals(1, update.expectedRowVer()[0]);
        assertEquals(1, delete.expectedRowVer()[0]);
        assertEquals(1, restore.expectedRowVer()[0]);
        byte[] returned = restore.expectedRowVer();
        returned[1] = 99;
        assertEquals(2, restore.expectedRowVer()[1]);
    }

    @Test void mutationsAuthorizeByTenantCaseAndOccurrenceAndNeverDateIdAlone() throws Exception {
        String source = Files.readString(Path.of(DAO));
        assertTrue(source.contains("WHERE Id=? AND ShaleClientId=? AND CaseId=? AND IsDeleted=?"));
        assertTrue(source.contains("WHERE Id=? AND ShaleClientId=? AND CaseId=? AND IsDeleted=0 AND RowVer=?"));
        assertTrue(source.contains("WHERE Id=? AND ShaleClientId=? AND CaseId=? AND IsDeleted=1 AND RowVer=?"));
        assertTrue(source.contains("validateCase(con"));
        assertTrue(source.contains("validateActor(con"));
        assertTrue(source.contains("verifyTenant(con"));
    }

    @Test void selectorTypeValidationRequiresExactActiveEffectiveWinnerButHistoricalRetentionUsesStoredRow() throws Exception {
        String source = Files.readString(Path.of(DAO));
        assertTrue(source.contains("ROW_NUMBER() OVER (PARTITION BY t.SystemKey"));
        assertTrue(source.contains("WHERE Id=? AND rn=1 AND IsActive=1 AND IsDeleted=0"));
        assertTrue(source.contains("c.caseDateTypeId()==before.typeId ? requireHistoricalType"));
        assertTrue(source.contains("requireHistoricalType(con,t,before.typeId)"));
        assertTrue(source.contains("SELECT Id, SupportsTime FROM dbo.CaseDateTypes WHERE Id=? AND (ShaleClientId=? OR ShaleClientId IS NULL)"));
    }

    @Test void mutationsUseSqlServerTimeRowVerSoftDeleteRestoreTouchAndAuditWithoutForbiddenDomains() throws Exception {
        String source = Files.readString(Path.of(DAO));
        assertTrue(source.contains("SYSUTCDATETIME()"));
        assertTrue(source.contains("RowVer=?"));
        assertTrue(source.contains("IsDeleted=1, DeletedAt=SYSUTCDATETIME(), DeletedByUserId=?"));
        assertTrue(source.contains("IsDeleted=0, DeletedAt=NULL, DeletedByUserId=NULL"));
        assertTrue(source.contains("UPDATE dbo.Cases SET UpdatedAt=SYSDATETIME()"));
        assertTrue(source.contains("phiAuditService.audit"));
        assertTrue(source.contains("EntityType.CASE_DATE"));
        assertFalse(source.contains("DELETE FROM dbo.CaseDates"));
        assertFalse(source.contains("CalendarEvents"));
        assertFalse(source.contains("UPDATE dbo.CaseDateTypes"));
        assertFalse(source.contains("INSERT dbo.CaseDateTypes"));
    }

    @Test void serviceBoundaryExposesOnlyOccurrenceMutations() throws Exception {
        String port = Files.readString(Path.of(PORT));
        assertTrue(port.contains("CaseDateDto createCaseDate(CreateCaseDateCommand command)"));
        assertTrue(port.contains("CaseDateDto updateCaseDate(UpdateCaseDateCommand command)"));
        assertTrue(port.contains("void deleteCaseDate(DeleteCaseDateCommand command)"));
        assertTrue(port.contains("CaseDateDto restoreCaseDate(RestoreCaseDateCommand command)"));
        assertFalse(port.contains("createCaseDateType"));
        assertFalse(port.contains("updateCaseDateType"));
    }
}
