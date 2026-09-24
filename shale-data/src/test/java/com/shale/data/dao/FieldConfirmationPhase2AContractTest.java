package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class FieldConfirmationPhase2AContractTest {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
    private static String read(String path)throws Exception{return Files.readString(ROOT.resolve(path)).replace("\r\n","\n");}

    @Test void schemaUsesStableKeysIndependentSettingsAndImmutablePolicySnapshots()throws Exception{
        String s=read("docs/sql/2026-09-23_field_confirmation_foundation_phase2a.sql");
        assertAll("stable historical policy contract",
                ()->assertTrue(s.contains("PRIMARY KEY(ShaleClientId,FormKey,FieldKey)")),
                ()->assertTrue(s.contains("RequiresConfirmation bit NOT NULL")),
                ()->assertFalse(s.contains("IsRequired")),
                ()->assertTrue(s.contains("PolicyRevisionSnapshot")),
                ()->assertTrue(s.contains("RequiredFirmWideRoleDefinitionId")),
                ()->assertFalse(s.contains("CaseTeam")),
                ()->assertFalse(s.matches("(?is).*INSERT\\s+dbo\\.FieldConfirmationPolicies.*")));
    }

    @Test void caseDateTargetAndBusinessRevisionAreTenantConstrainedAndTyped()throws Exception{
        String s=read("docs/sql/2026-09-23_field_confirmation_foundation_phase2a.sql");
        assertAll("typed tenant-safe target",
                ()->assertTrue(s.contains("ValueRevision bigint NOT NULL")),
                ()->assertTrue(s.contains("TargetType IN('CASE_DATE')")),
                ()->assertTrue(s.contains("REFERENCES dbo.CaseDates(Id,ShaleClientId)")),
                ()->assertTrue(s.contains("UX_CaseDateConfirmationTargets_ValueRevision")),
                ()->assertTrue(s.contains("r.BusinessValueRevision<>i.BusinessValueRevision")),
                ()->assertTrue(s.contains("(N'dbo.FormFieldPolicyKeys'),(N'dbo.FieldConfirmationPolicies'),(N'dbo.SavedValueConfirmationRequirements')")),
                ()->assertTrue(s.contains("ADD FILTER PREDICATE sec.fn_FilterByTenant(ShaleClientId)")));
    }

    @Test void readDoesNotRetroactivelyApplyCurrentPolicyAndPreservesSemanticAuthority()throws Exception{
        String dao=read("shale-data/src/main/java/com/shale/data/dao/CaseDateDao.java");
        String architecture=read("architecture/field-confirmation.md");
        assertAll("read and semantic compatibility",
                ()->assertTrue(dao.contains("target.BusinessValueRevision=cd.ValueRevision")),
                ()->assertTrue(dao.contains("Status.NOT_REQUIRED")),
                ()->assertFalse(dao.substring(dao.indexOf("listCaseDateConfirmationsForCase"),dao.indexOf("private record ConfirmationRead")).contains("FieldConfirmationPolicies")),
                ()->assertTrue(architecture.contains("CaseDateTypeSemanticRoleMappings")),
                ()->assertTrue(architecture.contains("STATUTE_OF_LIMITATIONS")),
                ()->assertTrue(architecture.contains("TORT_NOTICE_DEADLINE")));
    }

    @Test void verificationIsReadOnlyAndNeedsNoOperatorPlaceholders()throws Exception{
        String v=read("docs/sql/2026-09-23_field_confirmation_foundation_phase2a_verify.sql");
        assertAll("runnable verification",
                ()->assertFalse(v.contains("REPLACE_WITH")),()->assertFalse(v.contains("ExpectedTenantCount")),
                ()->assertFalse(v.contains("Acknowledgement")),()->assertTrue(v.contains("FindingCount")),
                ()->assertFalse(v.matches("(?is).*(INSERT|UPDATE|DELETE|MERGE|ALTER|CREATE|DROP)\\s+dbo\\..*")));
    }

    @Test void phaseOneScriptsMatchDeployedExecutionMechanicsAndEvidence()throws Exception{
        String a=read("docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a.sql");
        String av=read("docs/sql/2026-09-23_firm_wide_roles_foundation_phase1a_verify.sql");
        String b=read("docs/sql/2026-09-23_firm_wide_roles_audit_allowlist_phase1b.sql");
        assertAll("production reconciliation",
                ()->assertTrue(a.contains("COLLATE DATABASE_DEFAULT")),
                ()->assertTrue(av.contains("tenant IDs 7, 8, and 9")),
                ()->assertFalse(av.contains("REPLACE_WITH_APPROVED_DATABASE")),
                ()->assertTrue(b.contains("EXEC sys.sp_executesql @dropSql")),
                ()->assertTrue(b.contains("EXEC sys.sp_executesql @addSql")));
    }
}
