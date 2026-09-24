package com.shale.ui.controller;

import com.shale.ui.theme.ThemeManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Advisory rendered-layout contract for the production shell and routed page roots. */
final class ApplicationShellCssRuntimeTest {
	private static final double EPSILON = 0.01;

	@Test
	void productionFxmlUsesTheComputedShellGeometryAndTransparentRouteRoots() throws Exception {
		Process process = new ProcessBuilder(
				Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-cp", System.getProperty("java.class.path"), Probe.class.getName())
				.redirectErrorStream(true).start();
		assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Application-shell JavaFX probe timed out");
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(0, process.exitValue(), output);
		assertFalse(output.contains("CSS Error"), output);
	}

	public static final class Probe {
		public static void main(String[] args) throws Exception {
			CountDownLatch started = new CountDownLatch(1);
			CountDownLatch finished = new CountDownLatch(1);
			AtomicReference<Throwable> failure = new AtomicReference<>();
			Platform.startup(started::countDown);
			require(started.await(10, TimeUnit.SECONDS), "JavaFX probe did not start");
			Platform.runLater(() ->
			{
				try {
					verifyShell();
					verifyRoute("/fxml/my-shale.fxml", "#myTasksPanel");
					verifyRoute("/fxml/cases.fxml", null);
					verifyRoute("/fxml/case.fxml", "#overviewPane");
				} catch (Throwable thrown) {
					failure.set(thrown);
				} finally {
					finished.countDown();
				}
			});
			require(finished.await(25, TimeUnit.SECONDS), "JavaFX probe did not finish");
			Platform.exit();
			if (failure.get() != null)
				throw new AssertionError("Application-shell JavaFX probe failed", failure.get());
		}

		private static void verifyShell() throws Exception {
			Parent root = load("/fxml/main.fxml");
			Scene scene = styledScene(root, 1400, 900);
			root.applyCss();
			root.layout();

			Region toolbar = node(root, "#topToolbar", Region.class);
			Insets padding = toolbar.getPadding();
			require(padding.getTop() >= 8 && padding.getBottom() >= 8,
					"toolbar computed vertical padding must be at least 8px: " + padding);
			require(padding.getLeft() >= 10 && padding.getRight() >= 10,
					"toolbar computed horizontal padding must be at least 10px: " + padding);

			Node brand = node(root, "#brandLabel", Label.class);
			Node back = node(root, "#backButton", Button.class);
			Node search = node(root, "#globalSearchField", Region.class);
			Node searchButton = node(root, "#globalSearchButton", Button.class);
			Node intake = node(root, "#newIntakeButton", Button.class);
			Node trailing = node(root, ".shell-trailing-actions", Region.class);
			require(sceneX(brand) >= 10, "brand must begin at least 10px from the window edge");
			require(gap(brand, back) >= 10, "brand/back gap must be at least 10px");
			require(gap(back, search) >= 10, "back/search gap must be at least 10px");
			require(gap(search, searchButton) >= 8, "search field/button gap must be at least 8px");
			require(gap(searchButton, intake) >= 10, "search/New Intake gap must be at least 10px");
			require(scene.getWidth() - sceneMaxX(trailing) >= 10, "trailing controls must retain the right inset");
			double tallest = List.of(brand, back, search, searchButton, intake, trailing).stream()
					.mapToDouble(node -> node.getBoundsInParent().getHeight()).max().orElseThrow();
			require(toolbar.getHeight() + EPSILON >= tallest + padding.getTop() + padding.getBottom(),
					"toolbar height must include its computed vertical padding");

			Region sidebar = node(root, "#navigationSidebar", Region.class);
			VBox items = node(root, "#navigationItems", VBox.class);
			Insets itemPadding = items.getPadding();
			require(itemPadding.getLeft() >= 10 && itemPadding.getRight() >= 10,
					"inner navigation container must own at least 10px horizontal padding: " + itemPadding);
			List<Button> buttons = items.getChildren().stream().map(Button.class::cast).toList();
			for (Button button : buttons) {
				Bounds buttonBounds = button.localToScene(button.getBoundsInLocal());
				Bounds sidebarBounds = sidebar.localToScene(sidebar.getBoundsInLocal());
				require(buttonBounds.getMinX() - sidebarBounds.getMinX() >= 10,
						button.getText() + " left inset must be at least 10px");
				require(sidebarBounds.getMaxX() - buttonBounds.getMaxX() >= 10,
						button.getText() + " right inset must be at least 10px");
			}
			require(close(items.getSpacing(), 6),
					"navigation container must configure 6px vertical spacing");

			for (int index = 1; index < buttons.size(); index++) {
				double actualGap = buttons.get(index).getBoundsInParent().getMinY()
						- buttons.get(index - 1).getBoundsInParent().getMaxY();

				require(actualGap >= 5 && actualGap <= 7,
						"rendered navigation gap must remain within one snapped pixel of 6px"
								+ " actual=" + actualGap);
			}

			Region plane = node(root, ".primary-content-surface", Region.class);
			require(close(plane.getBackground().getFills().getFirst().getRadii().getTopLeftHorizontalRadius(), 14),
					"shared content-plane radius must be 14px");
			require(plane.getLayoutX() >= 16 && plane.getLayoutY() >= 14,
					"application canvas must remain visible around the shared content plane");
		}

		private static void verifyRoute(String resource, String opaqueInnerSelector) throws Exception {
			Parent root = load(resource);
			styledScene(root, 1100, 760);
			root.applyCss();
			root.layout();
			assertTrue(root instanceof Region, "Routed page root must be a Region");

			Region rootRegion = (Region) root;
			Background background = rootRegion.getBackground();
			require(isTransparent(background),
					resource + " routed page root must remain transparent");
			if (opaqueInnerSelector != null) {
			    Region inner = node(root, opaqueInnerSelector, Region.class);
			    require(!isTransparent(inner.getBackground()),
			            resource + " inner card/content surface must remain opaque");
			}
		}

		private static Parent load(String resource) throws Exception {
			var url = Probe.class.getResource(resource);
			if (url == null)
				throw new AssertionError("Missing " + resource);
			return FXMLLoader.load(url);
		}

		private static Scene styledScene(Parent root, double width, double height) {
			Scene scene = new Scene(root, width, height);
			ThemeManager.application().register(scene);
			return scene;
		}

		private static boolean isTransparent(Background background) {
			return background == null || background.getFills().isEmpty()
					|| background.getFills().stream().allMatch(fill -> fill.getFill() instanceof Color color
							&& color.getOpacity() == 0);
		}

		private static double gap(Node left, Node right) {
			return sceneX(right) - sceneMaxX(left);
		}

		private static double sceneX(Node node) {
			return node.localToScene(node.getBoundsInLocal()).getMinX();
		}

		private static double sceneMaxX(Node node) {
			return node.localToScene(node.getBoundsInLocal()).getMaxX();
		}

		private static boolean close(double actual, double expected) {
			return Math.abs(actual - expected) < EPSILON;
		}

		private static <T extends Node> T node(Parent root, String selector, Class<T> type) {
			Node result = root.lookup(selector);
			if (result == null)
				throw new AssertionError("Missing production node " + selector);
			return type.cast(result);
		}

		private static void require(boolean condition, String message) {
			if (!condition)
				throw new AssertionError(message);
		}
	}
}
