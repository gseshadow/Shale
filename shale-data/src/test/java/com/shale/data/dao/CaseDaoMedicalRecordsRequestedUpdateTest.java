package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseDaoMedicalRecordsRequestedUpdateTest {
    @Test
    void markMedicalRecordsRequestedUpdatesOnlyRequestedFlagWithTenantAndSoftDeleteFilters() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
        int methodStart = source.indexOf("public boolean markMedicalRecordsRequested");
        int methodEnd = source.indexOf("public void addCaseUpdate", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertTrue(method.contains("SET MedicalRecordsRequested = 1"));
        assertTrue(method.contains("UpdatedAt = SYSDATETIME()"));
        assertTrue(method.contains("WHERE Id = ?"));
        assertTrue(method.contains("AND ShaleClientId = ?"));
        assertTrue(method.contains("AND MedicalRecordsRequested = 0"));
        assertTrue(method.contains("AND (IsDeleted = 0 OR IsDeleted IS NULL)"));
    }
}
