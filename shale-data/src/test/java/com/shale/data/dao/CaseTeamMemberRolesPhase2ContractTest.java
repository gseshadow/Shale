package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class CaseTeamMemberRolesPhase2ContractTest {
	private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
	private static String read(String path)throws Exception{return Files.readString(ROOT.resolve(path)).replace("\r\n","\n");}

	@Test void migrationBackfillsEveryMappedLegacyRoleAndPreservesRolelessMemberships()throws Exception{
		String sql=read("docs/sql/2026-09-03_case_team_member_roles_phase2.sql");
		assertTrue(sql.contains("d.ShaleClientId IS NULL AND d.LegacyRoleId=cu.RoleId"),"backfill must exclusively use the global legacy bridge");
		assertTrue(sql.contains("WHERE cu.RoleId IS NOT NULL"),"roleless memberships must not receive fabricated assignments");
		assertFalse(sql.contains("UPDATE cu SET RoleId"),"migration must not overwrite legacy role values");
		assertTrue(sql.contains("prelitigation_staff',5,N'prelitigation'"),"intentional prelitigation mapping must be guarded");
	}

	@Test void migrationRejectsMissingAmbiguousAndCrossTenantData()throws Exception{
		String sql=read("docs/sql/2026-09-03_case_team_member_roles_phase2.sql");
		for(String code:new String[]{"56604","56605","56607","56608"})assertTrue(sql.contains("THROW "+code),"missing preflight "+code);
		assertTrue(sql.contains("FK_CaseTeamMemberRoles_MembershipTenant"));
		assertTrue(sql.contains("FK_CaseTeamMemberRoles_DefinitionTenant"));
		assertTrue(sql.contains("CK_CaseTeamMemberRoles_DefinitionScope"));
	}

	@Test void migrationRegistersStrictRlsUniquenessConcurrencyAndVerificationCounts()throws Exception{
		String sql=read("docs/sql/2026-09-03_case_team_member_roles_phase2.sql");
		assertTrue(sql.contains("sec.fn_FilterByTenant(ShaleClientId) ON dbo.CaseTeamMemberRoles"));
		assertTrue(sql.contains("UX_CaseTeamMemberRoles_Active"));
		assertTrue(sql.contains("RowVer rowversion NOT NULL"));
		for(String count:new String[]{"TotalMemberships","MembershipsWithLegacyRoles","MigratedActiveAssignments","RolelessMemberships","UnmappedLegacyRoles","DuplicateActiveAssignments","CrossTenantViolations"})assertTrue(sql.contains(count),"missing verification count "+count);
	}

	@Test void daoSupportsZeroOneManyRestoreHistoricalReadsAndIndependentRemoval()throws Exception{
		String java=read("shale-data/src/main/java/com/shale/data/dao/CaseTeamMembershipDao.java");
		assertTrue(java.contains("cu.RoleId,cu.IsPrimary,cu.RowVer"),"membership read must not require a role join");
		assertTrue(java.contains("a.IsDeleted,a.CreatedAt"),"historical assignments including inactive definitions must remain readable");
		assertTrue(java.contains("SET IsDeleted=0,DeletedAt=NULL"),"reassignment must restore a removed assignment");
		assertTrue(java.contains("Role assignment changed or was removed."),"RowVer conflicts need an actionable result");
		assertTrue(java.contains("UPDATE dbo.CaseUsers SET RoleId=NULL"),"removing the represented compatibility role must preserve membership");
	}

	@Test void responsibleAttorneyUsesProtectedKeyAndAtomicReplacement()throws Exception{
		String java=read("shale-data/src/main/java/com/shale/data/dao/CaseTeamMembershipDao.java");
		assertTrue(java.contains("RESPONSIBLE_ATTORNEY = \"responsible_attorney\""));
		assertTrue(java.contains("clearResponsible(c"));
		assertTrue(java.contains("sys.sp_getapplock"),"case-scoped transaction lock must serialize responsible replacement");
		assertTrue(java.contains("CaseUserId<>?"),"replacement must preserve the selected member while removing the former assignment");
		assertTrue(java.contains("RESPONSIBLE_ATTORNEY_CHANGED"));
	}

	@Test void assignmentsAuditAndTimelineInsideTransactionAndUseDedicatedEntityType()throws Exception{
		String java=read("shale-data/src/main/java/com/shale/data/dao/CaseTeamMembershipDao.java");
		assertTrue(java.contains("CASE_TEAM_MEMBER_ROLE,id,ADDED"));
		assertTrue(java.contains("CASE_TEAM_ROLE_ASSIGNED"));
		assertTrue(java.contains("c.commit()"));
		String allow=read("docs/sql/2026-09-03_case_team_member_audit_allowlist_phase2.sql");
		assertTrue(allow.contains("OR [EntityType]=''CASE_TEAM_MEMBER_ROLE''"),"allowlist successor must use the canonical OR-chain extension");
	}

	@Test void legacyArrowEditorDualWritePreservesRolesItCannotDisplay()throws Exception{
		String java=read("shale-data/src/main/java/com/shale/data/dao/CaseDao.java");
		assertTrue(java.contains("INTO #PreservedCaseTeamRoles"));
		assertTrue(java.contains("d.LegacyRoleId IS NULL OR cu.RoleId IS NULL OR d.LegacyRoleId<>cu.RoleId"));
		assertTrue(java.contains("JOIN dbo.CaseTeamRoleDefinitions d ON d.ShaleClientId IS NULL AND d.LegacyRoleId=cu.RoleId"));
		assertTrue(java.contains("FROM #PreservedCaseTeamRoles p JOIN dbo.CaseUsers"));
	}

	@Test void phase3CompleteTeamSaveIsOneTransactionWithConcurrencyAuditAndCompatibility()throws Exception{
		String java=read("shale-data/src/main/java/com/shale/data/dao/CaseTeamMembershipDao.java");
		assertTrue(java.contains("updateTeam(CaseTeamUpdateCommand"));
		assertTrue(java.contains("WITH (UPDLOCK,HOLDLOCK)"),"complete diff must be calculated under the business transaction lock");
		assertTrue(java.contains("Arrays.equals(current,desired.membershipRowVer())"),"baseline membership RowVer must be validated");
		assertTrue(java.contains("reconcileRoles(c,x,memberId"));
		assertTrue(java.contains("event(c,x.tenantId(),x.actorUserId(),CASE_TEAM_MEMBER_ROLE"));
		assertTrue(java.contains("syncLegacyOnAssign"),"legacy compatibility must remain conservative");
		String adapter=read("shale-data/src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java");
		assertTrue(adapter.contains("updateCaseTeam(CaseTeamUpdateCommand c){return requireCaseTeamMembershipDao().updateTeam(c);}"),"production service adapter must delegate the aggregate operation to its DAO");
	}
}
