package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Protects the shared narrative-editor contract for Case Updates. */
final class CaseUpdatesNarrativeEditorTest {
    private static final String CONTROLLER = read("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final String FXML = read("src/main/resources/fxml/case.fxml");

    @Test
    void addUsesTransactionalSharedPopupAndExistingSavePath() {
        String open = method("private void onSubmitCaseUpdateInternal()", "private void saveNewCaseUpdate");
        String save = method("private void saveNewCaseUpdate", "private void handleMedicalRecordsRequestedSafeguardAfterSavedUpdate");

        assertTrue(FXML.contains("fx:id=\"submitCaseUpdateButton\" text=\"Add Case Update\""),
                "Case Updates should expose the established Add action");
        assertFalse(FXML.contains("fx:id=\"caseUpdatesComposerArea\""),
                "Case Updates should no longer expose a raw inline TextArea composer");
        assertTrue(open.contains("EnhancedTextArea.openEditor") && open.contains("\"Add Case Update\"")
                        && open.contains("this::saveNewCaseUpdate"),
                "Add must use the shared popup and persist only from its Apply callback");
        assertTrue(save.contains("trimmedText.isBlank()") && save.contains("Update text is required."),
                "The existing blank-update validation must remain before persistence");
        assertTrue(save.contains("caseDao.addCaseNote(activeCaseId, activeClientId, trimmedText, createdByUserId)"),
                "Apply must retain the authoritative Case Update DAO save path and actor context");
    }

    @Test
    void editUsesCurrentMarkdownAndCreatorRestrictedUpdatePath() {
        String permission = method("private boolean canEditCaseUpdate", "private void startEditingCaseUpdate");
        String open = method("private void startEditingCaseUpdate", "private void saveEditedCaseUpdate");
        String save = method("private void saveEditedCaseUpdate", "private static String safeAuthorName");

        assertTrue(permission.contains("actorUserId.intValue() == createdByUserId.intValue()"),
                "Only the update creator may be offered Edit");
        assertTrue(open.contains("EnhancedTextArea.openEditor") && open.contains("\"Edit Case Update\"")
                        && open.contains("safeText(dto.getNoteText())")
                        && open.contains("value -> saveEditedCaseUpdate(dto, value)"),
                "Edit must initialize the shared popup from persisted text and save only on Apply");
        assertTrue(save.contains("caseDao.updateCaseNote(caseUpdateId, activeCaseId, activeClientId, activeActorUserId, trimmedText)"),
                "Apply must preserve Case Update identity, case, tenant, actor, and the existing update path");
    }

    @Test
    void cardsRenderSupportedFormattingWithoutChangingLegacyPlainText() {
        String card = method("private Node createCaseUpdateCardInternal", "private String buildCaseUpdateMetadata");

        assertTrue(card.contains("NarrativeMarkdownCodec.plainText(safeText(dto.getNoteText()))"),
                "Read-only cards must use Shale's shared syntax-free narrative projection");
    }

    private static String method(String start, String end) {
        int from = CONTROLLER.indexOf(start);
        int to = CONTROLLER.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from, "Expected controller method boundaries for " + start);
        return CONTROLLER.substring(from, to);
    }

    private static String read(String file) {
        try {
            return Files.readString(Path.of(file));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
