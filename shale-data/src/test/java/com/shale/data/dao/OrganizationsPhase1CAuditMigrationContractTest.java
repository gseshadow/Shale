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
		assertTrue(s.contains("SET XACT_ABORT ON")&&s.contains("BEGIN TRANSACTION")&&s.contains("ROLLBACK TRANSACTION"));
		assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId') IS NOT NULL"));
		assertTrue(s.contains("('ORGANIZATION_TYPE'), ('ORGANIZATION_ORGANIZATION_TYPE')"));
		assertTrue(s.contains("Preserve extras only"));
		assertFalse(s.contains("INSERT dbo.EntityActionAuditLog"));
		assertFalse(s.matches("(?is).*\\b(?:UPDATE|DELETE)\\s+dbo\\.EntityActionAuditLog.*"));
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
