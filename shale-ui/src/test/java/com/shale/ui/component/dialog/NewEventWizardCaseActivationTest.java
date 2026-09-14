package com.shale.ui.component.dialog;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.CalendarEventType;
import com.shale.ui.component.CaseCard;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

final class NewEventWizardCaseActivationTest {
    private NewEventWizard.Handle handle;

    @AfterEach
    void closeDialog() {
        if (handle != null) JavaFxTestSupport.runAndWait(handle::close);
    }

    @Test
    void primaryRowClickCommitsCaseCollapsesResultsSwitchesTypesAndKeepsSharedValues() {
        var chosen = option(41, "Selected Case");
        handle = show(() -> List.of(chosen), Runnable::run);
        seedTypes();
        JavaFxTestSupport.runAndWait(() -> {
            handle.titleForTest().setText("Retained title");
            handle.startDateForTest().setValue(LocalDate.of(2026, 8, 21));
            handle.startTimeForTest().getEditor().setText("13:15");
            handle.durationHoursForTest().setValue(1);
            handle.durationMinutesForTest().setValue(30);
            handle.allDayForTest().setSelected(false);
            handle.notesForTest().setText("Retained notes");
            handle.typeForTest().setValue(generalChoice());
            handle.openCaseSelectorForTest();
        });
        drainFx();
        JavaFxTestSupport.runAndWait(() -> {
            handle.caseResultsForTest().getSelectionModel().selectFirst();
            Event.fireEvent(handle.caseResultsForTest(), primaryClick());
            assertSame(chosen, handle.selectedCaseForTest());
            assertFalse(handle.caseFieldForTest().getChildren().contains(handle.caseResultsForTest()));
            assertTrue(handle.caseFieldForTest().lookupAll(".case-card-compact").stream().anyMatch(CaseCard.class::isInstance));
            assertNull(handle.typeForTest().getValue(), "General type is incompatible with Case authority");
            assertEquals(List.of(21), handle.typeForTest().getItems().stream().map(NewEventWizard.TypeChoice::authoritativeTypeId).toList());
            assertEquals("Retained title", handle.titleForTest().getText());
            assertEquals(LocalDate.of(2026, 8, 21), handle.startDateForTest().getValue());
            assertEquals("13:15", handle.startTimeForTest().getEditor().getText());
            assertEquals(1, handle.durationHoursForTest().getValue());
            assertEquals(30, handle.durationMinutesForTest().getValue());
            assertFalse(handle.allDayForTest().isSelected());
            assertEquals("Retained notes", handle.notesForTest().getText());
        });
    }

    @Test
    void nestedMiniCardMouseAndEnterAndSpaceCommitByStableCaseIdExactlyOnce() {
        assertNestedCardActivation(primaryClick());
        assertListKeyActivation(KeyCode.ENTER);
        assertListKeyActivation(KeyCode.SPACE);
    }

    @Test
    void changeReplacesCaseAndRemoveRestoresGeneralAuthority() {
        AtomicInteger load = new AtomicInteger();
        var first = option(51, "Duplicate");
        var replacement = option(52, "Duplicate");
        handle = show(() -> load.incrementAndGet() == 1 ? List.of(first) : List.of(replacement), Runnable::run);
        seedTypes();
        openLoadAndActivate(KeyCode.ENTER);
        JavaFxTestSupport.runAndWait(() -> {
            assertEquals(51, handle.selectedCaseForTest().caseId());
            handle.typeForTest().setValue(caseChoice());
            button("Change").fire();
        });
        drainFx();
        JavaFxTestSupport.runAndWait(() -> {
            handle.caseResultsForTest().getSelectionModel().selectFirst();
            Event.fireEvent(handle.caseResultsForTest(), key(KeyCode.SPACE));
            assertEquals(52, handle.selectedCaseForTest().caseId(), "replacement uses authoritative ID, not duplicate name");
            assertEquals(NewEventWizard.SourceKind.CASE_EVENT, handle.typeForTest().getItems().getFirst().sourceKind());
            button("Remove").fire();
            assertNull(handle.selectedCaseForTest());
            assertNull(handle.typeForTest().getValue(), "Case type is incompatible after removal");
            assertEquals(List.of(11), handle.typeForTest().getItems().stream().map(NewEventWizard.TypeChoice::authoritativeTypeId).toList());
        });
    }

    @Test
    void staleCaseLoadIsRejected() {
        ManualExecutor executor = new ManualExecutor();
        AtomicInteger load = new AtomicInteger();
        handle = show(() -> List.of(option(load.incrementAndGet(), "Result")), executor);
        JavaFxTestSupport.runAndWait(() -> {
            handle.openCaseSelectorForTest();
            handle.openCaseSelectorForTest();
        });
        executor.runNext();
        drainFx();
        JavaFxTestSupport.runAndWait(() -> assertTrue(handle.caseResultsForTest().getItems().isEmpty(), "first generation is stale"));
        executor.runNext();
        drainFx();
        JavaFxTestSupport.runAndWait(() -> assertEquals(2, handle.caseResultsForTest().getItems().getFirst().caseId()));
    }

    private void assertNestedCardActivation(MouseEvent event) {
        var chosen = option(61, "Nested Card");
        handle = show(() -> List.of(chosen), Runnable::run);
        seedTypes();
        JavaFxTestSupport.runAndWait(handle::openCaseSelectorForTest);
        drainFx();
        JavaFxTestSupport.runAndWait(() -> {
            handle.stageForTest().getScene().getRoot().applyCss();
            handle.stageForTest().getScene().getRoot().layout();
            CaseCard card = handle.caseResultsForTest().lookupAll(".case-card").stream().filter(CaseCard.class::isInstance)
                    .map(CaseCard.class::cast).findFirst().orElseThrow();
            int before = handle.caseGenerationForTest();
            Event.fireEvent(card, event);
            Event.fireEvent(card, event.copyFor(card, card));
            assertEquals(61, handle.selectedCaseForTest().caseId());
            assertEquals(before + 1, handle.caseGenerationForTest(), "duplicate activation does not commit twice");
        });
        JavaFxTestSupport.runAndWait(handle::close);
        handle = null;
    }

    private void assertListKeyActivation(KeyCode code) {
        var chosen = option(code == KeyCode.ENTER ? 71 : 72, code.getName());
        handle = show(() -> List.of(chosen), Runnable::run);
        seedTypes();
        openLoadAndActivate(code);
        JavaFxTestSupport.runAndWait(() -> assertSame(chosen, handle.selectedCaseForTest()));
        JavaFxTestSupport.runAndWait(handle::close);
        handle = null;
    }

    private void openLoadAndActivate(KeyCode code) {
        JavaFxTestSupport.runAndWait(handle::openCaseSelectorForTest);
        drainFx();
        JavaFxTestSupport.runAndWait(() -> {
            handle.caseResultsForTest().getSelectionModel().selectFirst();
            Event.fireEvent(handle.caseResultsForTest(), key(code));
        });
    }

    private Button button(String text) {
        return handle.caseFieldForTest().lookupAll(".button").stream().filter(Button.class::isInstance)
                .map(Button.class::cast).filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }

    private NewEventWizard.Handle show(java.util.function.Supplier<List<NewCalendarEventDialog.CaseOption>> loader, Executor executor) {
        return JavaFxTestSupport.runAndWait(() -> NewEventWizard.show(null, 7, LocalDate.of(2026, 8, 18), loader,
                List::of, ignored -> null, executor));
    }

    private void seedTypes() {
        JavaFxTestSupport.runAndWait(() -> {
            int generation = handle.beginTypeLoad();
            handle.populateTypes(7, List.of(generalType()), List.of(caseType()), generation);
        });
    }

    private static CalendarEventType generalType() {
        return new CalendarEventType(11, 7, "general", "General", "#123456", 1, true, null, null);
    }

    private static EffectiveCaseDateTypeDto caseType() {
        return new EffectiveCaseDateTypeDto(21, 7, null, "Case Date", null, "OTHER", "#654321", true, 1,
                true, false, EffectiveCaseDateTypeDto.Origin.TENANT_CREATED, new byte[]{1});
    }

    private static NewEventWizard.TypeChoice generalChoice() {
        return new NewEventWizard.TypeChoice(NewEventWizard.SourceKind.GENERAL_EVENT, 11, "General", "#123456", true, 1);
    }

    private static NewEventWizard.TypeChoice caseChoice() {
        return new NewEventWizard.TypeChoice(NewEventWizard.SourceKind.CASE_EVENT, 21, "Case Date", "#654321", true, 1);
    }

    private static NewCalendarEventDialog.CaseOption option(int id, String name) {
        return new NewCalendarEventDialog.CaseOption(id, name, "Attorney", "#abcdef", false);
    }

    private static MouseEvent primaryClick() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 4, 4, 4, 4, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null);
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static void drainFx() {
        JavaFxTestSupport.runAndWait(() -> {});
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        @Override public void execute(Runnable command) { tasks.add(command); }
        void runNext() { tasks.remove().run(); }
    }
}
