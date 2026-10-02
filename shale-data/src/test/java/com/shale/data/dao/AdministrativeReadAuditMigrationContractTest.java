package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class AdministrativeReadAuditMigrationContractTest {
	private static String read(String relative)throws Exception{return Files.readString(Path.of("..","docs","sql",relative));}
	@Test void migrationIsAdditiveRerunnableStrictTenantAndServerTimed()throws Exception{
		String sql=read("2026-09-29_administrative_read_audit_phase6b.sql");
		for(String token:new String[]{"IF OBJECT_ID(N'dbo.AdministrativeReadAuditLog',N'U') IS NULL","Id bigint IDENTITY","ShaleClientId int NOT NULL","ActorUserId int NOT NULL","Metadata varchar(1000) NULL","DEFAULT(SYSUTCDATETIME())","APPLICATION_INSTANCE_RECENT_LIST","APPLICATION_INSTANCE_VERSION_DISTRIBUTION","FK_AdministrativeReadAuditLog_ActorTenant","FILTER PREDICATE sec.fn_FilterByTenant","AFTER INSERT","AFTER UPDATE","No seed rows"})assertTrue(sql.contains(token),token);
		for(String forbidden:new String[]{"DROP TABLE","ALTER COLUMN","fn_FilterByTenantOrGlobal","INSERT dbo.AdministrativeReadAuditLog"})assertFalse(sql.contains(forbidden),forbidden);
	}
	@Test void verifierCoversSchemaIsolationAndPriorPhase()throws Exception{
		String sql=read("verification/2026-09-29_administrative_read_audit_phase6b_verification.sql");
		for(String check:new String[]{"ExactColumns","TrustedForeignKeys","ClosedReadTypes","ServerUtcDefault","MetadataBoundAndResultCheck","ExpectedIndex","StrictRlsPredicates","NoOverlayPredicate","NoSeedRows","PriorPhaseTableIntact"})assertTrue(sql.contains(check),check);
		String rls=read("verification/2026-09-29_administrative_read_audit_phase6b_rls.sql");assertTrue(rls.contains("CrossTenantFiltered"));assertTrue(rls.contains("expected 33504"));assertTrue(rls.contains("non-dbo"));
	}
}
