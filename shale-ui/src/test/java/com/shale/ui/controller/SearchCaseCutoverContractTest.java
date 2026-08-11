package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import com.shale.core.dto.CaseSummaryProjection;
import com.shale.data.dao.CaseSummaryDao;
import org.junit.jupiter.api.Test;

final class SearchCaseCutoverContractTest {
	@Test void activeCasesUseSummaryDaoWhileDeletedAndOtherGroupsRemainLegacy() throws Exception {
		String service = Files.readString(Path.of("src/main/java/com/shale/ui/services/SearchService.java"));
		assertTrue(service.contains("caseSummaryDao.searchActiveByName(shaleClientId"));
		assertTrue(service.contains("caseDao.searchDeletedCasesByName"));
		assertTrue(service.contains("contactDao.searchContacts"));
		assertTrue(service.contains("organizationDao.searchOrganizations"));
		assertTrue(service.contains("userDao.searchUsers"));
		assertTrue(service.contains("taskDao.searchTasks"));
		assertTrue(service.contains("calendarEventDao.searchCalendarEvents"));
	}

	@Test void callbacksGuardGenerationAndIdentityAndReuseCaseCardFactory() throws Exception {
		String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/SearchController.java"));
		assertEquals(2, occurrences(controller, "if (!isCurrent(generationAtSubmit, tenantId, userId))"));
		assertTrue(controller.contains("generation == loadGeneration"));
		assertTrue(controller.contains("Objects.equals(tenantId, appState.getShaleClientId())"));
		assertTrue(controller.contains("Objects.equals(userId, appState.getUserId())"));
		assertTrue(controller.contains("caseCardFactory.create(toCaseCardModel(row), CaseCardFactory.Variant.COMPACT)"));
	}

	@Test void mapsTheRealProjectionStatusApiAndPreservesNoStatus() {
		var populated = searchRow("Review", "#123456");
		var populatedCard = SearchController.toCaseCardModel(populated);
		assertEquals("Review", populatedCard.primaryStatusName());
		assertEquals("#123456", populatedCard.primaryStatusColor());

		var noStatusCard = SearchController.toCaseCardModel(searchRow(null, null));
		assertEquals("", noStatusCard.primaryStatusName());
		assertEquals("", noStatusCard.primaryStatusColor());
	}

	private static CaseSummaryDao.SearchCaseRow searchRow(String statusName, String statusColor) {
		var summary = new CaseSummaryProjection(42L, 7, "C-42", "Example", null, null, null,
				statusName, statusColor, null, null, null, null, null, null, null, null,
				LocalDateTime.MIN, LocalDateTime.MIN, false);
		return new CaseSummaryDao.SearchCaseRow(summary, null, null, null, null, null);
	}

	private static int occurrences(String text, String value) {
		int count=0, from=0;
		while ((from=text.indexOf(value,from))>=0) { count++; from+=value.length(); }
		return count;
	}
}
