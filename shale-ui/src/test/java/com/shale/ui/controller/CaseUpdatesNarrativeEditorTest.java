package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.shale.ui.component.EnhancedTextArea;
import com.shale.ui.component.richtext.NarrativeMarkdownCodec;

/** Protects the shared narrative-editor contract for Case Updates. */
final class CaseUpdatesNarrativeEditorTest {
    private static final String CONTROLLER = read("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final String FXML = read("src/main/resources/fxml/case.fxml");
    private static final String ENHANCED_TEXT_AREA = read("src/main/java/com/shale/ui/component/EnhancedTextArea.java");

    @Test
    void newUpdateUsesInlineEnhancedDraftAndSubmitRemainsAuthoritative() {
        String submit = method("private void onSubmitCaseUpdateInternal()", "private void handleMedicalRecordsRequestedSafeguardAfterSavedUpdate");

        assertTrue(FXML.contains("<EnhancedTextArea fx:id=\"caseUpdatesComposerArea\""),
                "Case Updates should keep an immediately editable inline EnhancedTextArea draft");
        assertFalse(FXML.contains("<TextArea fx:id=\"caseUpdatesComposerArea\""),
                "The inline composer must not regress to a raw JavaFX TextArea");
        assertTrue(FXML.contains("editorTitle=\"Case Update\"")
                        && FXML.contains("expandable=\"true\"")
                        && FXML.contains("spellCheckEnabled=\"true\"")
                        && FXML.contains("prefRowCount=\"4\""),
                "The compact composer should expose the shared popup and authenticated spellcheck defaults");
        assertTrue(FXML.contains("fx:id=\"submitCaseUpdateButton\" text=\"Submit\""),
                "The existing Submit action must remain authoritative for creation");
        assertTrue(submit.contains("caseUpdatesComposerArea.getText()"),
                "Submit must read the current inline draft, including popup-applied changes");
        assertFalse(submit.contains("EnhancedTextArea.openEditor"),
                "Submit must not be replaced by a popup-only Add workflow");
        assertTrue(submit.contains("trimmedText.isBlank()") && submit.contains("Update text is required."),
                "The existing blank-update validation must remain before persistence");
        assertTrue(submit.contains("caseDao.addCaseNote(activeCaseId, activeClientId, trimmedText, createdByUserId)"),
                "Submit must retain the authoritative Case Update DAO save path and actor context");
        assertTrue(submit.contains("caseUpdatesComposerArea.setText(\"\")")
                        && submit.contains("renderCaseUpdates(updates)"),
                "A successful Submit must clear the draft and refresh saved updates");
    }

    @Test
    void composerPopupApplyChangesOnlyDraftAndCancelLeavesItUntouched() {
        EnhancedTextArea composer = new EnhancedTextArea();
        composer.setText("Order records from UNM");

        var applied = composer.createExpandedEdit();
        applied.setDraft("**Order** records from UNM");
        composer.applyExpandedEdit(applied);
        assertTrue("**Order** records from UNM".equals(composer.getText()),
                "Applying the expanded form editor should update only its inline draft value");

        var cancelled = composer.createExpandedEdit();
        cancelled.setDraft("This popup change is cancelled");
        assertTrue("**Order** records from UNM".equals(composer.getText()),
                "Discarding the isolated popup draft must leave the inline value unchanged");
        assertTrue(ENHANCED_TEXT_AREA.contains("isSpellCheckEnabled(), this::setText"),
                "The embedded popup Apply callback must target the control value, not Case persistence");
        assertFalse(ENHANCED_TEXT_AREA.contains("CaseDao"),
                "The shared form editor must remain independent from Case Update persistence");
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
        assertFalse(cardMethod().contains("new TextArea") || cardMethod().contains("new Button(\"Save\")")
                        || cardMethod().contains("new Button(\"Cancel\")"),
                "Saved-update cards must remain read-only instead of entering a raw inline edit state");
    }

    @Test
    void cardsRenderSupportedFormattingWithoutChangingLegacyPlainText() {
        String card = cardMethod();

        assertTrue(card.contains("NarrativeMarkdownCodec.plainText(safeText(dto.getNoteText()))"),
                "Read-only cards must use Shale's shared syntax-free narrative projection");
        assertTrue("Important\n• Call client".equals(
                        NarrativeMarkdownCodec.plainText("**Important**\n- Call client")),
                "Supported formatting should render without exposing Markdown syntax");
        assertTrue("Legacy plain text".equals(NarrativeMarkdownCodec.plainText("Legacy plain text")),
                "Legacy plain-text updates should display unchanged");
    }

    private static String cardMethod() {
        return method("private Node createCaseUpdateCardInternal", "private String buildCaseUpdateMetadata");
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
