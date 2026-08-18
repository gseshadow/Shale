package com.shale.ui.component.dialog;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.CalendarEventType;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.animation.AnimationTimer;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

final class NewEventWizardTypePopupLifecycleTest {
    private NewEventWizard.Handle handle;
    private Thread.UncaughtExceptionHandler previousFxExceptionHandler;
    private final AtomicReference<Throwable> fxFailure = new AtomicReference<>();

    @AfterEach
    void closeDialogAndRestoreExceptionHandler() {
        if (handle != null) JavaFxTestSupport.runAndWait(handle::close);
        if (previousFxExceptionHandler != null)
            JavaFxTestSupport.runAndWait(() -> Thread.currentThread().setUncaughtExceptionHandler(previousFxExceptionHandler));
    }

    @Test
    void repeatedGeneralAndCasePopupCommitmentsSurvivePulsesAndCommitOncePerActivation() throws Exception {
        installFxExceptionCapture();
        showAndSeed();

        repeatCommitSequence(NewEventWizard.SourceKind.GENERAL_EVENT, List.of(11, 12, 13), 12);
        assignCase();
        repeatCommitSequence(NewEventWizard.SourceKind.CASE_EVENT, List.of(21, 22, 23), 21);
        removeCase();
        repeatCommitSequence(NewEventWizard.SourceKind.GENERAL_EVENT, List.of(11, 12, 13), 11);

        awaitPulses(8);
        assertNull(fxFailure.get(), () -> "uncaught JavaFX Application Thread failure: " + fxFailure.get());
    }

    private void repeatCommitSequence(NewEventWizard.SourceKind authority, List<Integer> completeIds, int firstId) throws Exception {
        for (int repetition = 0; repetition < 6; repetition++) {
            int iteration = repetition;
            int id = completeIds.get((completeIds.indexOf(firstId) + repetition) % completeIds.size());
            Activation activation = Activation.values()[repetition % Activation.values().length];
            int before = JavaFxTestSupport.runAndWait(handle::typeCommitCountForTest);
            NewEventWizard.TypeChoice previous = JavaFxTestSupport.runAndWait(handle::selectedTypeForTest);
            AtomicReference<NewEventWizard.TypeLifecycleState> activationState = new AtomicReference<>();

            JavaFxTestSupport.runAndWait(() -> {
                handle.typeForTest().show();
                assertTrue(handle.typeForTest().isShowing());
                handle.typeForTest().getSelectionModel().clearSelection();
                int index = indexOf(id);
                assertTrue(index >= 0, "expected authoritative type id " + id);
                // A real popup mouse/Enter/Space activation first commits the highlighted item through
                // ComboBox selection/action. Do not dispatch a second synthetic gesture to the owner
                // after that listener has hidden the popup; that is not a JavaFX user activation.
                handle.typeForTest().getSelectionModel().select(index);
                Event.fireEvent(handle.typeForTest(), activation.event());
                activationState.set(handle.typeLifecycleStateForTest());
            });

            awaitPulses(3);
            JavaFxTestSupport.runAndWait(() -> {
                String trace = trace(activation,authority,previous,id,activationState.get(),handle.typeLifecycleStateForTest());
                assertEquals(before + 1, handle.typeCommitCountForTest(), "one activation must commit exactly once; " + trace);
                assertEquals(authority, handle.selectedTypeForTest().sourceKind(), trace);
                assertEquals(id, handle.selectedTypeForTest().authoritativeTypeId(), trace);
                assertFalse(handle.typeForTest().isShowing());

                handle.typeForTest().show();
                assertEquals(completeIds, displayedIds(), "reopening restores the complete authority list");
                handle.typeForTest().hide();
            });
            awaitPulses(2);
            assertNull(fxFailure.get(), () -> "uncaught JavaFX failure after repetition " + iteration + ": " + fxFailure.get());
        }
    }

    private static String trace(Activation activation, NewEventWizard.SourceKind authority,
            NewEventWizard.TypeChoice previous, int requestedId,
            NewEventWizard.TypeLifecycleState activationState, NewEventWizard.TypeLifecycleState completedState) {
        return "activation=" + activation + ", authority=" + authority
                + ", previousId=" + (previous == null ? null : previous.authoritativeTypeId())
                + ", requestedId=" + requestedId + ", afterActivation=" + activationState
                + ", afterCompletion=" + completedState;
    }

    private void installFxExceptionCapture() {
        JavaFxTestSupport.runAndWait(() -> {
            Thread fxThread = Thread.currentThread();
            previousFxExceptionHandler = fxThread.getUncaughtExceptionHandler();
            fxThread.setUncaughtExceptionHandler((thread, failure) -> fxFailure.compareAndSet(null, failure));
        });
    }

    private void showAndSeed() {
        handle = JavaFxTestSupport.runAndWait(() -> NewEventWizard.show(null, 7, LocalDate.of(2026, 8, 18),
                () -> List.of(new NewCalendarEventDialog.CaseOption(41, "Case", "Attorney", "#abcdef", false)),
                List::of, ignored -> null, Runnable::run));
        JavaFxTestSupport.runAndWait(() -> {
            int generation = handle.beginTypeLoad();
            handle.populateTypes(7,
                    List.of(general(11, "Hearing"), general(12, "Duplicate"), general(13, "Duplicate")),
                    List.of(caseType(21, "Hearing"), caseType(22, "Duplicate"), caseType(23, "Duplicate")), generation);
        });
    }

    private void assignCase() {
        JavaFxTestSupport.runAndWait(handle::openCaseSelectorForTest);
        JavaFxTestSupport.runAndWait(() -> {
            handle.caseResultsForTest().getSelectionModel().selectFirst();
            javafx.event.Event.fireEvent(handle.caseResultsForTest(), key(KeyCode.ENTER));
        });
    }

    private void removeCase() {
        JavaFxTestSupport.runAndWait(() -> handle.caseFieldForTest().lookupAll(".button").stream()
                .filter(javafx.scene.control.Button.class::isInstance).map(javafx.scene.control.Button.class::cast)
                .filter(button -> "Remove".equals(button.getText())).findFirst().orElseThrow().fire());
    }

    private int indexOf(int id) {
        for (int i = 0; i < handle.typeForTest().getItems().size(); i++)
            if (handle.typeForTest().getItems().get(i).authoritativeTypeId() == id) return i;
        return -1;
    }

    private List<Integer> displayedIds() {
        return handle.typeForTest().getItems().stream().map(NewEventWizard.TypeChoice::authoritativeTypeId).toList();
    }

    private static void awaitPulses(int count) throws Exception {
        CountDownLatch pulses = new CountDownLatch(count);
        AtomicReference<AnimationTimer> timer = new AtomicReference<>();
        JavaFxTestSupport.runAndWait(() -> {
            AnimationTimer animation = new AnimationTimer() {
                @Override public void handle(long now) {
                    pulses.countDown();
                    if (pulses.getCount() == 0) stop();
                }
            };
            timer.set(animation);
            animation.start();
        });
        assertTrue(pulses.await(10, TimeUnit.SECONDS), "JavaFX pulses did not complete");
        JavaFxTestSupport.runAndWait(() -> timer.get().stop());
    }

    private static CalendarEventType general(int id, String name) {
        return new CalendarEventType(id, 7, "key-" + id, name, "#123456", id, true, null, null);
    }

    private static EffectiveCaseDateTypeDto caseType(int id, String name) {
        return new EffectiveCaseDateTypeDto(id, 7, null, name, null, "OTHER", "#654321", true, id,
                true, false, EffectiveCaseDateTypeDto.Origin.TENANT_CREATED, new byte[]{1});
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private static MouseEvent mouse() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 4, 4, 4, 4, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null);
    }

    private enum Activation {
        MOUSE, ENTER, SPACE;
    }
}
