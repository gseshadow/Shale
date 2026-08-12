package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class UserDetailAssignedCasesCutoverTest {
    private static String read(String relative) throws Exception {
        Path p=Path.of(relative); if(!Files.exists(p)) p=Path.of("shale-ui").resolve(relative); return Files.readString(p);
    }
    @Test void productionServiceDelegatesToAuthoritativeProjectionAndPreservesCardRow() throws Exception {
        String service=read("src/main/java/com/shale/ui/services/UserDetailService.java");
        assertTrue(service.contains("caseSummaryDao.listActiveAssignedForUserDetail(shaleClientId, userId, ASSIGNED_CASES_LIMIT)"));
        assertTrue(service.contains("private static CaseRow toCaseRow(CaseGridRow row)"));
        assertTrue(service.contains("row.dateOfIncident()"));
        assertFalse(service.contains("CaseDao caseDao"));
    }
    @Test void controllerKeepsAsyncGenerationAndSelectedUserTenantStalenessGuards() throws Exception {
        String controller=read("src/main/java/com/shale/ui/controller/UserController.java");
        assertTrue(controller.contains("dbExec.submit(() ->"));
        assertTrue(controller.contains("userDetailService.loadAssignedCases(shaleClientId, targetUserId)"));
        assertTrue(controller.contains("requestId != assignedCasesRefreshSequence || currentUser == null || currentUser.id() != targetUserId"));
        assertTrue(controller.contains("userDetailCache.matches(targetUserId, currentUser.shaleClientId())"));
        assertTrue(controller.contains("createAssignedCaseCard(row)"));
    }
    @Test void productionCompositionInjectsCaseSummaryDao() throws Exception {
        String scene=read("src/main/java/com/shale/ui/navigation/SceneManager.java");
        assertTrue(scene.contains("new UserDetailService(userDao, new CaseSummaryDao(dbSessionProvider), taskDao)"));
    }
}
