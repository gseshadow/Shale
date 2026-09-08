package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CaseOverviewEditorDialogTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/component/dialog/CaseOverviewEditorDialog.java");

    @Test void rendersDistinctShownAndAvailableGroupsFromTheirAuthoritativeModelOrders() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("new Label(\"Shown on Overview\")"));
        assertTrue(source.contains("new Label(\"Available dates\")"));
        assertTrue(source.contains("for(EffectiveCaseDateTypeDto type:model.shownTypes())"));
        assertTrue(source.contains("for(EffectiveCaseDateTypeDto type:model.availableTypes())"));
    }

    @Test void onlyShownRowsReceiveMovementControlsAndSaveUsesThatSameOrderedState() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("if(shown){Button up="), "available rows must not render active movement controls");
        assertTrue(source.contains("up.setDisable(!model.canMoveUp(type.id()))"));
        assertTrue(source.contains("down.setDisable(!model.canMoveDown(type.id()))"));
        assertTrue(source.contains("new Submission(model.selectedIds()"), "submission must use the shown-row order");
    }

    @Test void usesCustomWindowChromeWithDragCloseEscapeAndDirtyCloseLifecycle() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("AppDialogs.applySecondaryWindowChrome(stage)"), "native window decoration must be hidden");
        assertTrue(source.contains("AppDialogs.createSecondaryWindowShell(stage,\"Edit Overview\",close,content)"), "the Shale header must retain its draggable close affordance");
        assertTrue(source.contains("KeyCode.ESCAPE"));
        assertTrue(source.contains("close.run()"), "Escape must use the same dirty-close confirmation path");
        assertTrue(source.contains("stage.initOwner(owner)"));
        assertTrue(source.contains("stage.initModality(Modality.WINDOW_MODAL)"));
        assertTrue(source.contains("stage.setOnCloseRequest"));
        assertTrue(source.contains("WindowSizingUtil.sizeModalStage"));
    }
}
