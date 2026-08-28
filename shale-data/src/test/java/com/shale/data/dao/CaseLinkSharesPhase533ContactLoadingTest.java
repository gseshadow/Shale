package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

final class CaseLinkSharesPhase533ContactLoadingTest {

	private static final Path CASE_DAO = Path.of("src/main/java/com/shale/data/dao/CaseDao.java");

	@Test
	void contactOptionQueriesUseStructuredContactPointsAndDisplayFallback()
			throws Exception {

		String source = Files.readString(CASE_DAO);
		String region = source.substring(
				source.indexOf(
						"public List<CaseLinkContactOptionDto> searchCaseLinkShareContacts"),
				source.indexOf(
						"public List<CaseLinkShareDto> listCaseLinkShares"));

		/*
		 * Phase 3B: Contact-option loading and searching must not read the retiring scalar email
		 * columns from dbo.Contacts.
		 */
		assertFalse(region.contains("ct.EmailPersonal"),
				"Contact options must not read Contacts.EmailPersonal");
		assertFalse(region.contains("ct.EmailWork"),
				"Contact options must not read Contacts.EmailWork");
		assertFalse(region.contains("ct.EmailOther"),
				"Contact options must not read Contacts.EmailOther");

		/*
		 * Email searching must use the authoritative structured assignment table. The production
		 * query may use a correlated EXISTS or another bounded structured projection, so this
		 * test does not require a particular SQL alias.
		 */
		assertTrue(region.contains("dbo.ContactEmailAddresses"),
				"Contact options must use structured ContactEmailAddresses");
		assertTrue(region.contains("EmailAddress"),
				"Contact-option email search must use the structured EmailAddress");
		assertTrue(region.contains("IsDeleted"),
				"Structured Contact email loading must enforce lifecycle state");
		assertTrue(region.contains("ShaleClientId"),
				"Structured Contact email loading must enforce tenant ownership");

		/*
		 * Preserve the established Contact display-name fallback.
		 */
		assertTrue(region.contains("COALESCE(NULLIF(LTRIM(RTRIM("));
		assertTrue(region.contains(".Name"));
		assertTrue(region.contains("CONCAT("));
		assertTrue(region.contains(".FirstName"));
		assertTrue(region.contains(".LastName"));
		assertTrue(region.contains(".WorkName"));

		/*
		 * Preserve Contact tenant, lifecycle, and deterministic ordering.
		 */
		assertTrue(region.contains("ct.ShaleClientId = ?"));
		assertTrue(region.contains("ISNULL(ct.IsDeleted, 0) = 0"));
		assertTrue(region.contains(
				"ORDER BY DisplayName ASC, ct.Id ASC"));
	}

	@Test
	void completeContactOptionOperationIsNotCappedAtOneHundred()
			throws Exception {

		String source = Files.readString(CASE_DAO);
		String listMethod = source.substring(
				source.indexOf(
						"public List<CaseLinkContactOptionDto> listCaseLinkShareContacts"),
				source.indexOf(
						"public List<CaseLinkContactOptionDto> listCaseLinkShareCaseContacts"));

		assertFalse(
				Pattern.compile("TOP\\s*\\(").matcher(listMethod).find(),
				"complete list must not use TOP");
		assertFalse(
				listMethod.contains("100"),
				"complete list must not use a 100-row magic cap");
		assertTrue(listMethod.contains("ct.ShaleClientId = ?"));
		assertTrue(listMethod.contains("ISNULL(ct.IsDeleted, 0) = 0"));
	}

	@Test
	void caseContactsUseAuthoritativeAssociationAndTenantValidation()
			throws Exception {

		String source = Files.readString(CASE_DAO);
		String method = source.substring(
				source.indexOf(
						"public List<CaseLinkContactOptionDto> listCaseLinkShareCaseContacts"),
				source.indexOf(
						"private static String caseLinkShareContactDisplayNameExpression"));

		assertTrue(method.contains("FROM dbo.CaseParties cp"));
		assertFalse(method.contains("dbo.CaseContacts"),
				"current case-party choices must not use historical CaseContacts snapshots");
		assertTrue(method.contains(
				"JOIN dbo.Contacts ct ON ct.Id = cp.ContactId"));
		assertTrue(method.contains("cp.ContactId IS NOT NULL"));
		assertTrue(method.contains("c.ShaleClientId = ?"));
		assertTrue(method.contains("ct.ShaleClientId = ?"));
		assertTrue(method.contains("ISNULL(ct.IsDeleted, 0) = 0"));
		assertTrue(method.contains("GROUP BY ct.Id"));
		assertTrue(method.contains(
				"ORDER BY DisplayName ASC, ct.Id ASC"));
	}
}