package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinkSharesPhase534DialogLayoutTest {
    private static final Path CASE_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test
    void mainLinkDialogUsesFixedFooterScrollableScreenSafeContent() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String method = source.substring(source.indexOf("private Optional<CaseLinkInput> showCaseLinkDialog"),
                source.indexOf("private static ScrollPane screenSafeDialogScrollPane"));
        assertTrue(method.contains("screenSafeDialogScrollPane(grid)"));
        assertTrue(source.contains("scroll.setFitToWidth(true)"));
        assertTrue(source.contains("ScrollPane.ScrollBarPolicy.NEVER"));
        assertTrue(method.contains("dialog.setResizable(true)"));
        assertTrue(method.contains("dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL)"));
        assertTrue(method.contains("applyContentSizedDialogBounds(dialog, caseLinksOwner()"));
        assertTrue(method.contains("scrollFocusedNodeIntoView(formScroll)"));
    }

    @Test
    void sharedWithSummaryBoundsContactCardsAboveEditAction() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String editor = source.substring(source.indexOf("private final class SharedWithEditor"),
                source.indexOf("private record ShareDetails"));
        assertTrue(editor.contains("adaptiveContactScrollPane(cards"));
        assertTrue(editor.contains("Shared With Contact Cards"));
        assertTrue(editor.contains("root.getChildren().addAll(heading, cardsScroll, edit)"));
        assertTrue(editor.contains("Edit Shared With"));
    }

    @Test
    void shareModalBoundsSelectedCaseContactsAndGivesAllContactsGrowPriority() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String modal = source.substring(source.indexOf("private Optional<List<StagedShare>> showShareSelectionDialog"),
                source.indexOf("private void toggleWorking", source.indexOf("private Optional<List<StagedShare>> showShareSelectionDialog")));
        assertTrue(modal.contains("dialog.getDialogPane().getButtonTypes().setAll(ButtonType.APPLY, ButtonType.CANCEL)"));
        assertTrue(modal.contains("boundedVerticalScrollPane(selectedBox"));
        assertTrue(modal.contains("boundedVerticalScrollPane(caseRows"));
        assertTrue(modal.contains("VBox.setVgrow(allList, Priority.ALWAYS)"));
        assertTrue(modal.contains("VBox.setVgrow(allContactsSection, Priority.ALWAYS)"));
        assertTrue(modal.contains("applyScreenSafeDialogBounds(dialog"));
        assertTrue(modal.contains("FlowPane actions"));
    }
}
