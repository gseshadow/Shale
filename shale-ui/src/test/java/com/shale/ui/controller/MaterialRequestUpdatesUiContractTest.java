package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class MaterialRequestUpdatesUiContractTest {
    @Test void detailEditorHasUnifiedResilientAppendOnlyUpdatesFeed() throws Exception {
        String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java"));
        assertTrue(s.contains("requestUpdatesSection(stage,requestId"));
        assertTrue(s.contains("Add Note"));
        assertTrue(s.contains("No updates yet."));
        assertTrue(s.contains("Updates could not be loaded."));
        assertTrue(s.contains("Your text has been kept."));
        assertTrue(s.contains("submitting.compareAndSet(false,true)"));
        assertTrue(s.contains("editor.clear()"));
        assertTrue(s.contains("detailRef.set(refreshed)"), "Adding a note must refresh the editor RowVer");
        assertTrue(s.contains("svc.getMaterialRequest(caseId,requestId,tenant,actor)"));
        assertTrue(s.contains("renderRequestUpdates"));
        assertTrue(s.contains("row.actorDisplayName()"));
        assertTrue(s.contains("row.createdAt()"));
        assertTrue(s.contains("isUserNote(row.updateType())"), "Persisted UpdateType must classify note cards");
        assertTrue(s.contains("new VBox(6,meta,body)"), "User-note metadata must precede its body");
        assertTrue(s.contains("new VBox(1,body,meta)"), "System descriptions must precede secondary metadata");
        assertTrue(s.contains("request-update-note-card"));
        assertTrue(s.contains("request-update-system-row"));
        assertFalse(s.contains("row.body().contains"), "Classification must not infer type from message content");
        assertFalse(s.contains("editMaterialRequestUpdate"));
        assertFalse(s.contains("deleteMaterialRequestUpdate"));
        assertFalse(s.contains("Button editUpdate"));
    }
    @Test void persistedUpdateTypeAloneControlsHighlightedPresentation(){
        assertTrue(CaseMaterialRequestsTabController.isUserNote("USER_NOTE"));
        assertFalse(CaseMaterialRequestsTabController.isUserNote("SYSTEM_CHANGE"));
        assertFalse(CaseMaterialRequestsTabController.isUserNote("SYSTEM_EVENT"));
        assertFalse(CaseMaterialRequestsTabController.isUserNote("user_note"), "Classification must not infer capitalization");
        assertFalse(CaseMaterialRequestsTabController.isUserNote("Title changed"), "Classification must not infer message wording");
    }
    @Test void detailUsesStableTwoColumnBodyWithIsolatedFooter() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java"));
        assertTrue(source.contains("materialRequestDetailColumns(fieldsScroll,updatesSection)"));
        assertTrue(source.contains("fieldsConstraint.setPercentWidth(58)"));
        assertTrue(source.contains("updatesConstraint.setPercentWidth(42)"));
        assertTrue(source.contains("setHgrow(Priority.ALWAYS)"));
        assertTrue(source.contains("setVgrow(Priority.ALWAYS)"));
        assertTrue(source.contains("new VBox(12,detailBody,footer)"),"Footer must remain outside both scrolling columns");
        assertTrue(source.contains("ScrollPane fieldsScroll=new ScrollPane(fieldsColumn)"));
        assertTrue(source.contains("ScrollPane historyScroll=new ScrollPane(feed)"));
        assertTrue(source.contains("VBox.setVgrow(historyScroll,Priority.ALWAYS)"));
        assertTrue(source.contains("historyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)"));
        assertTrue(source.contains("historyScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)"));
        assertTrue(source.contains("REQUEST_DETAIL_WIDTH=1080"));
        assertTrue(source.contains("REQUEST_DETAIL_MIN_WIDTH=900"));
        assertEquals(1,count(source,"requestUpdatesSection(stage,requestId"));
        assertEquals(1,count(source,"setPromptText(\"Add a note about this request…\")"));
        assertTrue(source.contains("Button cancel=ActionButtonFactory.neutral"));
        assertTrue(source.contains("Button delete=ActionButtonFactory.danger"));
        assertTrue(source.contains("Button save=ActionButtonFactory.primary"));
        String css=Files.readString(Path.of("src/main/resources/css/app.css"));
        assertTrue(css.contains(".material-request-updates-history"));
        assertTrue(css.contains(".material-request-updates"));
        assertTrue(css.contains("-fx-wrap-text: true"));
        assertTrue(css.contains(".request-update-note-card"));
        assertTrue(css.contains(".request-update-system-row .request-update-body"));
        assertTrue(css.contains("rgba(52, 110, 201, 0.22)"), "Note surface must match Task History");
    }

    private static int count(String source,String needle){int count=0,index=0;while((index=source.indexOf(needle,index))>=0){count++;index+=needle.length();}return count;}

}
