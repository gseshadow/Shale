package com.shale.ui.component.factory;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.shale.core.dto.MaterialRequestSummaryDto;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

final class MaterialRequestCardFactoryRenderingTest {
	@BeforeAll
	static void startJavaFxToolkit() {
		assumeTrue(hasDisplay(), "Material Request rendered card test requires a graphical display.");
		JavaFxTestSupport.ensureToolkitStarted();
	}

	@Test
	void renderedCardShowsMaterialRailAndStatusGradientThroughTransparentBody() throws Exception {
		RenderedMaterialRequestCard rendered = render(summary(LocalDateTime.of(2026, 7, 25, 12, 0)));
		try {
			assertTrue(rendered.card().getStyle().contains("linear-gradient(to right"), rendered.card().getStyle());
			assertTrue(rendered.card().getStyle().contains("rgba(226,232,240"), rendered.card().getStyle());
			assertFalse(rendered.card().getStyle().contains("#2F80ED"), "Due gradient must not use the material type color.");

			assertTrue(rendered.rail().getStyle().contains("#2F80ED"), rendered.rail().getStyle());
			assertFalse(rendered.rail().getStyle().contains(DueProximityStyles.DUE_WITHIN_ONE_WEEK_COLOR),
					"Material Type rail must not use due-proximity color.");
			assertEquals(7.0, rendered.rail().getPrefWidth(), 0.1);
			double railLayoutMaxX = rendered.rail().localToParent(rendered.rail().getLayoutBounds()).getMaxX();
			double bodyLayoutMinX = rendered.body().localToParent(rendered.body().getLayoutBounds()).getMinX();
			assertEquals(railLayoutMaxX, bodyLayoutMinX, 0.1,
					"HBox layout boxes must meet exactly; boundsInParent includes descendant effects and is not an occupancy boundary.");

			assertTrue(rendered.body().getStyle().contains("-fx-background-color: transparent"), rendered.body().getStyle());
			assertNotNull(rendered.card().getClip(), "The outer painted card should own the rounded clip.");
			assertTrue(rendered.userMiniCard().getStyle().contains("#7C3AED"), rendered.userMiniCard().getStyle());
			assertTrue(rendered.userMiniCard().getWidth() < rendered.card().getWidth() * 0.75,
					"MINI user card should remain compact inside the wider request card.");
		} finally {
			runFxAndWait(rendered.stage()::close);
		}
	}

	@Test
	void distantDueDateStillRendersConfiguredNeutralStatusGradientInsteadOfFlatWhite() throws Exception {
		RenderedMaterialRequestCard rendered = render(summary(LocalDateTime.of(2026, 8, 22, 0, 0)));
		try {
			assertTrue(rendered.card().getStyle().contains("linear-gradient(to right"), rendered.card().getStyle());
			assertTrue(rendered.card().getStyle().contains("rgba(226,232,240"), rendered.card().getStyle());
			assertTrue(rendered.rail().getStyle().contains("#2F80ED"), rendered.rail().getStyle());
		} finally {
			runFxAndWait(rendered.stage()::close);
		}
	}

	@Test
	void embeddedMiniPrimaryClicksNavigateAndDoNotBubbleToRequestCard() throws Exception {
		AtomicInteger requestOpens = new AtomicInteger();
		AtomicInteger organizationOpens = new AtomicInteger();
		AtomicInteger userOpens = new AtomicInteger();
		AtomicReference<RenderedMaterialRequestCard> ref = new AtomicReference<>();
		runFxAndWait(() -> ref.set(render(summary(LocalDateTime.of(2026, 8, 22, 0, 0)), requestOpens, new AtomicInteger(), organizationOpens, userOpens)));
		RenderedMaterialRequestCard rendered = ref.get();
		try {
			Node organization = findFirst(rendered.card(), com.shale.ui.component.OrganizationCard.class);
			Node user = findFirst(rendered.card(), com.shale.ui.component.UserCard.class);
			assertNotNull(organization);
			assertNotNull(user);

			runFxAndWait(() ->
			{
				organization.fireEvent(click(MouseButton.PRIMARY));
				user.fireEvent(click(MouseButton.PRIMARY));
				organization.fireEvent(click(MouseButton.SECONDARY));
				rendered.card().fireEvent(click(MouseButton.PRIMARY));
			});
			runFxAndWait(() ->
			{
			});

			assertEquals(1, organizationOpens.get(), "Organization MINI primary click should navigate once.");
			assertEquals(1, userOpens.get(), "User MINI primary click should navigate once.");
			assertEquals(1, requestOpens.get(), "Only the explicit card-background primary click should open the request.");
		} finally {
			runFxAndWait(rendered.stage()::close);
		}
	}

	@Test
	void distinctCardsCaptureTheirOwnIdsAcrossTheFxQueueInOrder() throws Exception {
		List<Long> opened = new ArrayList<>();
		AtomicInteger mutableSelection = new AtomicInteger();
		AtomicReference<HBox> a = new AtomicReference<>(), b = new AtomicReference<>();
		AtomicReference<Stage> stage = new AtomicReference<>();
		runFxAndWait(() ->
		{
			MaterialRequestCardFactory factory = new MaterialRequestCardFactory(opened::add);
			a.set((HBox) factory.create(summary(101L), MaterialRequestCardFactory.Variant.LIST));
			b.set((HBox) factory.create(summary(202L), MaterialRequestCardFactory.Variant.LIST));
			VBox list = new VBox(a.get(), b.get());
			stage.set(new Stage());
			stage.get().setScene(new Scene(list));
			stage.get().show();
			a.get().fireEvent(click(MouseButton.PRIMARY));
			mutableSelection.set(202); // must not affect A's already accepted activation
			b.get().fireEvent(click(MouseButton.PRIMARY));
		});
		runFxAndWait(() ->
		{
		}); // deterministically drain both queued activations
		try {
			assertEquals(List.of(101L, 202L), opened);
			assertEquals(101L, a.get().getProperties().get(MaterialRequestCardFactory.MATERIAL_REQUEST_ID_KEY));
			assertEquals(202L, b.get().getProperties().get(MaterialRequestCardFactory.MATERIAL_REQUEST_ID_KEY));
		} finally {
			runFxAndWait(stage.get()::close);
		}
	}

	@Test
	void mouseEnterAndSpaceUseTheSameExplicitCardIdentity() throws Exception {
		List<Long> opened = new ArrayList<>();
		AtomicReference<HBox> card = new AtomicReference<>();
		AtomicReference<Stage> stage = new AtomicReference<>();
		runFxAndWait(() ->
		{
			card.set((HBox) new MaterialRequestCardFactory(opened::add).create(summary(303L), MaterialRequestCardFactory.Variant.LIST));
			stage.set(new Stage());
			stage.get().setScene(new Scene(new StackPane(card.get())));
			stage.get().show();
			card.get().fireEvent(click(MouseButton.PRIMARY));
			card.get().fireEvent(key(KeyCode.ENTER));
			card.get().fireEvent(key(KeyCode.SPACE));
		});
		runFxAndWait(() ->
		{
		});
		try {
			assertEquals(List.of(303L, 303L, 303L), opened);
		} finally {
			runFxAndWait(stage.get()::close);
		}
	}

	@Test
	void cardDetachedBeforeQueuedActivationDrainsCannotOpen() throws Exception {
		List<Long> opened = new ArrayList<>();
		AtomicReference<Stage> stage = new AtomicReference<>();
		runFxAndWait(() ->
		{
			HBox stale = (HBox) new MaterialRequestCardFactory(opened::add).create(summary(404L), MaterialRequestCardFactory.Variant.LIST);
			VBox list = new VBox(stale);
			stage.set(new Stage());
			stage.get().setScene(new Scene(list));
			stage.get().show();
			stale.fireEvent(click(MouseButton.PRIMARY));
			list.getChildren().clear(); // asynchronous rebuild detaches the stale card before callback
		});
		runFxAndWait(() ->
		{
		});
		try {
			assertTrue(opened.isEmpty());
		} finally {
			runFxAndWait(stage.get()::close);
		}
	}

	@Test
	void contactMiniPrimaryClickNavigatesWithoutOpeningRequest() throws Exception {
		AtomicInteger requestOpens = new AtomicInteger();
		AtomicInteger contactOpens = new AtomicInteger();
		AtomicReference<RenderedMaterialRequestCard> ref = new AtomicReference<>();
		runFxAndWait(() -> ref.set(render(contactSummary(), requestOpens, contactOpens, new AtomicInteger(), new AtomicInteger())));
		RenderedMaterialRequestCard rendered = ref.get();
		try {
			Node contact = findFirst(rendered.card(), com.shale.ui.component.ContactCard.class);
			assertNotNull(contact);
			runFxAndWait(() ->
			{
				contact.fireEvent(click(MouseButton.PRIMARY));
				contact.fireEvent(click(MouseButton.SECONDARY));
			});
			runFxAndWait(() ->
			{
			});
			assertEquals(1, contactOpens.get(), "Contact MINI primary click should navigate once.");
			assertEquals(0, requestOpens.get(), "Handled embedded MINI click must not bubble to request navigation.");
		} finally {
			runFxAndWait(rendered.stage()::close);
		}
	}

	@Test
	void cardUsesComputedContentHeightAndNormalGapBeforeDates() throws Exception {
		RenderedMaterialRequestCard rendered = render(summary(LocalDateTime.of(2026, 8, 22, 0, 0)));
		try {
			FlowPane facts = (FlowPane) rendered.card().lookup(".material-request-card__facts");
			assertNotNull(facts);
			assertEquals(7, facts.getChildren().size(), "Typical hydrated request should include the current Next Follow-up fact.");
			assertEquals(Priority.NEVER, VBox.getVgrow(rendered.userMiniCard()),
					"MINI cards should not grow vertically to push date facts down.");
			assertNull(VBox.getVgrow(facts), "Facts section must keep natural height.");
			assertEquals(Region.USE_PREF_SIZE, rendered.card().getMaxHeight(), 0.1,
					"The card should refuse parent-provided spare height and use its computed content height.");

			assertTrue(facts.getBoundsInParent().getMaxY() <= rendered.body().getHeight() - rendered.body().getPadding().getBottom() + 0.5,
					"Facts remain fully visible inside modest bottom padding.");
			assertEquals(18.0, facts.getHgap(), 0.1);
			assertEquals(7.0, facts.getVgap(), 0.1);
			double computedBodyHeight = rendered.body().prefHeight(rendered.body().getWidth());
			assertEquals(computedBodyHeight, rendered.body().getHeight(), 1.0,
					"The seven-fact card must use its computed content height (facts=" + facts.getHeight()
							+ ", bodyWidth=" + rendered.body().getWidth() + ", card=" + rendered.card().getHeight() + ").");
			assertTrue(rendered.card().getHeight() < rendered.stage().getScene().getHeight() - 80,
					"A short request should not stretch to fill the available scene height.");
			assertEquals(rendered.card().getHeight(), rendered.card().getClip().getBoundsInLocal().getHeight(), 0.5,
					"Rounded clip height tracks the final computed card height.");
		} finally {
			runFxAndWait(rendered.stage()::close);
		}
	}

	@Test
	void wrappedTitleGrowsOnlyItsOwnCardAndDoesNotResizeSiblingCards() throws Exception {
		AtomicReference<RenderedList> ref = new AtomicReference<>();
		runFxAndWait(() ->
		{
			HBox shortCard = (HBox) new MaterialRequestCardFactory(id ->
			{
			}).create(summary(LocalDateTime.of(2026, 8, 22, 0, 0)), MaterialRequestCardFactory.Variant.LIST);
			HBox wrappedCard = (HBox) new MaterialRequestCardFactory(id ->
			{
			}).create(summaryWithTitle(
					"A very long material request title that should wrap onto multiple lines at narrower widths while preserving every field and growing naturally"),
					MaterialRequestCardFactory.Variant.LIST);
			VBox list = new VBox(10, shortCard, wrappedCard);
			list.setPadding(new Insets(8));
			list.setFillWidth(true);
			StackPane root = new StackPane(list);
			Scene scene = new Scene(root, 360, 520);
			scene.getStylesheets().add(MaterialRequestCardFactoryRenderingTest.class.getResource("/css/app.css").toExternalForm());
			Stage stage = new Stage();
			stage.setScene(scene);
			stage.show();
			root.applyCss();
			root.layout();
			ref.set(new RenderedList(stage, list, shortCard, wrappedCard));
		});
		RenderedList rendered = ref.get();
		try {
			assertTrue(rendered.second().getHeight() > rendered.first().getHeight(),
					"Wrapped title should grow its own card naturally.");
			assertTrue(rendered.first().getHeight() < rendered.second().getHeight() - 8,
					"Sibling cards must not inherit the tallest card height.");
		} finally {
			runFxAndWait(rendered.stage()::close);
		}
	}

	@Test
	void listInsetsExposeParentAroundMultipleRoundedCardsAndPreserveResponsiveWidth() throws Exception {
		AtomicReference<RenderedList> ref = new AtomicReference<>();
		runFxAndWait(() ->
		{
			HBox first = (HBox) new MaterialRequestCardFactory(id ->
			{
			}).create(summary(LocalDateTime.of(2026, 8, 22, 0, 0)), MaterialRequestCardFactory.Variant.LIST);
			HBox second = (HBox) new MaterialRequestCardFactory(id ->
			{
			}).create(summary(LocalDateTime.of(2026, 7, 25, 12, 0)), MaterialRequestCardFactory.Variant.LIST);
			VBox list = new VBox(10, first, second);
			list.setPadding(new Insets(8));
			list.setFillWidth(true);
			list.setStyle("-fx-background-color: #D9E2EC;");
			StackPane root = new StackPane(list);
			root.setStyle("-fx-background-color: #001122;");
			Scene scene = new Scene(root, 520, 420);
			scene.getStylesheets().add(MaterialRequestCardFactoryRenderingTest.class.getResource("/css/app.css").toExternalForm());
			Stage stage = new Stage();
			stage.setScene(scene);
			stage.show();
			root.applyCss();
			root.layout();
			ref.set(new RenderedList(stage, list, first, second));
		});
		RenderedList rendered = ref.get();
		try {
			Insets padding = rendered.list().getPadding();
			assertEquals(8.0, padding.getTop(), 0.1);
			assertEquals(8.0, padding.getRight(), 0.1);
			assertEquals(8.0, padding.getBottom(), 0.1);
			assertEquals(8.0, padding.getLeft(), 0.1);
			assertEquals(10.0, rendered.second().getBoundsInParent().getMinY() - rendered.first().getBoundsInParent().getMaxY(), 1.0,
					"Only VBox spacing should separate multiple request cards, avoiding doubled margins.");
			assertEquals(rendered.list().getWidth() - padding.getLeft() - padding.getRight(), rendered.first().getWidth(), 1.0,
					"Card should resize to the container width minus the intended external insets.");
			assertTrue(rendered.first().getBoundsInParent().getMinX() >= padding.getLeft() - 0.1);
			assertTrue(rendered.first().getBoundsInParent().getMinY() >= padding.getTop() - 0.1);
			assertSame(rendered.first().getParent(), rendered.list(), "No wrapper should be inserted between the list and the request card surface.");
			assertNotNull(rendered.first().getClip(), "Rounded card clip remains on the only card surface.");
		} finally {
			runFxAndWait(rendered.stage()::close);
		}
	}

	private static RenderedMaterialRequestCard render(MaterialRequestSummaryDto summary) throws Exception {
		AtomicReference<RenderedMaterialRequestCard> ref = new AtomicReference<>();
		runFxAndWait(() -> ref.set(render(summary, new AtomicInteger(), new AtomicInteger(), new AtomicInteger(), new AtomicInteger())));
		return ref.get();
	}

	private static RenderedMaterialRequestCard render(MaterialRequestSummaryDto summary, AtomicInteger requestOpens, AtomicInteger contactOpens, AtomicInteger organizationOpens,
			AtomicInteger userOpens) {
		Node cardNode = new MaterialRequestCardFactory(id -> requestOpens.incrementAndGet(), id -> contactOpens.incrementAndGet(), id -> organizationOpens.incrementAndGet(),
				id -> userOpens.incrementAndGet()).create(summary, MaterialRequestCardFactory.Variant.LIST);
		HBox card = (HBox) cardNode;
		StackPane root = new StackPane(card);
		root.setStyle("-fx-background-color: #001122; -fx-padding: 24;");
		Scene scene = new Scene(root, 720, 360);
		scene.getStylesheets().add(MaterialRequestCardFactoryRenderingTest.class.getResource("/css/app.css").toExternalForm());
		Stage stage = new Stage();
		stage.setScene(scene);
		stage.show();
		root.applyCss();
		root.layout();
		Region rail = (Region) card.lookup(".material-request-card__material-type-rail");
		VBox body = (VBox) card.lookup(".material-request-card__body");
		Region userMiniCard = findFirstUserMiniCard(card);
		return new RenderedMaterialRequestCard(stage, card, rail, body, userMiniCard);
	}

	private static <T> T findFirst(Node root, Class<T> type) {
		if (type.isInstance(root))
			return type.cast(root);
		if (root instanceof javafx.scene.Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				T found = findFirst(child, type);
				if (found != null)
					return found;
			}
		}
		return null;
	}

	private static Region findFirstUserMiniCard(Node root) {
		if (root instanceof com.shale.ui.component.UserCard userCard)
			return userCard;
		if (root instanceof javafx.scene.Parent parent) {
			for (Node child : parent.getChildrenUnmodifiable()) {
				Region found = findFirstUserMiniCard(child);
				if (found != null)
					return found;
			}
		}
		return null;
	}

	private static MaterialRequestSummaryDto summaryWithTitle(String title) {
		return new MaterialRequestSummaryDto(
				1L, 10, 6502L, 3, "Medical records", null, "#2F80ED", title,
				11, "Brian Downing", "#7C3AED", 11, "Brian Downing", "#7C3AED",
				null, null, 22, "Blue Cross Blue Shield", null, "Portal", LocalDateTime.of(2026, 7, 23, 9, 0),
				"REQUESTED", LocalDateTime.of(2026, 8, 22, 0, 0), LocalDateTime.of(2026, 7, 30, 9, 0), null, LocalDateTime.of(2026, 7, 23, 9, 0),
				new byte[] { 1 });
	}

	private static MouseEvent click(MouseButton button) {
		return new MouseEvent(MouseEvent.MOUSE_CLICKED, 4, 4, 4, 4, button, 1,
				false, false, false, false, button == MouseButton.PRIMARY, button == MouseButton.MIDDLE, button == MouseButton.SECONDARY, false, false, true, null);
	}

	private static KeyEvent key(KeyCode code) {
		return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, false, false, false, false);
	}

	private static MaterialRequestSummaryDto summary(long id) {
		return new MaterialRequestSummaryDto(
				id, 10, 6502L, 3, "Medical records", null, "#2F80ED", "Request " + id,
				11, "Requestor", "#7C3AED", 12, "Assignee", "#059669",
				null, null, 22, "Organization", null, "Portal", LocalDateTime.of(2026, 7, 23, 9, 0),
				"REQUESTED", null, null, null, LocalDateTime.of(2026, 7, 23, 9, 0),
				new byte[] { 1 });
	}

	private static MaterialRequestSummaryDto contactSummary() {
		return new MaterialRequestSummaryDto(
				2L, 10, 6502L, 3, "Medical records", null, "#2F80ED", "Contact Request",
				11, "Brian Downing", "#7C3AED", 12, "Assigned User", "#059669",
				44, "Chris Contact", null, null, null, "Portal", LocalDateTime.of(2026, 7, 23, 9, 0),
				"REQUESTED", LocalDateTime.of(2026, 8, 22, 0, 0), LocalDateTime.of(2026, 7, 30, 9, 0), null, LocalDateTime.of(2026, 7, 23, 9, 0),
				new byte[] { 1 });
	}

	private static MaterialRequestSummaryDto summary(LocalDateTime due) {
		return new MaterialRequestSummaryDto(
				1L, 10, 6502L, 3, "Medical records", null, "#2F80ED", "Test Medical Records Request",
				11, "Brian Downing", "#7C3AED", 11, "Brian Downing", "#7C3AED",
				null, null, 22, "Blue Cross Blue Shield", null, "Portal", LocalDateTime.of(2026, 7, 23, 9, 0),
				"REQUESTED", due, LocalDateTime.of(2026, 7, 30, 9, 0), null, LocalDateTime.of(2026, 7, 23, 9, 0),
				new byte[] { 1 });
	}

	private static void runFxAndWait(Runnable action) throws Exception {
		JavaFxTestSupport.runAndWait(action::run);
	}

	private static boolean hasDisplay() {
		String display = System.getenv("DISPLAY");
		String wayland = System.getenv("WAYLAND_DISPLAY");
		return (display != null && !display.isBlank()) || (wayland != null && !wayland.isBlank());
	}

	private record RenderedMaterialRequestCard(Stage stage, HBox card, Region rail, VBox body, Region userMiniCard) {
	}

	private record RenderedList(Stage stage, VBox list, HBox first, HBox second) {
	}
}
