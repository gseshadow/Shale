package com.shale.ui.component.factory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestCardActivationRegressionTest {
    @Test
    void mouseEnterAndSpaceShareOneQueuedActivationPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/MaterialRequestCardFactory.java"));
        assertTrue(source.contains("Runnable activate = () -> Platform.runLater(() -> onOpenRequest.accept(request.id()))"),
                "Opening is queued once so the mouse dispatch finishes before a modal nested event loop starts.");
        assertEquals(1, count(source, "onOpenRequest.accept(request.id())"),
                "Mouse and keyboard handlers must not duplicate the detail-opening call.");
        assertTrue(source.contains("if (e.getButton() == MouseButton.PRIMARY)"));
        assertTrue(source.contains("e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.SPACE"));
        assertTrue(source.contains("card.setFocusTraversable(true)"));
        assertFalse(source.contains("focusedProperty().addListener"));
        assertFalse(source.contains("hoverProperty().addListener"));
    }

    @Test
    void detailControllerEnforcesFxThreadAndUsesEstablishedOwnedModalStage() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java"));
        assertTrue(source.contains("if(!Platform.isFxApplicationThread())throw new IllegalStateException"));
        assertTrue(source.contains("AppDialogs.createModalStage(owner.get(),\"Material Request\")"));
        assertTrue(source.contains("stage.showAndWait()"));
    }

    private static int count(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
