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
        assertTrue(s.contains("renderRequestUpdates"));
        assertTrue(s.contains("row.actorDisplayName()"));
        assertTrue(s.contains("row.createdAt()"));
        assertFalse(s.contains("editMaterialRequestUpdate"));
        assertFalse(s.contains("deleteMaterialRequestUpdate"));
    }
}
