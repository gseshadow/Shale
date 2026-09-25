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

import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;

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
                    () -> assertTrue(light.getStyle().contains("-shale-user-avatar-background: #FFFF00FF"),
                            "the avatar needs the authoritative persisted light color as a solid fill"),
                    () -> assertTrue(light.getStyle().contains("-shale-user-avatar-foreground: #172033"),
                            "the avatar initials need the shared readable dark foreground"),
                    () -> assertTrue(dark.getStyle().contains("-shale-user-name-foreground: white"),
                            "dark colors need the shared readable light foreground"),
                    () -> assertTrue(dark.getStyle().contains("-shale-user-avatar-background: #07172CFF"),
                            "the avatar needs the authoritative persisted dark color as a solid fill"),
                    () -> assertTrue(dark.getStyle().contains("-shale-user-avatar-foreground: white"),
                            "dark avatar colors need the shared readable light foreground"));
        });
    }

    @Test
    void initialsComeFromUnicodeSafeFirstAndLastMeaningfulNameWords() {
        JavaFxTestSupport.runAndWait(() -> {
            UserCardFactory factory = new UserCardFactory(id -> { });
            String[][] examples = {
                    { "Brian Downing", "BD" },
                    { "Isela Anchondo", "IA" },
                    { "Pauline Guillen-Montano", "PG" },
                    { "Assistant CCO", "AC" },
                    { "Cher", "C" },
                    { "  Mary   Jane   Watson  ", "MW" },
                    { "\u2003Nora\u2003\u2003Jones\u2003", "NJ" },
                    { "élise 李", "É李" },
                    { "\uD801\uDC28 test", "\uD801\uDC00T" },
                    { "", "?" }
            };

            for (int i = 0; i < examples.length; i++) {
                UserCard card = factory.create(new UserCardModel(i, examples[i][0], "#1677F2", null),
                        UserCardFactory.Variant.TEAM_ACCENTED);
                Label initials = (Label) findById(card, "user-card-avatar-initials");
                assertEquals(examples[i][1], initials.getText(),
                        "initials must use Unicode-safe first/last meaningful name characters for " + examples[i][0]);
                assertTrue(initials.getText().codePointCount(0, initials.getText().length()) <= 2,
                        "initials must never exceed two Unicode characters");
            }
        });
    }

    @Test
    void initialsRemainInsideTheExistingAvatarWithoutCreatingAnAccessibleTarget() {
        JavaFxTestSupport.runAndWait(() -> {
            UserCard card = new UserCardFactory(id -> { }).create(
                    new UserCardModel(1, "Ada Lovelace", "#00FFFF", null),
                    UserCardFactory.Variant.TEAM_ACCENTED);
            Label initials = (Label) findById(card, "user-card-avatar-initials");
            Circle circle = (Circle) findByStyleClass(card, "user-card-avatar-circle");

            assertAll(
                    () -> assertEquals(26.0, circle.getRadius(), "the existing full-card avatar size must remain unchanged"),
                    () -> assertEquals(initials.getParent(), circle.getParent(),
                            "StackPane must center the initials over the existing circle"),
                    () -> assertTrue(initials.isMouseTransparent(), "initials must not change the card click target"),
                    () -> assertFalse(initials.isFocusTraversable(), "initials must not become a keyboard target"),
                    () -> assertEquals(AccessibleRole.NODE, initials.getAccessibleRole(),
                            "initials must not announce a duplicate abbreviated identity"),
                    () -> assertEquals("User: Ada Lovelace", card.getAccessibleText(),
                            "the full card identity must remain authoritative"));
        });
    }

    @Test
    void initialsAreScopedToTeamAccentedCards() {
        JavaFxTestSupport.runAndWait(() -> {
            UserCardFactory factory = new UserCardFactory(id -> { });
            for (UserCardFactory.Variant variant : new UserCardFactory.Variant[] {
                    UserCardFactory.Variant.FULL, UserCardFactory.Variant.COMPACT, UserCardFactory.Variant.MINI }) {
                UserCard card = factory.create(new UserCardModel(1, "Ada Lovelace", "#00FFFF", null), variant);
                assertEquals(null, findById(card, "user-card-avatar-initials"),
                        "non-Team UserCard variants must not gain the initials label: " + variant);
                assertFalse(card.getStyleClass().contains("user-card-team-accented"));
            }
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
    void invalidColorFallbacksRemainThemeSemantic() throws Exception {
        String css = Files.readString(CARDS_CSS);
        assertAll(
                () -> assertTrue(css.contains("-shale-user-avatar-background: -shale-color-avatar-neutral-background;")),
                () -> assertTrue(css.contains("-shale-user-avatar-foreground: -shale-color-avatar-neutral-text;")),
                () -> assertTrue(css.contains("-fx-fill: -shale-user-avatar-background;")),
                () -> assertTrue(css.contains("-fx-text-fill: -shale-user-avatar-foreground;")));
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

    private static Node findById(Node root, String id) {
        if (id.equals(root.getId())) return root;
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findById(child, id);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Node findByStyleClass(Node root, String styleClass) {
        if (root.getStyleClass().contains(styleClass)) return root;
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findByStyleClass(child, styleClass);
                if (found != null) return found;
            }
        }
        return null;
    }
}
