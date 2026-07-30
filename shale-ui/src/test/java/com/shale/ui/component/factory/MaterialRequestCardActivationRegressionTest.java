package com.shale.ui.component.factory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestCardActivationRegressionTest {
    @Test
    void mouseEnterAndSpaceShareOneQueuedActivationPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/MaterialRequestCardFactory.java"));
        assertTrue(source.contains("private void activateRequest(Node card, long requestId)"));
        assertTrue(source.contains("Platform.runLater(() ->"),
                "Opening is queued once so input dispatch finishes and detached cards can be rejected.");
        assertEquals(1, count(source, "onOpenRequest.accept(requestId)"),
                "Mouse and keyboard handlers must not duplicate the detail-opening call.");
        assertTrue(source.contains("final long requestId = request.id()"));
        assertTrue(source.contains("card.getParent() == null || card.getScene() == null"));
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
        assertTrue(source.contains("stage.show()"), "Detail display must not block the FX queue in a nested event loop.");
    }

    private static int count(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }
}
