package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseMaterialsArchitectureContractTest {
    @Test
    void promptRulesRequireAuditCompatibilityForNewFeaturesAndArchitecture() throws Exception {
        String rules = Files.readString(Path.of("..", "architecture", "codex-prompt-rules.md"));
        assertTrue(rules.contains("identify sensitive reads/views and all meaningful domain or administrative mutations"));
        assertTrue(rules.contains("map them to Shale's established PHI audit, PHI read audit, or entity-action audit framework"));
        assertTrue(rules.contains("preserve tenant, actor, entity, parent, and case context where applicable"));
        assertTrue(rules.contains("Architecture documents must include this audit-compatibility review"));
        assertTrue(rules.contains("explicitly document any action intentionally not audited and why"));
    }

    @Test
    void caseMaterialsPhase0DocumentsAuditAndPhaseOneBoundary() throws Exception {
        String doc = Files.readString(Path.of("..", "architecture", "case-materials.md"));
        assertTrue(doc.contains("Case Materials must extend Shale's existing audit architecture rather than invent a second framework"));
        assertTrue(doc.contains("Global-plus-tenant overlay lookup"));
        assertTrue(doc.contains("Sensitive views/reads include opening Case Materials tab"));
        assertTrue(doc.contains("Audit and Case Timeline action matrix"));
        assertTrue(doc.contains("Phase 1 recommended boundary"));
        assertTrue(doc.contains("Blocking conflict before Phase 1"));
    }

    @Test
    void phiRegistriesIncludeCaseMaterialsObjectsAndFields() throws Exception {
        assertTrue(com.shale.core.privacy.PhiFieldRegistry.isPhi("MaterialRequests", "Title"));
        assertTrue(com.shale.core.privacy.PhiFieldRegistry.isPhi("MaterialRequestFollowUps", "Notes"));
        assertTrue(com.shale.core.privacy.PhiFieldRegistry.isPhi("MaterialItems", "StorageLocation"));
        String writeAudit = Files.readString(Path.of("src", "main", "java", "com", "shale", "data", "dao", "PhiAuditService.java"));
        assertTrue(writeAudit.contains("\"materialrequests\", 9"));
        assertTrue(writeAudit.contains("\"materialrequestfollowups\", 10"));
        assertTrue(writeAudit.contains("\"materialitems\", 11"));
        String readAudit = Files.readString(Path.of("..", "shale-ui", "src", "main", "java", "com", "shale", "ui", "services", "PhiReadAuditService.java"));
        assertTrue(readAudit.contains("\"MaterialRequest\", 9"));
        assertTrue(readAudit.contains("\"MaterialRequestFollowUp\", 10"));
        assertTrue(readAudit.contains("\"MaterialItem\", 11"));
    }

}
