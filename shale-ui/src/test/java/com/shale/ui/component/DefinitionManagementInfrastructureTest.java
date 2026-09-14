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
                    "CaseDate", "Classification", "OrganizationType")) {
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

    private static String read(String relative) throws Exception {
        return Files.readString(MAIN.resolve(relative));
    }
}
