package com.shale.ui.component.factory;

import com.shale.core.dto.MaterialRequestStatusHistoryDto;
import com.shale.core.dto.MaterialRequestSummaryDto;
import com.shale.core.dto.RequestStatusDto;
import com.shale.ui.component.OrganizationCard;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class MaterialRequestCardGestureRegressionTest {
    private Stage stage;
    private VBox list;
    private final List<Long> opened = new ArrayList<>();

    @BeforeAll
    static void startToolkit() {
        assumeTrue(hasDisplay(), "Requires Xvfb or a graphical display");
        JavaFxTestSupport.ensureToolkitStarted();
    }

    @AfterEach
    void closeStage() {
        if (stage != null) JavaFxTestSupport.runAndWait(stage::close);
    }

    @RepeatedTest(25)
    void firstFreshClickAcrossVisibleNonInteractiveRegionsOpensOnce() {
        HBox card = showCard(101L);
        for (String selector : List.of(".material-request-card__title", ".material-request-card__body",
                ".material-request-card__date-fact", ".status-timeline__pill")) {
            Node target = JavaFxTestSupport.runAndWait(() -> card.lookup(selector));
            assertNotNull(target, selector);
            fireClick(target, 10, 10, 10, 10, true);
        }
        fireClick(card, 4, 4, 4, 4, true); // padded/background surface
        drainFxQueue();
        assertEquals(List.of(101L, 101L, 101L, 101L, 101L), opened);
    }

    @Test
    void twoFreshCardsKeepDistinctIdentityInOneRenderedList() {
        HBox[] cards = showCards(101L, 202L);
        fireClick(cards[0].lookup(".material-request-card__title"), 8, 8, 8, 8, true);
        fireClick(cards[1].lookup(".material-request-card__date-fact"), 8, 8, 8, 8, true);
        drainFxQueue();
        assertEquals(List.of(101L, 202L), opened);
    }

    @Test
    void tinyMovementClicksButTrueDragDoesNotAndDoesNotArmLaterClicks() {
        HBox card = showCard(303L);
        fireClick(card.lookup(".material-request-card__title"), 10, 10, 11, 10, true);
        drainFxQueue();
        assertEquals(List.of(303L), opened);

        JavaFxTestSupport.runAndWait(() -> {
            card.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, 10, 10, true));
            card.fireEvent(mouse(MouseEvent.DRAG_DETECTED, 25, 10, false));
            card.fireEvent(mouse(MouseEvent.MOUSE_DRAGGED, 40, 10, false));
            card.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, 40, 10, false));
        });
        drainFxQueue();
        assertEquals(List.of(303L), opened, "A true drag must not synthesize request activation.");

        fireClick(card, 10, 10, 10, 10, true);
        drainFxQueue();
        assertEquals(List.of(303L, 303L), opened, "Dragging must not arm or repair later clicks.");
    }

    @Test
    void onlyVisibleInteractiveMiniCardSuppressesBackgroundActivation() {
        HBox card = showCard(404L);
        OrganizationCard organization = find(card, OrganizationCard.class);
        assertNotNull(organization);
        fireClick(organization, 4, 4, 4, 4, true);
        drainFxQueue();
        assertTrue(opened.isEmpty());

        fireClick(card.lookup(".material-request-card__body"), 4, 4, 4, 4, true);
        drainFxQueue();
        assertEquals(List.of(404L), opened);
    }

    @Test
    void mouseEnterSpaceAndDetachedGuardRetainExplicitIdentity() {
        HBox card = showCard(505L);
        fireClick(card, 4, 4, 4, 4, true);
        JavaFxTestSupport.runAndWait(() -> {
            card.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
            card.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.SPACE, false, false, false, false));
        });
        drainFxQueue();
        assertEquals(List.of(505L, 505L, 505L), opened);

        JavaFxTestSupport.runAndWait(() -> {
            card.fireEvent(mouse(MouseEvent.MOUSE_CLICKED, 4, 4, true));
            list.getChildren().clear();
        });
        drainFxQueue();
        assertEquals(List.of(505L, 505L, 505L), opened);
    }

    private HBox showCard(long id) { return showCards(id)[0]; }

    private HBox[] showCards(long... ids) {
        return JavaFxTestSupport.runAndWait(() -> {
            MaterialRequestCardFactory factory = new MaterialRequestCardFactory(opened::add, null, ignored -> { }, null);
            HBox[] cards = new HBox[ids.length];
            for (int i = 0; i < ids.length; i++) cards[i] = create(factory, ids[i]);
            list = new VBox(10, cards);
            stage = new Stage(); stage.setScene(new Scene(list, 900, 700)); stage.show();
            list.applyCss(); list.layout();
            return cards;
        });
    }

    private static HBox create(MaterialRequestCardFactory factory, long id) {
        LocalDateTime at = LocalDateTime.of(2026, 7, 23, 9, 0);
        var history = List.of(new MaterialRequestStatusHistoryDto(id, 10, 6502, id, "REQUESTED", "REQUESTED", 1, "Actor", at));
        var statuses = List.of(new RequestStatusDto(1, 10, "REQUESTED", "Requested", "#2F80ED", 1, true, false, new byte[]{1}));
        return (HBox) factory.create(summary(id), MaterialRequestCardFactory.Variant.LIST, "Requested", "#2F80ED", history, statuses);
    }

    private static MaterialRequestSummaryDto summary(long id) {
        LocalDateTime at = LocalDateTime.of(2026, 7, 23, 9, 0);
        return new MaterialRequestSummaryDto(id, 10, 6502, 3, "Medical records", null, "#2F80ED", "Request",
                null, null, null, null, null, null, null, null, 22, "Organization", null, "Portal", at,
                "REQUESTED", null, null, at, at, new byte[]{1});
    }

    private void fireClick(Node target, double pressX, double pressY, double releaseX, double releaseY, boolean still) {
        JavaFxTestSupport.runAndWait(() -> {
            target.fireEvent(mouse(MouseEvent.MOUSE_PRESSED, pressX, pressY, still));
            target.fireEvent(mouse(MouseEvent.MOUSE_RELEASED, releaseX, releaseY, still));
            target.fireEvent(mouse(MouseEvent.MOUSE_CLICKED, releaseX, releaseY, still));
        });
    }

    private static MouseEvent mouse(javafx.event.EventType<MouseEvent> type, double x, double y, boolean still) {
        return new MouseEvent(type, x, y, x, y, MouseButton.PRIMARY, 1, false, false, false, false,
                type != MouseEvent.MOUSE_RELEASED, false, false, false, false, still, null);
    }

    private static <T> T find(Node root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof javafx.scene.Parent parent) for (Node child : parent.getChildrenUnmodifiable()) {
            T found = find(child, type); if (found != null) return found;
        }
        return null;
    }

    private static void drainFxQueue() { JavaFxTestSupport.runAndWait(() -> { }); }
    private static boolean hasDisplay() { return System.getenv("DISPLAY") != null || System.getenv("WAYLAND_DISPLAY") != null; }
}
