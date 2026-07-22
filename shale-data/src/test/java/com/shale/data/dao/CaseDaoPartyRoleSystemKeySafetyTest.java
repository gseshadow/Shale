package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CasePartyDto;

final class CaseDaoPartyRoleSystemKeySafetyTest {
    @Test
    void builtInPartyRoleSpecialBehaviorUsesVerifiedSystemKeyOnly() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));

        assertTrue(Files.readString(Path.of("../docs/sql/2026-04-06_partyroles_system_key_phase1.sql"))
                .contains("('party',   'Party')"), "Migration seed verifies the built-in Party SystemKey is party");
        assertTrue(source.contains("LOWER(LTRIM(RTRIM(COALESCE(pr.SystemKey, '')))) = 'party'"),
                "Party-role behavior should compare the stable SystemKey");
        assertFalse(source.contains("LOWER(LTRIM(RTRIM(COALESCE(pr.Name, '')))) = 'party'"),
                "Party-role behavior must not depend on the display Name");

        String caseController = Files.readString(Path.of("../shale-ui/src/main/java/com/shale/ui/controller/CaseController.java"));
        assertFalse(caseController.contains("legacyNameFallback"),
                "Desktop party-role behavior must not fall back to PartyRoleName");

        String addWorkflow = Files.readString(Path.of("../shale-ui/src/main/java/com/shale/ui/controller/support/PartyAddWorkflowDialog.java"));
        assertTrue(addWorkflow.contains("r.systemKey()"),
                "Desktop default PartyRole selection should use SystemKey while labels still use Name");
        assertFalse(addWorkflow.contains("safeText(r.name())"),
                "Desktop default PartyRole selection must not use display Name");
    }

    @Test
    void partyRoleSystemKeyRecognitionIsNullSafeNormalizedAndNameIndependent() {
        assertTrue(CaseDao.isBuiltinPartyRoleSystemKey("party"));
        assertTrue(CaseDao.isBuiltinPartyRoleSystemKey(" PARTY "));
        assertTrue(CaseDao.isBuiltinPartyRoleSystemKey("Party"));
        assertFalse(CaseDao.isBuiltinPartyRoleSystemKey(null), "Null SystemKey must be generic and safe");
        assertFalse(CaseDao.isBuiltinPartyRoleSystemKey(""), "Blank SystemKey must be generic and safe");
        assertFalse(CaseDao.isBuiltinPartyRoleSystemKey("custom-party"), "Unknown custom SystemKeys must be generic");
    }

    @Test
    void partyRoleSystemKeyResolutionDoesNotFallbackToDisplayName() throws Exception {
        Method resolver = CaseDao.class.getDeclaredMethod("resolvePartyRoleSystemKey", String.class, String.class);
        resolver.setAccessible(true);

        assertNull(resolver.invoke(null, null, "Party"),
                "A custom role named Party without the protected SystemKey must not receive special behavior");
        assertNull(resolver.invoke(null, "  ", "party"),
                "Blank SystemKey must not be backfilled from Name");
        assertTrue("party".equals(resolver.invoke(null, " PARTY ", "Renamed Client")),
                "The built-in SystemKey should still work when the display Name changes");
        assertTrue("custom".equals(resolver.invoke(null, " Custom ", "Party")),
                "Unknown custom SystemKeys should be preserved as generic custom keys");
    }

    @Test
    void userFacingCasePartyRenderingStillUsesDisplayName() {
        CasePartyDto dto = new CasePartyDto(1L, 2L, null, null, 3L, "Renamed Client", "party", "represented", true, "notes", null, null, "contact", "Ada Lovelace", "", "");

        assertTrue("Renamed Client".equals(dto.getPartyRoleName()));
        assertTrue("party".equals(dto.getPartyRoleSystemKey()));

        CaseDao.PartyRoleRow option = new CaseDao.PartyRoleRow(3L, "Renamed Client", "party");
        assertTrue("Renamed Client".equals(option.name()));
    }
}
