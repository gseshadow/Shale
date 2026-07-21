package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinksPhase3AdministrationTest {
	@Test
	void administrationReadAndMutationsRequireActiveTenantAdminButCaseLinksUseActorAuth() throws Exception {
		String dao = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
		String adapter = Files.readString(Path.of("src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java"));
		String createCaseLink = methodSource(dao, "public CaseLinkDto createCaseLink");

		assertTrue(adapter.contains("listLinkTypesForAdministration(int shaleClientId, int actorUserId)"));
		assertAdminRequired(dao, "public List<LinkTypeDto> listLinkTypesForAdministration");
		assertAdminRequired(dao, "public LinkTypeDto createLinkType");
		assertAdminRequired(dao, "public LinkTypeDto updateLinkType");
		assertAdminRequired(dao, "public LinkTypeDto setLinkTypeActive");
		assertAdminRequired(dao, "public void resetLinkTypeOverride");
		assertTrue(dao.contains("is_deleted = 0 AND is_admin = 1"));
		assertTrue(createCaseLink.contains("validateCaseForTenant(con, shaleClientId, caseId)"));
		assertTrue(usesOrdinaryActorAuthorization(createCaseLink),
				"Ordinary case-link workflows should continue to validate an active tenant actor without requiring admin.");
	}

	@Test
	void ordinaryCaseLinkAuthorizationCheckIsLineEndingIndependent() {
		String lfSource = "public CaseLinkDto createCaseLink() {\n"
				+ "    validateCaseForTenant(con, shaleClientId, caseId);\n"
				+ "    validateActorForTenant(con, shaleClientId, actorUserId);\n"
				+ "    validateActiveLinkTypeForTenant(con, shaleClientId, linkTypeId);\n"
				+ "}\n"
				+ "public LinkTypeDto createLinkType() {\n"
				+ "    validateAdminActorForTenant(con, shaleClientId, actorUserId);\n"
				+ "}";
		String crlfSource = lfSource.replace("\n", "\r\n");

		assertTrue(usesOrdinaryActorAuthorization(methodSource(lfSource, "public CaseLinkDto createCaseLink")));
		assertTrue(usesOrdinaryActorAuthorization(methodSource(crlfSource, "public CaseLinkDto createCaseLink")));
	}

	private static void assertAdminRequired(String source, String signature) {
		String method = methodSource(source, signature);
		assertTrue(method.contains("validateAdminActorForTenant(con, shaleClientId, actorUserId)"), signature);
	}

	private static boolean usesOrdinaryActorAuthorization(String method) {
		return method.contains("validateActorForTenant(con, shaleClientId, actorUserId)")
				&& method.contains("validateActiveLinkTypeForTenant(con, shaleClientId, linkTypeId)")
				&& !method.contains("validateAdminActorForTenant");
	}

	private static String methodSource(String source, String signature) {
		int start = source.indexOf(signature);
		assertTrue(start >= 0, signature);
		int openingBrace = source.indexOf('{', start);
		assertTrue(openingBrace >= 0, signature);
		int depth = 0;
		for (int i = openingBrace; i < source.length(); i++) {
			char ch = source.charAt(i);
			if (ch == '{') {
				depth++;
			} else if (ch == '}') {
				depth--;
				if (depth == 0) {
					return source.substring(start, i + 1);
				}
			}
		}
		throw new AssertionError("Could not find method end for " + signature);
	}
}
