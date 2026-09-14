package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class OrganizationsPhase1CAuditMigrationContractTest {
	private static Path repositoryFile(String relative) {
		Path direct=Path.of(relative); return Files.exists(direct)?direct:Path.of("..").resolve(relative);
	}
	@Test void migrationIsGuardedRerunnableAndOnlyExpandsEntityTypeAllowlist() throws Exception {
		String s=Files.readString(repositoryFile("docs/sql/2026-09-09_organizations_phase1c_audit_allowlist.sql"));
		assertTrue(s.contains("SET XACT_ABORT ON")&&s.contains("BEGIN TRANSACTION"),
				"the allowlist replacement must run transactionally with XACT_ABORT enabled");
		assertTrue(s.contains("BEGIN TRY")&&s.contains("BEGIN CATCH")
				&&s.contains("IF XACT_STATE() <> 0 ROLLBACK TRANSACTION")&&s.contains("THROW;"),
				"a failed allowlist replacement must roll back and rethrow");
		assertTrue(s.contains("IF @AuditObjectId IS NULL")&&s.contains("IF @EntityTypeColumnId IS NULL")
				&&s.contains("allowlist discovery is missing or ambiguous"),
				"replacement must fail closed unless the expected table, column, and one positive allowlist exist");
		assertTrue(s.contains("('ORGANIZATION_TYPE'), ('ORGANIZATION_ORGANIZATION_TYPE')"),
				"both Organizations Phase 1C entity types must be declared");
		assertTrue(s.contains("Preserve extras only")&&s.contains("INSERT @Allowed (Value)")
				&&s.contains("e.ConstraintObjectId = @AllowlistObjectId")
				&&s.contains("NOT EXISTS (SELECT 1 FROM @Allowed AS a WHERE a.Value = e.Value)"),
				"reruns must preserve every validated existing allowlist token without duplicating it");
		int preflight=s.indexOf("Existing EntityActionAuditLog rows contain an EntityType outside the resulting allowlist");
		int replacement=s.indexOf("ALTER TABLE dbo.EntityActionAuditLog DROP CONSTRAINT");
		assertTrue(preflight>0&&preflight<replacement,
				"existing audit rows must be validated before the allowlist constraint is replaced");
		assertTrue(s.contains("WITH CHECK ADD CONSTRAINT")
				&&s.contains("CHECK CONSTRAINT CK_EntityActionAuditLog_EntityType")
				&&s.contains("is_disabled = 0 AND is_not_trusted = 0) <> 1"),
				"the replacement allowlist must finish enabled and trusted on every run");
		assertFalse(s.matches("(?is).*\\b(?:INSERT\\s+(?:INTO\\s+)?|UPDATE\\s+|DELETE\\s+(?:FROM\\s+)?)dbo\\.EntityActionAuditLog\\b.*"),
				"the schema-only successor must not mutate audit rows");
	}
	@Test void verificationIsReadOnlyAndChecksBothTokens() throws Exception {
		String s=Files.readString(repositoryFile("docs/sql/verification/2026-09-09_organizations_phase1c_audit_allowlist_verification.sql"));
		assertTrue(s.contains("ORGANIZATION_TYPE")&&s.contains("ORGANIZATION_ORGANIZATION_TYPE"));
		assertTrue(s.contains("MatchingCount")&&s.contains("FindingCount"));
		assertFalse(s.matches("(?is).*COUNT_BIG\\(\\*\\)\\s+(?:AS\\s+)?RowCount\\b.*"));
		assertTrue(s.contains("IF @FindingCount <> 0")&&s.contains("THROW 57103"));
		assertFalse(s.matches("(?is).*\\b(?:ALTER|INSERT|UPDATE|DELETE|MERGE)\\s+(?:TABLE\\s+|INTO\\s+)?dbo\\..*"));
	}
}
