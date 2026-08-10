package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

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
				source.indexOf("public GridPage findActiveGridPage"));
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
				source.indexOf("public GridPage findActiveGridPage"));
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
}
