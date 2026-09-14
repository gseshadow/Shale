package com.shale.ui.controller;

import com.shale.ui.component.dialog.NewEventWizard;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
		String compact = source.replaceAll("\\s+", "");
		List<String> orderedFields = List.of(
				"add(fields,row++,\"Title\",title);",
				"add(fields,row++,\"AssigntoCase\",caseField);",
				"add(fields,row++,\"Type\",type);",
				"add(fields,row++,\"StartDate\",startDate);",
				"add(fields,row++,\"EndDate\",endDate);",
				"add(fields,row++,\"TimeandDuration\",timing);",
				"add(fields,row++,\"AllDay\",allDay);",
				"add(fields,row,\"Notes\",notes);"
		);
		int previous = -1;
		for (String field : orderedFields) {
			int current = compact.indexOf(field, previous + 1);
			assertTrue(current > previous, "Missing or out-of-order New Event field: " + field);
			previous = current;
		}
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
		var general12 = new NewEventWizard.TypeChoice(NewEventWizard.SourceKind.GENERAL_EVENT, 12, "Duplicate", "#111111", true, 1);
		var general13 = new NewEventWizard.TypeChoice(NewEventWizard.SourceKind.GENERAL_EVENT, 13, "Duplicate", "#111111", true, 1);
		var case12 = new NewEventWizard.TypeChoice(NewEventWizard.SourceKind.CASE_EVENT, 12, "Duplicate", "#111111", true, 1);
		var renamedGeneral12 = new NewEventWizard.TypeChoice(NewEventWizard.SourceKind.GENERAL_EVENT, 12, "Renamed", "#222222", false, 99);
		assertAll(
				() -> assertTrue(general12.sameIdentityAs(renamedGeneral12)),
				() -> assertFalse(general12.sameIdentityAs(general13),
						"duplicate names do not establish identity"),
				() -> assertFalse(general12.sameIdentityAs(case12),
						"identity is source-qualified")
		);
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
		assertTrue(wizard.contains("TimeDurationInput.calculateEnd"));
		assertTrue(wizard.contains("!selected.supportsTime()"));
		assertTrue(controller.contains("input.caseDateTypeId(),input.title(),input.startsAt()"));
	}
}
