package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MyShaleCaseBoardProjectionCutoverTest {
	private static String source() throws Exception {
		return Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));
	}

	@Test void boardUsesSummaryBoundaryAndExistingCardFactory() throws Exception {
		String source=source();
		assertTrue(source.contains("caseSummaryDao.listActiveAssignedBoard(shaleClientId, userIdValue)"));
		assertFalse(source.contains("caseDao.listAssignedCasesForBoard(userIdValue)"));
		assertFalse(source.contains("caseDao.findMyCasesPage("));
		assertFalse(source.contains("caseDao.getMyCaseRow("));
		assertTrue(source.contains("Node card = buildCaseCard(vm)"));
		assertTrue(source.contains("caseCardFactory.create(new CaseCardModel("));
		assertTrue(source.contains("Objects.equals(selectedStatusId, vm.primaryStatusId)"));
		assertFalse(source.contains("Objects.equals(selected.statusName"));
	}

	@Test void initializationAndBothCompletionPathsRejectStaleLoads() throws Exception {
		String source=source();
		assertTrue(source.contains("if (!caseStatusOptionsInitialized)"));
		assertTrue(source.contains("caseStatusOptionsInitialized = true;"));
		assertTrue(source.contains("generationAtSubmit != myCasesBoardLoadGeneration"));
		assertTrue(source.contains("!Objects.equals(appState.getShaleClientId(), shaleClientId)"));
		assertTrue(source.contains("myCasesDirty = true;") && source.contains("refreshMyCasesBoard(true);"));
		assertTrue(source.contains("myCasesSectionPane.sceneProperty()"));
	}
}
