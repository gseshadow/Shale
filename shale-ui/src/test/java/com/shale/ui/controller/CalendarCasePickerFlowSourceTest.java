package com.shale.ui.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class CalendarCasePickerFlowSourceTest {
	@Test
	void caseLookupUsesCalendarExecutorAndNeverRunsFromAddCaseHandler() throws Exception {
		String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
		String dialog = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/NewCalendarEventDialog.java"));
		String picker = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/CasePickerDialog.java"));
		assertTrue(controller.contains("Calendar case options must load off the JavaFX Application Thread"));
		int wizardStart = controller.indexOf("NewEventWizard.show(");
		int wizardEnd = controller.indexOf("PerfLog.logDone", wizardStart);
		String wizardCall = controller.substring(wizardStart, wizardEnd);
		assertTrue(wizardCall.contains("() -> caseOptionsForPicker(null)"));
		assertTrue(wizardCall.contains("() -> assignedUserOptionsForPicker(tenantId, null)"));
		assertTrue(wizardCall.contains("dbExec"));
		assertTrue(dialog.contains("CasePickerDialog.showAsync"));
		assertTrue(dialog.contains("backgroundExecutor"));
		assertFalse(dialog.contains("CasePickerDialog.show(addCaseButton"));
		assertFalse(picker.contains("showAndWait();"), "nested selector is displayed before lookup completion");
	}

	@Test
	void caseOptionsUseOneDedicatedProjectionWithoutPaginationOrPerRowLookups() throws Exception {
		String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CalendarController.java"));
		int start = controller.indexOf("private List<NewCalendarEventDialog.CaseOption> caseOptionsForPicker");
		int end = controller.indexOf("private List<NewCalendarEventDialog.AssignedUserOption>", start);
		String method = controller.substring(start, end);
		assertEquals(1, occurrences(method, "caseSummaryDao.listActiveForCalendar("));
		assertFalse(method.contains("caseDao"), "selector must not use the legacy Case DAO");
		assertFalse(method.contains("countAll("), "selector does not need a total-count query");
		assertFalse(method.contains("caseDao.getCaseRow("), "every expected case id comes from the complete projection");
		assertFalse(method.contains("forEach(c -> caseDao."), "no per-row/N+1 DAO call");
		assertFalse(method.contains(".sorted("), "SQL owns deterministic ordering");
	}

	@Test
	void pickerLogsFailureBeforeAnyDetachedOrStaleUiGuard() throws Exception {
		String picker = Files.readString(Path.of("src/main/java/com/shale/ui/component/dialog/CasePickerDialog.java"));
		int catchBlock = picker.indexOf("catch (RuntimeException failure)");
		int log = picker.indexOf("logLoadFailure(requestedGeneration", catchBlock);
		int publish = picker.indexOf("Platform.runLater(() ->", catchBlock);
		int guard = picker.indexOf("if (disposed.get() || requestedGeneration != generation.get()", publish);
		assertTrue(catchBlock >= 0 && log > catchBlock && publish > log && guard > publish,
				"initial and Retry use the same load runnable, and logging precedes stale/closed UI rejection");
		assertTrue(picker.contains("retry.setOnAction(event -> load[0].run())"));
		assertTrue(picker.contains("Cases could not be loaded. Please try again."));
		assertFalse(picker.contains("printStackTrace"));
		assertFalse(picker.contains("System.err"));
		assertFalse(picker.contains("System.out"));
	}

	private static int occurrences(String text, String needle) {
		int count = 0;
		for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + needle.length()))
			count++;
		return count;
	}
}
