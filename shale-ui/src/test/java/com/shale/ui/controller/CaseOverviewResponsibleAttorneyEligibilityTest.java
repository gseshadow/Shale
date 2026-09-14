package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.data.dao.CaseDao;

class CaseOverviewResponsibleAttorneyEligibilityTest {
	@Test
	void onlyResponsibleAttorneyUsesAttorneyCandidatesAndOtherEditorsRemainIsolated() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
		String attorney = method(source, "private void onEditResponsibleAttorneyField");
		String assistant = method(source, "private void onEditPrimaryLegalAssistantField");
		String practiceArea = method(source, "private void onEditPracticeAreaField");
		String status = method(source, "private void onEditStatusField");

		assertTrue(attorney.contains("caseDao.listAttorneysForTenant(appState.getShaleClientId())"));
		assertTrue(attorney.contains("showUserCardChoice(\"Edit Responsible Attorney\""));
		assertTrue(attorney.contains("eligibleAttorneys, false"));
		assertTrue(attorney.contains("resolveResponsibleAttorneySelection("),
				"persisted ineligible attorney must remain representable outside candidates");
		assertTrue(attorney.contains("saveResponsibleAttorneyField(v.id())"));
		assertFalse(attorney.contains("listUsersForTenant"));

		assertTrue(assistant.contains("caseDao.listUsersForTenant"));
		assertFalse(assistant.contains("listAttorneysForTenant"));
		assertTrue(practiceArea.contains("practiceAreasForTenantCached"));
		assertTrue(status.contains("statusesForTenantCached"));
	}

	@Test
	void editorInitializationPreservesNullAndAssignedResponsibleAttorneyIds() {
		CaseDao.UserRow assigned = new CaseDao.UserRow(42, "Assigned Attorney", "#123456");
		List<CaseDao.UserRow> eligible = List.of(assigned);

		assertNull(CaseController.resolveResponsibleAttorneySelection(null, null, null, eligible),
				"an unassigned case must initialize the existing selector without a selection");
		assertSame(assigned,
				CaseController.resolveResponsibleAttorneySelection(42, "Assigned Attorney", "#123456", eligible),
				"an assigned eligible attorney must retain the existing candidate preselection");
	}

	@Test
	void unassignedEditorStillSavesANewlySelectedEligibleAttorney() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));
		String attorney = method(source, "private void onEditResponsibleAttorneyField");

		assertTrue(attorney.contains("showUserCardChoice(\"Edit Responsible Attorney\""));
		assertTrue(attorney.contains("currentValue, eligibleAttorneys, false"));
		assertTrue(attorney.contains("ifPresent(v -> saveResponsibleAttorneyField(v.id()))"),
				"the selected attorney ID must continue through the authoritative save path");
	}

	private static String method(String source, String signature) {
		int start = source.indexOf(signature);
		assertTrue(start >= 0, "Missing method: " + signature);
		int brace = source.indexOf('{', start);
		int depth = 0;
		for (int i = brace; i < source.length(); i++) {
			if (source.charAt(i) == '{')
				depth++;
			if (source.charAt(i) == '}' && --depth == 0)
				return source.substring(start, i + 1);
		}
		fail("Unterminated method: " + signature);
		return "";
	}
}
