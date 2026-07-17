package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseLinkSharesPhase537ContentSizingTest {
    private static final Path CASE_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path WINDOW_SIZING = Path.of("src/main/java/com/shale/ui/util/WindowSizingUtil.java");

    @Test
    void addEditLinkUsesContentSizedModalWithoutForcedViewportOrStageHeight() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String method = source.substring(source.indexOf("private Optional<CaseLinkInput> showCaseLinkDialog"),
                source.indexOf("private static ScrollPane screenSafeDialogScrollPane"));

        assertTrue(method.contains("formScroll.setPrefViewportWidth(720)"));
        assertFalse(method.contains("setPrefViewportHeight(520)"));
        assertFalse(method.contains("applyScreenSafeDialogBounds(dialog, caseLinksOwner(), 780, 680"));
        assertTrue(method.contains("applyContentSizedDialogBounds(dialog, caseLinksOwner(), formScroll, grid, 780, 560, 420)"));
        assertTrue(method.contains("sharedWithEditor.setOnSummaryChanged(resizeCaseLinkDialog)"));
        assertTrue(method.contains("dialog.setResizable(true)"));
    }

    @Test
    void contentSizingMeasuresNaturalContentAndClampsToScreenSafeBounds() throws Exception {
        String source = Files.readString(WINDOW_SIZING);
        String helper = source.substring(source.indexOf("public static void sizeContentModalStage"),
                source.indexOf("public static void constrainToVisualBounds"));

        assertTrue(helper.contains("dialogPane.applyCss()"));
        assertTrue(helper.contains("dialogPane.layout()"));
        assertTrue(helper.contains("content.prefHeight(viewportWidth)"));
        assertTrue(helper.contains("dialogPane.prefHeight(width)"));
        assertTrue(helper.contains("visualBounds.getHeight() * MODAL_SCREEN_RATIO"));
        assertTrue(helper.contains("scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)"));
        assertTrue(helper.contains("scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)"));
        assertTrue(helper.contains("centerModal(stage, owner"));
        assertTrue(helper.contains("constrainToVisualBounds(stage, owner)"));
    }

    @Test
    void sharedWithSummaryRequestsResizeAfterEmptyFewManyAndRemovalStates() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String editor = source.substring(source.indexOf("private final class SharedWithEditor"),
                source.indexOf("private record ShareDetails"));

        assertTrue(editor.contains("private Runnable onSummaryChanged"));
        assertTrue(editor.contains("void setOnSummaryChanged"));
        assertTrue(editor.contains("Platform.runLater(onSummaryChanged)"));
        assertTrue(editor.contains("root.requestLayout()"));
        assertTrue(editor.contains("root.getChildren().add(share)"));
        assertTrue(editor.contains("root.getChildren().addAll(heading, cardsScroll, edit)"));
        assertTrue(editor.contains("staged.clear(); staged.addAll"));
    }

    @Test
    void scrollPanePreservesScreenSafeFormBehavior() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String helper = source.substring(source.indexOf("private static ScrollPane screenSafeDialogScrollPane"),
                source.indexOf("private static ScrollPane boundedVerticalScrollPane"));

        assertTrue(helper.contains("scroll.setFitToWidth(true)"));
        assertTrue(helper.contains("scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)"));
        assertTrue(helper.contains("scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)"));
        assertTrue(helper.contains("transparent-scroll"));
        assertTrue(helper.contains("case-link-styled-scroll"));
    }

    @Test
    void shareLinkContactBrowserKeepsExistingScreenSafeGrowableSizing() throws Exception {
        String source = Files.readString(CASE_CONTROLLER);
        String modal = source.substring(source.indexOf("private Optional<List<StagedShare>> showShareSelectionDialog"),
                source.indexOf("private void toggleWorking", source.indexOf("private Optional<List<StagedShare>> showShareSelectionDialog")));

        assertTrue(modal.contains("content.setPrefHeight(620)"));
        assertTrue(modal.contains("VBox.setVgrow(allList, Priority.ALWAYS)"));
        assertTrue(modal.contains("applyScreenSafeDialogBounds(dialog, modalOwner, 820, 700, 600, 460)"));
        assertFalse(modal.contains("applyContentSizedDialogBounds"));
    }
}
