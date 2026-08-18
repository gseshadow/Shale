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
		assertTrue(compact.contains("typeGeneration==requestGeneration"));
		assertTrue(compact.contains("caseGeneration==requestGeneration"));
		assertTrue(compact.contains("submitting.compareAndSet(false,true)"));
		assertTrue(source.contains("CaseCardFactory.Variant.MINI"));
		assertTrue(source.contains("setDefaultButton"));
		assertTrue(source.contains("setCancelButton(true)"));
		assertTrue(source.contains("KeyCode.ESCAPE"));
	}

	@Test
	void singleFormHasExactOrderedLabelsAndNoWizardNavigationOrAssignedUser() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NewEventWizard.java"));
		String labels = "\"Title\",title); add(fields,row++,\"Assign to Case\",caseField); add(fields,row++,\"Type\",type);\n"
				+ "            add(fields,row++,\"Start Date\",startDate); add(fields,row++,\"End Date\",endDate); add(fields,row++,\"Start Time\",startTime);\n"
				+ "            add(fields,row++,\"Duration\",duration); add(fields,row++,\"All Day\",allDay); add(fields,row,\"Notes\",notes);";
		assertTrue(source.contains(labels));
		assertFalse(source.contains("enum Step"));
		assertFalse(source.contains("\"Back\""));
		assertFalse(source.contains("\"Next\""));
		assertFalse(source.contains("Assigned User"));
		assertTrue(source.contains("label.setLabelFor(node)"));
	}

	@Test
	void assignmentControlsTypeAuthorityAndPreservesStableIdentity() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NewEventWizard.java"));
		assertTrue(source.contains("selectedCase==null?SourceKind.GENERAL_EVENT:SourceKind.CASE_EVENT"));
		assertTrue(source.contains("t.authoritativeTypeId()==old.authoritativeTypeId()"));
		assertTrue(source.contains("CaseCardFactory.Variant.MINI"));
		assertTrue(source.contains("\"Change\""));
		assertTrue(source.contains("\"Remove\""));
		assertFalse(source.contains("CaseDateId"));
		assertFalse(source.toLowerCase().contains("synchron"));
	}

	@Test
	void titleAndDateTimeValidationAndCaseTitlePropagationAreExplicit() throws Exception {
		String wizard = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NewEventWizard.java"));
		String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
		assertTrue(wizard.contains("String normalized=safe(title.getText()).strip()"));
		assertTrue(wizard.contains("TITLE_LIMIT = 255"));
		assertTrue(wizard.contains("End Date must not be before Start Date"));
		assertTrue(wizard.contains("plusMinutes(minutes)"));
		assertTrue(wizard.contains("!selected.supportsTime()"));
		assertTrue(controller.contains("input.caseDateTypeId(),input.title(),input.startsAt()"));
	}
}
