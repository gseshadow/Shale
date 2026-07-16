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

		assertTrue(adapter.contains("listLinkTypesForAdministration(int shaleClientId, int actorUserId)"));
		assertTrue(dao.contains("validateAdminActorForTenant(con, shaleClientId, actorUserId)"));
		assertTrue(dao.contains("is_deleted = 0 AND is_admin = 1"));
		assertTrue(dao.contains("public CaseLinkDto createCaseLink") && dao.contains("validateActorForTenant(con, shaleClientId, actorUserId);\n\t\t\t\tvalidateActiveLinkTypeForTenant"),
				"Ordinary case-link workflows should continue to validate an active tenant actor without requiring admin.");
	}
}
