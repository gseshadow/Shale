package com.shale.ui.component.dialog;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.CalendarEventType;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class NewEventWizardTypeCommitmentTest {
    private NewEventWizard.Handle handle;

    @AfterEach
    void closeDialog() {
        if (handle != null) JavaFxTestSupport.runAndWait(handle::close);
    }

    @Test
    void generalTypesCommitByPrimaryMouseEnterAndSpaceAndPassSaveValidation() {
        for (Activation activation : Activation.values()) {
            showAndSeed();
            commit(activation, 12);
            JavaFxTestSupport.runAndWait(() -> {
                assertChoice(NewEventWizard.SourceKind.GENERAL_EVENT, 12);
                handle.titleForTest().setText("Committed general event");
                var request = handle.readForTest().orElseThrow();
                assertEquals(12, request.general().calendarEventTypeId());
                assertFalse(handle.typeForTest().isShowing(), "commit closes the popup");
            });
            closeCurrent();
        }
    }

    @Test
    void caseTypesCommitByPrimaryMouseEnterAndSpaceAndPassSaveValidation() {
        for (Activation activation : Activation.values()) {
            showAndSeed();
            assignCase(41);
            commit(activation, 22);
            JavaFxTestSupport.runAndWait(() -> {
                assertChoice(NewEventWizard.SourceKind.CASE_EVENT, 22);
                handle.titleForTest().setText("Committed case event");
                var request = handle.readForTest().orElseThrow();
                assertEquals(22, request.caseDate().caseDateTypeId());
                assertEquals(41, request.caseDate().caseId());
                assertFalse(handle.typeForTest().isShowing(), "commit closes the popup");
            });
            closeCurrent();
        }
    }

    @Test
    void reopeningRestoresCompleteAuthorityListAndSearchDoesNotDestroyItsSource() {
        showAndSeed();
        commit(Activation.MOUSE, 12);
        JavaFxTestSupport.runAndWait(() -> {
            handle.filterTypesForTest("Beta");
            assertEquals(List.of(12), ids());
            assertEquals(12, handle.selectedTypeForTest().authoritativeTypeId());
            handle.showAllAuthorityTypesForTest();
            assertEquals(List.of(11, 12, 13), ids());
            assertEquals(12, handle.typeForTest().getValue().authoritativeTypeId());
        });
    }

    @Test
    void duplicateNamesCommitBySourceAndStableIdRatherThanDisplayText() {
        showAndSeed();
        commit(Activation.MOUSE, 13);
        JavaFxTestSupport.runAndWait(() -> {
            assertEquals("Duplicate", handle.selectedTypeForTest().name());
            assertEquals(13, handle.selectedTypeForTest().authoritativeTypeId());
            handle.titleForTest().setText("Duplicate name event");
            assertEquals(13, handle.readForTest().orElseThrow().general().calendarEventTypeId());
        });
    }

    @Test
    void typedSearchTextIsNotACommitmentAndAuthorityChangesClearOnlyIncompatibleTypes() {
        showAndSeed();
        JavaFxTestSupport.runAndWait(() -> {
            handle.typeForTest().getEditor().setText("Duplicate");
            assertNull(handle.selectedTypeForTest(), "editor text alone is never authoritative");
        });
        commit(Activation.ENTER, 11);
        assignCase(41);
        JavaFxTestSupport.runAndWait(() -> assertNull(handle.selectedTypeForTest(), "General selection is incompatible with Case authority"));
        commit(Activation.SPACE, 21);
        assignCase(42);
        JavaFxTestSupport.runAndWait(() -> assertEquals(21, handle.selectedTypeForTest().authoritativeTypeId(),
                "changing to another Case preserves a compatible Case type"));
        removeCase();
        JavaFxTestSupport.runAndWait(() -> assertNull(handle.selectedTypeForTest(), "removing the Case clears its Case type"));
    }

    @Test
    void staleTypeResultsCannotReplaceCurrentAuthoritativeCollectionOrSelection() {
        showAndSeed();
        commit(Activation.MOUSE, 12);
        JavaFxTestSupport.runAndWait(() -> {
            int stale = handle.beginTypeLoad();
            int current = handle.beginTypeLoad();
            handle.populateTypes(7, List.of(general(99, "Stale")), List.of(), stale);
            assertEquals(List.of(11, 12, 13), ids());
            assertEquals(12, handle.selectedTypeForTest().authoritativeTypeId());
            handle.populateTypes(8, List.of(general(98, "Other tenant")), List.of(), current);
            assertEquals(List.of(11, 12, 13), ids());
            handle.populateTypes(7, List.of(general(12, "Beta"), general(14, "Current")), List.of(), current);
            assertEquals(List.of(12, 14), ids());
            assertEquals(12, handle.selectedTypeForTest().authoritativeTypeId());
        });
    }

    private void showAndSeed() {
        handle = JavaFxTestSupport.runAndWait(() -> NewEventWizard.show(null, 7, LocalDate.of(2026, 8, 18),
                () -> List.of(option(41), option(42)), List::of, ignored -> null, Runnable::run));
        JavaFxTestSupport.runAndWait(() -> {
            int generation = handle.beginTypeLoad();
            handle.populateTypes(7,
                    List.of(general(11, "Duplicate"), general(12, "Beta"), general(13, "Duplicate")),
                    List.of(caseType(21, "Duplicate"), caseType(22, "Case Beta"), caseType(23, "Duplicate")), generation);
        });
    }

    private void commit(Activation activation, int id) {
        JavaFxTestSupport.runAndWait(() -> {
            handle.typeForTest().show();
            int index = -1;
            for (int i = 0; i < handle.typeForTest().getItems().size(); i++)
                if (handle.typeForTest().getItems().get(i).authoritativeTypeId() == id) index = i;
            assertTrue(index >= 0);
            handle.typeForTest().getSelectionModel().select(index);
            Event.fireEvent(handle.typeForTest(), activation.event());
        });
    }

    private void assignCase(int id) {
        JavaFxTestSupport.runAndWait(handle::openCaseSelectorForTest);
        JavaFxTestSupport.runAndWait(() -> {
            var item = handle.caseResultsForTest().getItems().stream().filter(c -> c.caseId() == id).findFirst().orElseThrow();
            handle.caseResultsForTest().getSelectionModel().select(item);
            Event.fireEvent(handle.caseResultsForTest(), key(KeyCode.ENTER));
        });
    }

    private void removeCase() {
        JavaFxTestSupport.runAndWait(() -> handle.caseFieldForTest().lookupAll(".button").stream()
                .filter(javafx.scene.control.Button.class::isInstance).map(javafx.scene.control.Button.class::cast)
                .filter(button -> "Remove".equals(button.getText())).findFirst().orElseThrow().fire());
    }

    private void assertChoice(NewEventWizard.SourceKind source, int id) {
        assertNotNull(handle.selectedTypeForTest());
        assertEquals(source, handle.selectedTypeForTest().sourceKind());
        assertEquals(id, handle.selectedTypeForTest().authoritativeTypeId());
        assertSame(handle.selectedTypeForTest(), handle.typeForTest().getValue());
    }

    private List<Integer> ids() {
        return handle.typeForTest().getItems().stream().map(NewEventWizard.TypeChoice::authoritativeTypeId).toList();
    }

    private void closeCurrent() {
        JavaFxTestSupport.runAndWait(handle::close);
        handle = null;
    }

    private static CalendarEventType general(int id, String name) {
        return new CalendarEventType(id, 7, "key-" + id, name, "#123456", id, true, null, null);
    }

    private static EffectiveCaseDateTypeDto caseType(int id, String name) {
        return new EffectiveCaseDateTypeDto(id, 7, null, name, null, "OTHER", "#654321", true, id,
                true, false, EffectiveCaseDateTypeDto.Origin.TENANT_CREATED, new byte[]{1});
    }

    private static NewCalendarEventDialog.CaseOption option(int id) {
        return new NewCalendarEventDialog.CaseOption(id, "Case " + id, "Attorney", "#abcdef", false);
    }

    private static MouseEvent mouse() {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 4, 4, 4, 4, MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, false, false, true, null);
    }

    private static KeyEvent key(KeyCode code) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
    }

    private enum Activation {
        MOUSE, ENTER, SPACE;
        Event event() { return this == MOUSE ? mouse() : key(this == ENTER ? KeyCode.ENTER : KeyCode.SPACE); }
    }
}
