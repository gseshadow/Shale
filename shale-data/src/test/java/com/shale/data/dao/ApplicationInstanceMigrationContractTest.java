package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

class ApplicationInstanceMigrationContractTest {
	private static final Path MIGRATION=Path.of("..","docs","sql","2026-09-28_application_instances_foundation_phase4b.sql");
	private static final Path VERIFY=Path.of("..","docs","sql","verification","2026-09-28_application_instances_phase4b_verification.sql");
	@Test void schemaPreservesTenantOwnerMachineVersionLifecycleAndStrictRls()throws Exception{String s=Files.readString(MIGRATION);for(String token:new String[]{"ApplicationInstances","Id bigint IDENTITY","ShaleClientId int NOT NULL","UserId int NOT NULL","MachineId uniqueidentifier NULL","ClientType varchar(16) NOT NULL","MajorVersion int NOT NULL","MinorVersion int NOT NULL","BuildVersion int NOT NULL","StartedAt datetime2(7) NOT NULL","EndedAt datetime2(7) NULL","RowVer rowversion NOT NULL","FK_ApplicationInstances_UserTenant","ON DELETE NO ACTION","ClientType IN('DESKTOP','WEB','MOBILE')","EndedAt>=StartedAt","fn_FilterByTenant(ShaleClientId)","AFTER INSERT","AFTER UPDATE"})assertTrue(s.contains(token),token);assertFalse(s.contains("fn_FilterByTenantOrGlobal"));assertFalse(s.contains("LastHeartbeat"));}
	@Test void verifierUsesIndependentFindingCountsAndCoversPriorPhases()throws Exception{String s=Files.readString(VERIFY);assertTrue(s.contains("FindingCount"));for(String token:new String[]{"ExactColumns","RequiredColumnTypes","TrustedTenantUserForeignKey","StrictRlsPredicateCount","NoOverlayPredicate","NoSeedRows","PriorPhaseTables"})assertTrue(s.contains(token),token);assertFalse(s.contains("TRY"));}
}
