package com.shale.ui.whatsnew;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.shale.core.model.ReleaseItemType;
import com.shale.core.model.SemanticVersion;
import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.Theme;
import com.shale.ui.theme.ThemeManager;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

final class WhatsNewDialogTest {
	@Test void mapsOneScrollableAggregateWithAllSafeItemTreatmentsAndSemanticDismissButton() {
		JavaFxTestSupport.runAndWait(() -> {
			VBox root = WhatsNewDialog.createContent(presentation(), () -> {});
			assertEquals("UPDATES SINCE YOUR LAST VISIT", text(root, ".whats-new-eyebrow"));
			assertEquals("Shale 1.0.125", text(root, ".whats-new-current-version"));
			assertEquals(2, root.lookupAll(".whats-new-release").size(), "skipped releases belong in one dialog");
			assertTrue(root.lookup(".whats-new-scroll") instanceof ScrollPane, "release content must have a vertical scroll boundary");
			Set<String> types = root.lookupAll(".whats-new-item-type").stream().map(WhatsNewDialogTest::nodeText).collect(Collectors.toSet());
			assertEquals(Set.of("NEW","FIX","IMPROVED","IMPORTANT","LINK","VIDEO"), types);
			assertEquals(1, root.lookupAll(".whats-new-item-important").size(), "important is emphasis, not enforcement");
			assertEquals(2, root.lookupAll(".whats-new-resource").size(), "links and videos remain simple text resources");
			Button done = (Button) root.lookup(".shale-control-primary");
			assertEquals("Got it", done.getText()); assertTrue(done.isDefaultButton());
		});
	}

	@Test void themeManagerWiresTheSameDialogToLightAndDarkTokenSheets() {
		JavaFxTestSupport.runAndWait(() -> {
			ThemeManager themes = new ThemeManager();
			Scene scene = new Scene(WhatsNewDialog.createContent(presentation(), () -> {}));
			themes.register(scene);
			assertTrue(scene.getStylesheets().stream().anyMatch(url -> url.endsWith("/css/theme/light.css")));
			themes.setActiveTheme(Theme.DARK);
			assertTrue(scene.getStylesheets().stream().anyMatch(url -> url.endsWith("/css/theme/dark.css")));
			assertTrue(scene.getStylesheets().stream().anyMatch(url -> url.endsWith("/css/app.css")));
		});
	}

	private static WhatsNewPresentation presentation() {
		List<WhatsNewPresentation.Item> first = List.of(
			item(1,ReleaseItemType.FEATURE),item(2,ReleaseItemType.FIX),item(3,ReleaseItemType.IMPROVEMENT));
		List<WhatsNewPresentation.Item> second = List.of(
			item(4,ReleaseItemType.IMPORTANT),item(5,ReleaseItemType.LINK),item(6,ReleaseItemType.VIDEO));
		return new WhatsNewPresentation(new SemanticVersion(1,0,125),new SemanticVersion(1,0,125),2,List.of(
			new WhatsNewPresentation.ReleaseSection(1,new SemanticVersion(1,0,123),"First summary",first),
			new WhatsNewPresentation.ReleaseSection(2,new SemanticVersion(1,0,125),"Second summary",second)));
	}
	private static WhatsNewPresentation.Item item(long id,ReleaseItemType type){return new WhatsNewPresentation.Item(id,type,"Title "+id,"Body "+id,(type==ReleaseItemType.LINK||type==ReleaseItemType.VIDEO)?"https://example.com/"+id:null);}
	private static String text(Node root,String selector){return nodeText(root.lookup(selector));}
	private static String nodeText(Node node){return ((javafx.scene.control.Labeled)node).getText();}
}
