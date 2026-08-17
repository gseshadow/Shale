package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NewEventWizardPersistenceBoundaryTest {
	@Test
	void controllerRoutesEachStableBranchOnlyToItsAuthoritativeService() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
		int start = source.indexOf("NewEventWizard.Handle dialog");
		int end = source.indexOf("PerfLog.logDone", start);
		String save = source.substring(start, end);
		assertTrue(save.contains("request.sourceKind() == NewEventWizard.SourceKind.GENERAL_EVENT"));
		assertTrue(save.contains("calendarService.createEvent"));
		assertTrue(save.contains("caseService.createCaseDate"));
		assertTrue(save.contains("publishCaseDatesChanged"));
		assertFalse(save.contains("CaseDateId"));
		assertFalse(save.contains("synchron"));
	}

	@Test
	void wizardGuardsAsyncResultsAndDuplicateSubmissionAndUsesMiniCards() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NewEventWizard.java"));
		String compact = source.replaceAll("\\s+", "");
		assertTrue(compact.contains("generation==requestGeneration&&step==expected"));
		assertTrue(compact.contains("submitting.compareAndSet(false,true)"));
		assertTrue(source.contains("CaseCardFactory.Variant.MINI"));
		assertTrue(source.contains("setDefaultButton"));
		assertTrue(source.contains("setCancelButton(true)"));
		assertTrue(source.contains("KeyCode.ESCAPE"));
	}
}
