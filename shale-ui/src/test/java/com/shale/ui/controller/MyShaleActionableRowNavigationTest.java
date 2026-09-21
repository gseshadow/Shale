package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class MyShaleActionableRowNavigationTest {

	@Test
	void overdueRadarUsesExistingMyTasksSelectionAndClearsOnlyConflictingFilters() {
		JavaFxTestSupport.runAndWait(() -> {
			Loaded loaded = load();
			TextField search = field(loaded.controller(), "myTasksSearchField", TextField.class);
			search.setText("hidden task");
			loaded.controller().showOverdueTasksInMyTasks();

			assertEquals("", search.getText());
			assertEquals("Due Date (Soonest)", choiceValue(loaded.controller(), "myTasksSortChoice"));
			assertEquals("Assigned to Me", choiceValue(loaded.controller(), "myTasksSourceChoice"));
			assertEquals("All Priorities", choiceValue(loaded.controller(), "myTasksPriorityFilterChoice"));
			assertEquals("All Cases", choiceValue(loaded.controller(), "myTasksCaseFilterChoice"));
			assertEquals("All Active", choiceValue(loaded.controller(), "myTasksStatusFilterChoice"));
			assertTrue(field(loaded.controller(), "tasksSectionPane", VBox.class).isVisible());
			assertFalse(field(loaded.controller(), "overviewSectionPane", VBox.class).isVisible());
		});
	}

	@Test
	void overdueRadarReceivesTheSharedMouseAndKeyboardContract() {
		JavaFxTestSupport.runAndWait(() -> {
			MyShaleController controller = new MyShaleController();
			MyShaleController.CaseRadarRow descriptor = new MyShaleController.CaseRadarRow(
					MyShaleController.CaseRadarSeverity.CRITICAL, "Overdue tasks", 3,
					"Assigned to you and past due.", MyShaleController.CaseRadarAction.OVERDUE_TASKS);
			HBox row = (HBox) controller.buildCaseRadarRow(descriptor);
			assertActionable(row, "My Tasks");
		});
	}

	@Test
	void importantDatesRouteByAuthoritativeTaskAndCaseIdsForMouseAndKeyboard() {
		JavaFxTestSupport.runAndWait(() -> {
			MyShaleController controller = new MyShaleController();
			AtomicLong taskId = new AtomicLong();
			AtomicInteger caseId = new AtomicInteger();
			AtomicInteger taskActivations = new AtomicInteger();
			AtomicInteger caseActivations = new AtomicInteger();
			set(controller, "onOpenTask", (Consumer<Long>) id -> { taskId.set(id); taskActivations.incrementAndGet(); });
			set(controller, "onOpenCase", (Consumer<Integer>) id -> { caseId.set(id); caseActivations.incrementAndGet(); });

			HBox task = (HBox) controller.buildImportantDateRow(item(MyShaleController.ImportantDateType.TASK, 77L, 991L, "File motion"));
			assertActionable(task, "Task Details");
			fireMouse(task);
			assertEquals(991L, taskId.get());
			assertEquals(1, taskActivations.get());
			Event.fireEvent(task, key(KeyCode.ENTER));
			assertEquals(2, taskActivations.get());
			Event.fireEvent(task, key(KeyCode.SPACE));
			assertEquals(3, taskActivations.get(), "Mouse, Enter, and Space must each invoke Task Details exactly once.");

			HBox sol = (HBox) controller.buildImportantDateRow(item(MyShaleController.ImportantDateType.SOL, 77L, null, "Anderson"));
			assertActionable(sol, "Open case Anderson");
			Event.fireEvent(sol, key(KeyCode.ENTER));
			assertEquals(77, caseId.get());
			assertEquals(1, caseActivations.get());

			HBox tort = (HBox) controller.buildImportantDateRow(item(MyShaleController.ImportantDateType.TORT_NOTICE, 88L, null, "Baker"));
			Event.fireEvent(tort, key(KeyCode.SPACE));
			assertEquals(88, caseId.get());
			assertEquals(2, caseActivations.get(), "Each deadline key activation must open its case exactly once.");
		});
	}

	@Test
	void rowsWithoutApprovedDestinationRemainPlainPresentation() {
		JavaFxTestSupport.runAndWait(() -> {
			MyShaleController controller = new MyShaleController();
			HBox none = (HBox) controller.buildCaseRadarRow(new MyShaleController.CaseRadarRow(
					MyShaleController.CaseRadarSeverity.NEUTRAL, "Informational", 1, "No destination",
					MyShaleController.CaseRadarAction.NONE));
			assertNonActionable(none, "case-radar-row-actionable");

			HBox missingTask = (HBox) controller.buildImportantDateRow(item(MyShaleController.ImportantDateType.TASK, 77L, null, "No task"));
			assertNonActionable(missingTask, "important-date-row-actionable");
			HBox calendar = (HBox) controller.buildImportantDateRow(item(MyShaleController.ImportantDateType.CALENDAR, 77L, null, "Calendar item"));
			assertNonActionable(calendar, "important-date-row-actionable");
		});
	}

	private static MyShaleController.ImportantDateItem item(MyShaleController.ImportantDateType type, Long caseId, Long taskId, String title) {
		return new MyShaleController.ImportantDateItem(LocalDate.of(2026, 9, 25), type,
				MyShaleController.ImportantDateSeverity.WARNING, title, caseId, taskId);
	}

	private static void assertActionable(HBox row, String accessibleDestination) {
		assertTrue(row.getStyleClass().contains("shale-actionable-row"));
		assertTrue(row.isFocusTraversable());
		assertEquals(AccessibleRole.BUTTON, row.getAccessibleRole());
		assertTrue(row.getAccessibleText().contains(accessibleDestination));
		assertTrue(row.getOnMouseClicked() != null && row.getOnKeyPressed() != null);
	}

	private static void assertNonActionable(HBox row, String featureClass) {
		assertFalse(row.getStyleClass().contains(featureClass));
		assertFalse(row.getStyleClass().contains("shale-actionable-row"));
		assertFalse(row.isFocusTraversable());
		assertNull(row.getAccessibleText());
		assertNull(row.getOnMouseClicked());
		assertNull(row.getOnKeyPressed());
	}

	private static void fireMouse(Node row) {
		Event.fireEvent(row, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY,
				1, false, false, false, false, true, false, false, true, false, false, null));
	}

	private static KeyEvent key(KeyCode code) {
		return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
	}

	private static Object choiceValue(Object target, String name) {
		Object value = field(target, name, ChoiceBox.class).getValue();
		return value == null ? null : value.toString();
	}

	private static Loaded load() {
		try {
			FXMLLoader loader = new FXMLLoader(MyShaleActionableRowNavigationTest.class.getResource("/fxml/my-shale.fxml"));
			Parent root = loader.load();
			return new Loaded(root, loader.getController());
		} catch (Exception ex) {
			throw new AssertionError("My Shale FXML must load for actionable-row navigation coverage", ex);
		}
	}

	private static <T> T field(Object target, String name, Class<T> type) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return type.cast(field.get(target));
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("Missing production field " + name, ex);
		}
	}

	private static void set(Object target, String name, Object value) {
		try {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			field.set(target, value);
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("Unable to configure production callback " + name, ex);
		}
	}

	private record Loaded(Parent root, MyShaleController controller) { }
}
