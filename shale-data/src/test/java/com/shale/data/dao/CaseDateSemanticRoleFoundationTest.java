package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import com.shale.core.model.CaseDateSemanticRole;

final class CaseDateSemanticRoleFoundationTest {
    private static String migration() throws Exception {
        return Files.readString(Path.of("../docs/sql/2026-08-10_case_date_semantic_roles_phase1.sql"));
    }

    @Test void persistedKeysAreExplicitAndNotOrdinals() {
        assertEquals("INTAKE",CaseDateSemanticRole.INTAKE.persistedKey());
        assertEquals("STATUTE_OF_LIMITATIONS",CaseDateSemanticRole.STATUTE_OF_LIMITATIONS.persistedKey());
        assertEquals("TORT_NOTICE_DEADLINE",CaseDateSemanticRole.TORT_NOTICE_DEADLINE.persistedKey());
        assertEquals(CaseDateSemanticRole.INTAKE,CaseDateSemanticRole.require(" intake "));
        assertThrows(IllegalArgumentException.class,()->CaseDateSemanticRole.require("Intake Date"));
    }

    @Test void migrationMapsOnlyCompatibilityAnchorsWithoutUpdatingExistingData() throws Exception {
        String sql=migration();
        assertTrue(sql.contains("(N'intake',N'INTAKE')"));
        assertTrue(sql.contains("(N'statute_of_limitations',N'STATUTE_OF_LIMITATIONS')"));
        assertTrue(sql.contains("(N'tort_notice_deadline',N'TORT_NOTICE_DEADLINE')"));
        for(String excluded:new String[]{"trial","hearing","mediation","deposition","discovery_deadline","date_of_injury","date_of_medical_negligence","fee_agreement_signed"})
            assertFalse(sql.contains("(N'"+excluded+"',"));
        assertFalse(sql.contains("UPDATE dbo.CaseDateTypes"));
        assertFalse(sql.contains("UPDATE dbo.CaseDates"));
        assertFalse(sql.contains("FormConfiguredFields"));
    }

    @Test void migrationIsRepeatableConflictDetectingAndTenantSafe() throws Exception {
        String sql=migration();
        assertTrue(sql.contains("IF OBJECT_ID(N'dbo.CaseDateSemanticRoles', N'U') IS NULL"));
        assertTrue(sql.contains("WHERE NOT EXISTS"));
        assertTrue(sql.contains("THROW 56803"));
        assertTrue(sql.contains("UX_CaseDateRoleMappings_Global_Active"));
        assertTrue(sql.contains("UX_CaseDateRoleMappings_Tenant_Active"));
        assertTrue(sql.contains("sec.fn_FilterByTenantOrGlobal(ShaleClientId)"));
    }

    @Test void resolverRejectsMissingAmbiguousInactiveDeletedAndCrossTenantCandidates() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDateSemanticRoleResolver.java"));
        assertTrue(source.contains("if (!rs.next())"));
        assertTrue(source.contains("if (rs.next())"));
        assertTrue(source.contains("m.IsActive=1 AND m.IsDeleted=0 AND t.IsActive=1 AND t.IsDeleted=0"));
        assertTrue(source.contains("t.ShaleClientId=? OR t.ShaleClientId IS NULL"));
        assertTrue(source.contains("tenant_mapping.ShaleClientId=?"));
        assertFalse(source.contains("Name="));
    }
}
