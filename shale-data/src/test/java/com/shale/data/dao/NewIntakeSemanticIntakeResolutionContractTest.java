package com.shale.data.dao;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NewIntakeSemanticIntakeResolutionContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test void productionServiceGatewayDelegatesSemanticResolutionToDao() throws Exception {
        String adapter = Files.readString(ROOT.resolve("shale-data/src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java"));
        assertTrue(adapter.contains("return caseGateway.resolveEffectiveCaseDateTypeId(shaleClientId, actorUserId"));
        assertTrue(adapter.contains("return caseDateDao.resolveEffectiveCaseDateTypeId(shaleClientId, actorUserId, role)"));
    }

    @Test void resolutionUsesSemanticMappingResolverNotTypeIdentityOrPresentation() throws Exception {
        String dao = Files.readString(ROOT.resolve("shale-data/src/main/java/com/shale/data/dao/CaseDateDao.java"));
        String method = dao.substring(dao.indexOf("public int resolveEffectiveCaseDateTypeId"),
                dao.indexOf("public List<CaseDateDto> listCaseDatesForCase"));
        assertTrue(method.contains("CaseDateSemanticRoleResolver.requireEffectiveTypeId"));
        assertFalse(method.contains("SystemKey"));
        assertFalse(method.contains("Name"));
    }
}
