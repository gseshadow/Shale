package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseSummaryDocumentsContractTest {
	@Test void documentsLookupUsesSharedOneCaseProjectionBoundary() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
		String method = source.substring(source.indexOf("findActiveForDocuments"),
				source.indexOf("private static String summarySelectSql"));
		assertTrue(method.contains("verifyTenant(con, requestedTenantId)"));
		assertTrue(method.contains("ISNULL(c.IsDeleted, 0) = 0 AND c.Id = ?"));
		assertTrue(method.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY"));
		assertTrue(method.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT"));
		assertTrue(method.contains("ps.setLong(4, caseId)"));
		assertTrue(method.contains("localDate(rs, \"InjuryDate\")"));
		assertTrue(method.contains("localDate(rs, \"StatuteDate\")"));
		assertFalse(method.matches("(?s).*RoleId\\s*=\\s*[0-9]+.*"));
	}

	@Test void documentsDatesAreAuthoritativeBoundedAndTenantEffective() throws Exception {
		String sql = CaseSummaryDao.documentSelectSql();
		assertTrue(sql.contains("FROM dbo.CaseDates cd"));
		assertTrue(sql.contains("stored_type.SystemKey='date_of_injury'"));
		assertTrue(sql.contains("LOWER(LTRIM(RTRIM(family_type.SystemKey)))='statute_of_limitations'"));
		assertTrue(sql.contains("candidate.IsDeleted=0"));
		assertTrue(sql.contains("CASE WHEN candidate.ShaleClientId=c.ShaleClientId THEN 0 ELSE 1 END"));
		assertTrue(sql.contains("effective_type.IsActive=1"));
		assertTrue(sql.contains("ORDER BY family_date.StartsAt ASC,family_date.Id ASC"));
		assertFalse(sql.contains("SemanticRoleKey='STATUTE_OF_LIMITATIONS'"));
		assertFalse(sql.contains("CaseDatePresentationSelections"));
		assertTrue(sql.contains("cd.IsDeleted=0"));
		assertTrue(sql.contains("c.Id = ?"));
		assertFalse(sql.contains("c.DateOfInjury"));
		assertFalse(sql.contains("c.StatuteOfLimitations"));
		assertFalse(sql.contains("c.CallerDate"));
		assertFalse(sql.contains("c.TortNoticeDeadline"));
	}

	@Test void sharedProjectionSqlHasDeterministicScalarResolution() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
		String sql = source.substring(source.indexOf("private static String summarySelectSql"),
				source.indexOf("Active desktop global Case search"));
		assertTrue(sql.contains("SELECT TOP (1)"));
		assertTrue(sql.contains("cs.EndDate IS NULL"));
		assertTrue(sql.contains("cs.Id DESC"));
		assertTrue(sql.contains("cu.IsPrimary DESC"));
		assertTrue(sql.contains("LEFT JOIN dbo.PracticeAreas"));
	}
}
