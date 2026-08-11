package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import com.shale.core.dto.CaseSummaryProjection;

import org.junit.jupiter.api.Test;

final class CaseSummaryDaoContractTest {
	private static String source() throws Exception {
		return Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
	}

	@Test void tenantAuthorityIsSessionBackedAndCrossTenantMismatchFails() throws Exception {
		String source = source();
		assertTrue(source.contains("SESSION_CONTEXT(N'ShaleClientId')"));
		assertTrue(source.contains("rs.getInt(1) != requestedTenantId"));
		assertTrue(source.contains("c.ShaleClientId = ?"));
	}

	@Test void deletionAndOrderingAreExplicitClosedEnums() throws Exception {
		String source = source();
		assertTrue(source.contains("enum DeletedState { ACTIVE, DELETED, ALL }"));
		assertTrue(source.contains("ISNULL(c.IsDeleted, 0) = 0"));
		assertTrue(source.contains("ISNULL(c.IsDeleted, 0) = 1"));
		assertTrue(source.contains("enum Order { NAME_ASC, UPDATED_DESC }"));
		assertTrue(source.contains("c.Id ASC"));
		assertTrue(source.contains("c.Id DESC"));
	}

	@Test void statusUsesCurrentPrimaryEffectiveDeterministicRules() throws Exception {
		String source = source();
		assertTrue(source.contains("cs.EndDate IS NULL"));
		assertTrue(source.contains("ORDER BY cs.IsPrimary DESC, cs.EffectiveDate DESC"));
		assertTrue(source.contains("cs.UpdatedAt DESC, cs.CreatedAt DESC, cs.Id DESC"));
		assertTrue(source.contains("s.ShaleClientId = c.ShaleClientId OR s.ShaleClientId IS NULL"));
		assertFalse(source.contains("c.CaseStatusId"));
	}

	@Test void assignmentsUseRoleIdsRetainIdsAndAreDeterministic() throws Exception {
		String source = source();
		assertTrue(source.contains("cu.RoleId = ?"));
		assertTrue(source.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY"));
		assertTrue(source.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT"));
		assertTrue(source.contains("cu.IsPrimary DESC, cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC"));
		assertTrue(source.contains("attorney.UserId AS ResponsibleAttorneyId"));
		assertFalse(source.contains("WHERE cu") && source.contains("name_first ="));
	}

	@Test void optionalRelationshipsCannotRemoveOrMultiplyCasesAndQueryIsNotNPlusOne() throws Exception {
		String source = source();
		String projectionList = source.substring(source.indexOf("public List<CaseSummaryProjection> list"),
				source.indexOf("public List<SearchCaseRow> searchActiveByName"));
		assertTrue(source.contains("OUTER APPLY ("));
		assertTrue(source.contains("SELECT TOP (1)"));
		assertTrue(source.contains("LEFT JOIN dbo.PracticeAreas"));
		assertTrue(source.contains("while (rs.next()) rows.add(map(rs))"));
		assertTrue(source.contains("return List.copyOf(rows)"));
		assertTrue(projectionList.lines().filter(line -> line.contains("executeQuery()")).count() == 2,
				"Exactly one context check and one set query are allowed");
	}

	@Test void projectionDoesNotExpandPhiOrRemoveLegacyQueries() throws Exception {
		String source = source();
		String projectionList = source.substring(source.indexOf("public List<CaseSummaryProjection> list"),
				source.indexOf("public List<SearchCaseRow> searchActiveByName"));
		for (String forbidden : new String[] { "c.Description", "c.Summary", "CaseUpdates", "Contacts", "Organizations", "Medical" })
			assertFalse(projectionList.contains(forbidden), forbidden);
		String legacy = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
		for (String method : new String[] { "findCasesViewPage", "listCasesViewForExport", "listAssignedCasesForBoard",
				"searchCasesByName", "searchDeletedCasesByName", "findMyCasesPage" })
			assertTrue(legacy.contains(method), method + " must remain for deferred consumers");
	}

	@Test void activeGridComposesProjectionRulesWithBoundedEnrichment() throws Exception {
		String source = source();
		assertTrue(source.contains("record CaseGridRow(CaseSummaryProjection summary"));
		assertTrue(source.contains("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY"));
		assertTrue(source.contains("enum GridOrder"));
		assertTrue(source.contains("CaseDateTypeSemanticRoleMappings"));
		assertTrue(source.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY"));
		assertTrue(source.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT"));
		assertFalse(source.contains("c.CallerDate"));
		assertFalse(source.contains("c.StatuteOfLimitations"));
	}

	@Test void gridStatusModesPreserveAllSelectedSubsetAndNoStatusSemantics() {
		assertTrue(CaseSummaryDao.statusPredicate(CaseSummaryDao.GridStatusMode.UNRESTRICTED, 11).isEmpty());
		assertTrue(CaseSummaryDao.statusPredicate(CaseSummaryDao.GridStatusMode.NO_STATUS, 0)
				.contains("status_row.StatusId IS NULL"));
		String selected = CaseSummaryDao.statusPredicate(CaseSummaryDao.GridStatusMode.SELECTED, 2);
		assertTrue(selected.contains("StatusId IS NULL"));
		assertTrue(selected.contains("IN (?,?)"));
	}

	@Test void casesExportReusesTheAuthoritativeBoundedGridContract() throws Exception {
		String source = source();
		String export = source.substring(source.indexOf("public List<CaseGridRow> listActiveGridForExport"),
				source.indexOf("/**", source.indexOf("public List<CaseGridRow> listActiveGridForExport") + 10));
		assertTrue(export.contains("findActiveGridPage(requestedTenantId"));
		assertTrue(export.contains("EXPORT_BATCH_SIZE"));
		assertTrue(export.contains("total = batch.total()"));
		assertTrue(export.contains("rows.size() < total"));
		assertTrue(export.contains("Set.copyOf"), "criteria must be immutable across every batch");
		assertFalse(export.contains("CaseDao"));
		assertFalse(export.contains("CaseService"));
		assertFalse(export.contains("for (CaseGridRow"), "export must not hydrate one Case at a time");
	}

	@Test void assignedBoardIsOneSetBasedAuthoritativeSnapshot() throws Exception {
		String source = source();
		String board = source.substring(source.indexOf("public List<CaseBoardRow> listActiveAssignedBoard"),
				source.indexOf("static String statusPredicate"));
		assertTrue(board.contains("verifyTenant(con, requestedTenantId)"));
		assertTrue(board.contains("verifyEligibleAssignedUser(con, requestedTenantId, assignedUserId)"));
		assertTrue(board.contains("c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0"));
		assertTrue(board.contains("EXISTS (SELECT 1 FROM dbo.CaseUsers scope"));
		assertTrue(board.contains("scope.UserId=?"));
		assertTrue(board.contains("CaseDateTypeSemanticRoleMappings"));
		assertTrue(board.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY"));
		assertTrue(board.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT"));
		assertTrue(board.contains("ORDER BY status_row.StatusId ASC, dates.IntakeDate DESC, c.Id DESC"));
		assertFalse(board.contains("c.CallerDate"));
		assertFalse(board.contains("c.StatuteOfLimitations"));
		assertFalse(board.contains("c.TortNoticeDeadline"));
		assertTrue(board.lines().filter(line -> line.contains("executeQuery()")).count() == 1,
				"Board hydration must remain a single set query after boundary validation");
		assertTrue(source.contains("u.id=? AND u.ShaleClientId=? AND ISNULL(u.is_deleted,0)=0"));
	}

	@Test void deletedSearchIsExplicitSetBasedTenantScopedAndDeterministic() throws Exception {
		String source = source();
		String deleted = source.substring(source.indexOf("public List<DeletedCaseRow> searchDeletedByName"),
				source.indexOf("static String escapeLike"));
		assertTrue(deleted.contains("verifyTenant(con, requestedTenantId)"));
		assertTrue(deleted.contains("c.ShaleClientId=? AND c.IsDeleted = 1"));
		assertTrue(deleted.contains("LOWER(COALESCE(c.Name,'')) LIKE ?"));
		assertTrue(deleted.contains("ORDER BY c.Name ASC,c.Id ASC"));
		assertTrue(deleted.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY"));
		assertTrue(deleted.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT"));
		assertTrue(deleted.contains("CaseDateTypeSemanticRoleMappings"));
		assertTrue(deleted.contains("OUTER APPLY"));
		assertFalse(deleted.contains("c.CallerDate"));
		assertFalse(deleted.contains("c.StatuteOfLimitations"));
		assertFalse(deleted.contains("c.TortNoticeDeadline"));
		assertTrue(deleted.lines().filter(line -> line.contains("executeQuery()" )).count() == 1,
				"Deleted Cases must use one set query after tenant verification");
	}

	@Test void deletedRowDefensivelyCopiesTheAuthoritativeRestoreToken() {
		byte[] token = { 1, 2, 3 };
		var summary = new CaseSummaryProjection(42, 7, "C-42", "Deleted", null, null, null, null, null,
				null, null, null, null, null, null, null, null, LocalDateTime.MIN, LocalDateTime.MIN, true);
		var row = new CaseSummaryDao.DeletedCaseRow(summary, null, null, null, null, null, token);
		token[0] = 9;
		assertTrue(row.rowVer()[0] == 1);
		byte[] returned = row.rowVer();
		returned[1] = 9;
		assertTrue(row.rowVer()[1] == 2);
	}
}
