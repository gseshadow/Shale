package com.shale.ui.theme;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class AppearanceLifecycleContractTest {
    @Test
    void loginResolvesAppearanceOffThreadAndAppliesBeforeMainShell() throws Exception {
        String login = Files.readString(Path.of("src/main/java/com/shale/ui/controller/LoginController.java"));
        int load = login.indexOf("loadAppearanceForAuthenticatedUser()");
        int apply = login.indexOf("applyAppearanceBeforeMain(resolvedAppearance");
        int show = login.indexOf("sceneManager.showMain()", apply);
        assertTrue(load > 0 && apply > load && show > apply,
                "authenticated Appearance must load before it is applied and before the shell is shown");
        assertTrue(login.contains("appearance = Theme.LIGHT"), "load failure must preserve login with Light");
    }

    @Test
    void sessionBoundaryResetsLightAndRejectsAStaleIdentity() throws Exception {
        String scenes = Files.readString(Path.of("src/main/java/com/shale/ui/navigation/SceneManager.java"));
        assertTrue(scenes.contains("ThemeManager.application().setActiveTheme(Theme.LIGHT)"));
        assertTrue(scenes.contains("Objects.equals(appState.getUserId(), expectedUserId)"));
        assertTrue(scenes.contains("Objects.equals(appState.getShaleClientId(), expectedTenantId)"));
        assertFalse(scenes.contains("Preferences.userRoot"), "Appearance must not use machine-local Java Preferences");
    }
}
