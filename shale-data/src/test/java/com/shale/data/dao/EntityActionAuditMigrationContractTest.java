package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class EntityActionAuditMigrationContractTest {
	@Test
	void migrationCreatesStrictTenantOwnedAppendOnlyAuditTable() throws Exception {
		String sql = Files.readString(Path.of("..", "docs", "sql", "2026-07-20_entity_action_audit_phase61.sql"));
		assertTrue(sql.contains("CREATE TABLE dbo.EntityActionAuditLog"));
		assertTrue(sql.contains("ShaleClientId int NOT NULL"));
		assertTrue(sql.contains("ActorUserId int NOT NULL"));
		assertTrue(sql.contains("DEFAULT (SYSUTCDATETIME())"));
		assertTrue(sql.contains("sec.fn_FilterByTenant(ShaleClientId) ON dbo.EntityActionAuditLog"));
		assertFalse(sql.contains("fn_FilterByTenantOrGlobal"));
	}

	@Test
	void migrationDocumentsTenantVisibilityAndRollback() throws Exception {
		String sql = Files.readString(Path.of("..", "docs", "sql", "2026-07-20_entity_action_audit_phase61.sql"));
		assertTrue(sql.contains("Tenant 7 / tenant 8 visibility verification"));
		assertTrue(sql.contains("Rollback guidance"));
	}
}
