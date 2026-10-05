package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

final class LoginViewContractTest {
    private static final Path FXML = Path.of("src/main/resources/fxml/login.fxml");
    private static final Path CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/LoginController.java");

    @Test
    void loginResourceKeepsApprovedCopyAndSessionPlaceholder() throws Exception {
        var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(FXML.toFile());
        assertTrue(document.getDocumentElement().getTagName().endsWith("StackPane"),
                "login resource must retain one parseable root");
        String fxml = Files.readString(FXML);

        assertTrue(fxml.contains("Your practice. In order."), "approved login headline must remain exact");
        assertTrue(fxml.contains("Cases, tasks, and your team. Together in Shale."),
                "approved login subtitle must remain exact");
        assertTrue(fxml.contains("Welcome back") && fxml.contains("Sign in to your workspace."),
                "the sign-in card must retain its approved hierarchy");
        assertTrue(fxml.contains("Only on your personal device.") && fxml.contains("Coming soon"),
                "persistent-session copy must describe its unavailable state");
        assertTrue(fxml.contains("fx:id=\"stayLoggedInCheckBox\"") && fxml.contains("disable=\"true\""),
                "Stay logged in must remain an explicitly disabled placeholder");
        assertFalse(fxml.contains("forgot") || fxml.contains("support"),
                "login must not offer an unimplemented help destination");
    }

    @Test
    void controllerPreservesLoginInteractionAndAnimationLifecycle() throws Exception {
        String source = Files.readString(CONTROLLER).replace("\r\n", "\n");

        assertTrue(source.contains("bindBidirectional(visiblePasswordField.textProperty())"),
                "password reveal must preserve a single entered value");
        assertTrue(source.contains("selectRange(anchor, caret)"),
                "password reveal must restore the selection and caret");
        assertTrue(source.contains("authenticationInProgress.compareAndSet(false, true)"),
                "authentication must reject duplicate submissions");
        assertTrue(source.contains("Boolean.getBoolean(\"shale.ui.reduceMotion\")"),
                "login motion must support the application reduced-motion override");
        assertTrue(source.contains("public void dispose()") && source.contains("backgroundAnimation.stop()"),
                "view-owned background motion must stop when the login view is disposed");
    }
}
