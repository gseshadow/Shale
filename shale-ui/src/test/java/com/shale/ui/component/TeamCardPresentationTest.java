package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.testutil.JavaFxTestSupport;

class TeamCardPresentationTest {

    private static final Path CARDS_CSS = Path.of("src/main/resources/css/foundation/cards.css");
    private static final Path TEAM_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/TeamController.java");
    private static final Path USER_DAO = Path.of("../shale-data/src/main/java/com/shale/data/dao/UserDao.java");

    @Test
    void teamRouteUsesTheDirectoryRowsAuthoritativePersistedColor() throws Exception {
        String controller = Files.readString(TEAM_CONTROLLER);
        String dao = Files.readString(USER_DAO);

        assertAll(
                () -> assertTrue(controller.contains("UserCardFactory.Variant.TEAM_ACCENTED"),
                        "Team View must explicitly opt into the accented shared UserCard variant"),
                () -> assertTrue(controller.contains("row.color()"),
                        "Team View must pass the directory row's authoritative user color"),
                () -> assertTrue(dao.contains("u.Color,"),
                        "the Team directory query must continue reading dbo.Users.Color"),
                () -> assertTrue(dao.contains("rs.getString(\"Color\")"),
                        "the persisted color must continue flowing into DirectoryUserRow"));
    }

    @Test
    void gradientKeepsTheSemanticSurfaceBelowTheTransparentUserColorLayer() throws Exception {
        String css = Files.readString(CARDS_CSS);
        int rule = css.indexOf(".user-card-team-accented {");
        int semanticSurface = css.indexOf("-shale-color-elevated-surface,", rule);
        int gradient = css.indexOf("linear-gradient(to right", rule);

        assertAll(
                () -> assertTrue(rule >= 0, "Team UserCards need their scoped opt-in selector"),
                () -> assertTrue(semanticSurface > rule && semanticSurface < gradient,
                        "the opaque semantic surface must be the first/background JavaFX layer"),
                () -> assertTrue(css.contains("-shale-user-accent-strong 0%")),
                () -> assertTrue(css.contains("-shale-user-accent-sustained 65%")),
                () -> assertTrue(css.contains("-shale-user-accent-medium 84%")),
                () -> assertTrue(css.contains("-shale-user-accent-light 96%")),
                () -> assertTrue(css.contains("-shale-user-accent-clear 100%")));
    }

    @Test
    void lightAndDarkAuthoritativeColorsChooseContrastingNameForegrounds() {
        JavaFxTestSupport.runAndWait(() -> {
            UserCardFactory factory = new UserCardFactory(id -> { });
            UserCard light = factory.create(new UserCardModel(1, "Light", "#FFFF00", "LT"),
                    UserCardFactory.Variant.TEAM_ACCENTED);
            UserCard dark = factory.create(new UserCardModel(2, "Dark", "#07172C", "DK"),
                    UserCardFactory.Variant.TEAM_ACCENTED);

            assertAll(
                    () -> assertTrue(light.getStyle().contains("-shale-user-accent-strong: rgba(255,255,0,0.720)"),
                            "the stored light user color must drive the gradient"),
                    () -> assertTrue(light.getStyle().contains("-shale-user-name-foreground: #172033"),
                            "pale colors need the shared readable dark foreground"),
                    () -> assertTrue(dark.getStyle().contains("-shale-user-name-foreground: white"),
                            "dark colors need the shared readable light foreground"));
        });
    }

    @Test
    void missingAndInvalidColorsLeaveSemanticFallbacksInControl() {
        JavaFxTestSupport.runAndWait(() -> {
            UserCardFactory factory = new UserCardFactory(id -> { });
            for (String invalid : new String[] { null, "", "not-a-color;" }) {
                UserCard card = factory.create(new UserCardModel(1, "Fallback", invalid, null),
                        UserCardFactory.Variant.TEAM_ACCENTED);
                assertAll(
                        () -> assertTrue(card.getStyleClass().contains("user-card-team-accented")),
                        () -> assertTrue(card.getStyle() == null || card.getStyle().isEmpty(),
                                "invalid data must not override semantic surface or text tokens"));
            }
        });
    }

    @Test
    void accentedCardsKeepSharedEdgeShadowClickAndAccessibilityContracts() throws Exception {
        String css = Files.readString(CARDS_CSS);
        int teamRuleStart = css.indexOf(".user-card-team-accented {");
        String teamRule = css.substring(teamRuleStart, css.indexOf('}', teamRuleStart));
        AtomicInteger opened = new AtomicInteger();

        JavaFxTestSupport.runAndWait(() -> {
            UserCard card = new UserCardFactory(opened::set).create(
                    new UserCardModel(42, "Ada Lovelace", "#00FFFF", "AL"),
                    UserCardFactory.Variant.TEAM_ACCENTED);
            card.getOnMouseClicked().handle(null);

            assertAll(
                    () -> assertTrue(card.getStyleClass().contains("shale-entity-card"),
                            "the production root must participate in the shared card contract"),
                    () -> assertTrue(card.getStyleClass().contains("shale-entity-card-clickable")),
                    () -> assertEquals(42, opened.get(), "clicking must still open the authoritative user id"),
                    () -> assertEquals("User: Ada Lovelace", card.getAccessibleText(),
                            "the card must retain a meaningful accessible identity"));
        });

        assertAll(
                () -> assertTrue(css.contains("-fx-border-color: -shale-color-card-hover-border;")),
                () -> assertTrue(css.contains("-fx-border-width: 1.25;")),
                () -> assertTrue(css.contains("dropshadow(gaussian, -shale-color-card-shadow"),
                        "the shared entity-card contract must retain the semantic card shadow"),
                () -> assertFalse(teamRule.matches("(?s).*#[0-9a-fA-F]{3,8}.*"),
                        "the Team rule must not introduce a fixed light-theme edge or shadow"));
    }
}
