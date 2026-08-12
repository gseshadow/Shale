package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.shale.data.dao.CaseDao;

final class CaseLinksPhase551PrimaryCreateTest {
	private static final Path DAO = Path.of("src/main/java/com/shale/data/dao/CaseDao.java");

	@Test
	void ordinaryCreateClearsExistingPrimaryBeforePrimaryInsert() throws Exception {
		String method = methodSource(Files.readString(DAO), "public CaseLinkDto createCaseLink(");
		assertOrder(method,
				"boolean hasActiveLinks = hasActiveCaseLinks",
				"boolean hasActivePrimary = hasActivePrimaryCaseLink",
				"boolean makePrimaryOnInsert = primary || !hasActiveLinks",
				"clearActivePrimaryForCreate(con, shaleClientId, caseId, actorUserId)",
				"insertExternalLink(con",
				"insertCaseLink(con, shaleClientId, actorUserId, caseId, externalId, makePrimaryOnInsert");
		assertTrue(method.contains("if (!makePrimaryOnInsert && !hasActivePrimary)"));
		assertTrue(method.contains("ensurePrimaryCandidate(con, shaleClientId, caseId, actorUserId)"));
	}

	@Test
	void aggregateCreateClearsExistingPrimaryBeforePrimaryInsertAndBeforeShares() throws Exception {
		String method = methodSource(Files.readString(DAO), "public CaseLinkDto createCaseLinkWithShares(");
		assertOrder(method,
				"validateShareDraftContacts(con, shaleClientId, shares)",
				"boolean hasActiveLinks = hasActiveCaseLinks",
				"boolean hasActivePrimary = hasActivePrimaryCaseLink",
				"clearActivePrimaryForCreate(con, shaleClientId, caseId, actorUserId)",
				"insertExternalLink(con",
				"insertCaseLink(con, shaleClientId, actorUserId, caseId, externalId, makePrimaryOnInsert",
				"insertCaseLinkShare(con");
		String compact = method.replaceAll("\\s+", " ");
		assertTrue(compact.contains("catch (Exception e) { con.rollback(); throw e; }"));
	}

	@Test
	void primaryClearingSqlIsTenantCaseActivePrimaryScoped() throws Exception {
		String method = methodSource(Files.readString(DAO), "private void clearActivePrimaryForCreate(");
		assertTrue(method.contains("WHERE ShaleClientId = ?"));
		assertTrue(method.contains("AND CaseId = ?"));
		assertTrue(method.contains("AND IsDeleted = 0"));
		assertTrue(method.contains("AND IsPrimary = 1"));
		assertFalse(method.contains("db.requireConnection"));
	}

	@Test
	void primaryUniqueConflictTranslatesToFriendlyConcurrentChangeAndPreservesCause() {
		SQLException sql = new SQLException("Cannot insert duplicate key row in object 'dbo.CaseLinks' with unique index 'UX_CaseLinks_CaseId_Primary_Active'. The duplicate key value is (7, 6502).", "23000", 2601);
		RuntimeException translated = CaseDao.translateSql("Failed to create case link", sql);
		assertTrue(translated instanceof IllegalStateException);
		assertSame(sql, translated.getCause());
		assertEquals("The Primary Link changed while you were saving. The Links list has been refreshed; please review it and try again.", translated.getMessage());
		assertFalse(translated.getMessage().contains("UX_CaseLinks"));
		assertFalse(translated.getMessage().contains("duplicate key"));
	}

	@Test
	void duplicateAssociationConflictKeepsExistingTranslation() {
		SQLException sql = new SQLException("Violation of UNIQUE KEY UX_CaseLinks_CaseId_ExternalLinkId_Active", "23000", 2627);
		RuntimeException translated = CaseDao.translateSql("Failed to create case link", sql);
		assertTrue(translated instanceof IllegalArgumentException);
		assertSame(sql, translated.getCause());
		assertEquals("This external link is already associated with the case.", translated.getMessage());
	}

	private static void assertOrder(String source, String... fragments) {
		int previous = -1;
		for (String fragment : fragments) {
			int index = source.indexOf(fragment);
			assertTrue(index > previous, () -> "Expected after previous fragment: " + fragment);
			previous = index;
		}
	}

	private static String methodSource(String source, String signature) {
		String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
		int start = normalized.indexOf(signature);
		assertTrue(start >= 0, () -> "Missing method " + signature);
		int open = normalized.indexOf('{', start);
		int depth = 0;
		for (int i = open; i < normalized.length(); i++) {
			char ch = normalized.charAt(i);
			if (ch == '{') depth++;
			else if (ch == '}' && --depth == 0) return normalized.substring(start, i + 1);
		}
		throw new AssertionError("Unbalanced method " + signature);
	}
}
