package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

final class DefinitionManagementInfrastructureTest {
    private static final Path MAIN = Path.of("src/main/java/com/shale/ui");

    @Test void committedChangesAccumulateWithoutCountingOrResetting() {
        CommittedChangeTracker changes = new CommittedChangeTracker();
        assertFalse(changes.hasCommittedChanges(), "a clean management session must report unchanged");

        changes.markCommitted();
        changes.markCommitted();

        assertTrue(changes.hasCommittedChanges(), "one or many successful mutations produce one changed result");
    }

    @Test void sharedInfrastructureRemainsFeatureAndServiceNeutral() throws Exception {
        for (String file : List.of("component/DefinitionManagementWindow.java",
                "component/DefinitionManagementSession.java", "component/CommittedChangeTracker.java")) {
            String source = Files.readString(MAIN.resolve(file));
            for (String forbidden : List.of("CaseService", "ContactService", "OrganizationService", ".dto.",
                    "CaseDate", "Classification", "OrganizationType", "UserDao", "UserManagement")) {
                assertFalse(source.contains(forbidden), file + " must not depend on " + forbidden);
            }
        }
    }

    @Test void allLaunchersUseOneSessionAndAllPanesReceiveExternalExecutors() throws Exception {
        for (String feature : List.of("CaseDateType", "ContactClassification", "OrganizationType")) {
            String launcher = read("controller/" + feature + "ManagementLauncher.java");
            String pane = read("controller/" + (feature.equals("CaseDateType")
                    ? "CaseDateTypeManagementPane.java" : feature + "AdminPane.java"));
            assertTrue(launcher.contains("new DefinitionManagementSession()"), feature + " must use the shared session");
            assertTrue(launcher.contains("session.show("), feature + " must delegate window lifecycle to the session");
            assertTrue(pane.contains("Executor "), feature + " pane must receive an executor");
            assertFalse(pane.contains("Executors.new"), feature + " pane must not create an executor");
            assertFalse(pane.contains(".shutdown"), feature + " pane must not shut down its caller-owned executor");
        }
    }

    @Test void windowCompletionAndFocusContractsAreSingleAndSafe() throws Exception {
        String window = read("component/DefinitionManagementWindow.java");
        assertTrue(window.contains("completed.compareAndSet(false, true)"), "competing close paths must complete once");
        assertTrue(window.contains("if (!canClose.getAsBoolean()) e.consume()"), "mutation close must be preventable");
        assertTrue(window.contains("owner != null && owner.isShowing()"), "a closed owner must not be refocused");
    }

    @Test void contentModesKeepScrollableManagersAsTheDefaultAndAllowViewportOwnedLayouts() throws Exception {
        String window = read("component/DefinitionManagementWindow.java");
        String session = read("component/DefinitionManagementSession.java");
        String userLauncher = read("controller/UserManagementLauncher.java");

        assertTrue(session.contains("ContentMode.SCROLLABLE"),
                "existing managers must retain the shared outer-scroll behavior by default");
        assertTrue(window.contains("contentMode == ContentMode.SCROLLABLE ? scrollable(content) : content"),
                "fixed-layout content must enter the shared viewport without another ScrollPane");
        assertTrue(userLauncher.contains("DefinitionManagementWindow.ContentMode.FIXED"),
                "User Management must opt into viewport-owned layout without shared-window feature coupling");
        assertFalse(window.contains("UserManagementPane"),
                "the shared window must select layout by mode rather than by feature class");
    }

    @Test void sharedWindowOwnsOneTitleThemeAndA2ManagementVocabulary() throws Exception {
        String window = read("component/DefinitionManagementWindow.java");
        String appCss = Files.readString(Path.of("src/main/resources/css/app.css"));
        String managementCss = Files.readString(Path.of("src/main/resources/css/foundation/management.css"));

        assertTrue(window.contains("applySecondaryDialogShell(dialog, title)"),
                "the themed secondary shell must own the canonical title");
        assertFalse(window.contains("new Label(title)"),
                "manager content must not duplicate the title already owned by the shell");
        assertTrue(window.contains("Modality.WINDOW_MODAL"), "manager modality must remain window-modal");
        assertTrue(window.contains("sizeModalStage(stage, owner, 900, 700, 680, 520)"),
                "manager sizing must be owner-screen aware and retain usable minimums");
        assertTrue(appCss.contains("@import \"foundation/management.css\";"),
                "the focused management foundation must be loaded by the theme entry point");
        for (String selector : List.of(".management-window", ".management-window-header",
                ".management-window-content", ".management-window-footer", ".audit-management-root")) {
            assertTrue(managementCss.contains(selector), "missing shared management selector " + selector);
        }
    }

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
