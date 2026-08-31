package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class FunctionalSurfaceContractTest {
    private static final Path FXML = Path.of("src/main/resources/fxml");

    @Test
    void foundationDefinesTranslucentSemanticFunctionalSurfaces() throws IOException {
        String colors = Files.readString(Path.of("src/main/resources/css/foundation/colors.css"));
        String surfaces = Files.readString(Path.of("src/main/resources/css/foundation/surfaces.css"));
        String app = Files.readString(Path.of("src/main/resources/css/app.css"));

        assertTrue(colors.contains("-shale-color-page-header-surface: rgba(239, 246, 253, 0.90)"));
        assertTrue(colors.contains("-shale-color-functional-content-surface: rgba(242, 248, 255, 0.88)"));
        assertTrue(colors.contains("-shale-color-data-surface: rgba(235, 244, 252, 0.86)"));
        assertTrue(surfaces.contains(".page-header-surface"));
        assertTrue(surfaces.contains(".content-surface"));
        assertTrue(surfaces.contains(".data-surface"));
        assertTrue(app.contains("radial-gradient("), "the decorative application gradient must remain");
    }

    @Test
    void detailHeadersAndPageNavigationUseSharedHeaderSurface() throws IOException {
        for (String view : new String[] {"case.fxml", "contact.fxml", "organization.fxml", "user.fxml"}) {
            assertTrue(read(view).contains("styleClass=\"page-header-surface\""), view);
        }
        assertTrue(read("case.fxml").contains("styleClass=\"app-section-tabs-scroll page-header-surface\""));
        assertTrue(read("my-shale.fxml").contains("styleClass=\"app-section-tabs-scroll page-header-surface\""));
    }

    @Test
    void majorCollectionViewsUseSharedDataSurface() throws IOException {
        for (String view : new String[] {
                "cases.fxml", "contacts.fxml", "organizations.fxml", "team.fxml", "settings.fxml", "my-shale.fxml"
        }) {
            assertTrue(read(view).contains("data-surface"), view);
        }
    }

    private static String read(String name) throws IOException {
        return Files.readString(FXML.resolve(name));
    }
}
