package com.shale.ui.component;

import com.shale.ui.testutil.JavaFxTestSupport;
import com.shale.ui.theme.*;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.scene.input.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UserSelectorMultipleSelectionTest {
    private record User(int id, String name, String color) {
        User(int id, String name) { this(id, name, null); }
    }
    @Test void nativeRowsToggleIndependentlyExposeCheckedStateAndKeepSelectionWhenSearching() {
        JavaFxTestSupport.runAndWait(() -> {
            var selector = selector();
            var a = new User(17,"Alpha"); var b = new User(18,"Beta");
            selector.setCandidates(List.of(a,b));
            Scene scene = new Scene(new StackPane(selector),520,400); Stage stage = new Stage(); stage.setScene(scene); stage.show();
            try {
                selector.applyCss(); selector.layout();
                var rows = selector.lookupAll(".user-selector-choice-row").stream().map(CheckBox.class::cast).sorted(Comparator.comparing(CheckBox::getText)).toList();
                assertEquals(2,rows.size());
                rows.getFirst().fire(); rows.getLast().fire();
                assertEquals(Set.of(a,b),new HashSet<>(selector.getSelectedUsers()));
                assertEquals(Boolean.TRUE, rows.getFirst().queryAccessibleAttribute(javafx.scene.AccessibleAttribute.SELECTED));
                assertEquals("Alpha",rows.getFirst().getAccessibleText());
                rows.getFirst().requestFocus();
                rows.getFirst().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED," "," ",KeyCode.SPACE,false,false,false,false));
                rows.getFirst().fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED," "," ",KeyCode.SPACE,false,false,false,false));
                assertEquals(List.of(b),selector.getSelectedUsers(),"Space must toggle only the focused author");
                ((TextField)selector.lookup(".text-field")).setText("Alpha");
                assertEquals(List.of(b),selector.getSelectedUsers(),"Search cannot discard hidden checked rows");
                ((TextField)selector.lookup(".text-field")).clear();
                assertTrue(selector.lookupAll(".user-selector-choice-row").stream().map(CheckBox.class::cast).filter(c -> c.getText().equals("Beta")).findFirst().orElseThrow().isSelected());
            } finally { stage.close(); }
        });
    }
    @Test void bothThemesResolveSelectedRowPaintAndFocusWithoutNestedInteractiveControls() {
        JavaFxTestSupport.runAndWait(() -> {
            var selector = selector(); var a = new User(17,"Alex Morgan", "#125A84");
            selector.setCandidates(List.of(a,new User(18,"Jordan Lee", "#FFFF00"),new User(19,"Sam Rivera with a longer full name")));
            selector.setSelectedUsers(List.of(a));
            StackPane root = new StackPane(selector); root.setStyle("-fx-padding: 16px;");
            Scene scene = new Scene(root,520,400); ThemeManager manager = new ThemeManager(); manager.register(scene);
            try {
                for (Theme theme : Theme.values()) {
                    manager.setActiveTheme(theme); root.applyCss(); root.layout();
                    CheckBox row = selector.lookupAll(".user-selector-choice-row").stream().map(CheckBox.class::cast).filter(CheckBox::isSelected).findFirst().orElseThrow();
                    assertNotNull(row.getBackground()); assertNotNull(row.getBorder()); assertTrue(row.isFocusTraversable());
                    assertEquals(javafx.scene.AccessibleRole.CHECK_BOX, row.getAccessibleRole());
                    assertInstanceOf(UserCard.class, row.getGraphic());
                    UserCard card = (UserCard)row.getGraphic();
                    assertTrue(card.isMouseTransparent(), "All card clicks must reach the native checkbox");
                    assertFalse(card.isFocusTraversable(), "A card must not create a second keyboard target");
                    assertEquals("AM", ((javafx.scene.control.Label)card.lookup("#user-card-avatar-initials")).getText());
                    var avatar = (javafx.scene.shape.Circle)card.lookup(".user-card-avatar-circle");
                    assertEquals(javafx.scene.paint.Color.web(a.color()), avatar.getFill(), "Use the supplied authoritative user color in either theme");
                    var name = (javafx.scene.control.Label)card.lookup("#user-card-name-label");
                    assertEquals(a.name(), name.getText()); assertTrue(name.isWrapText());
                    assertNotEquals(row.getBackground(), selector.lookupAll(".user-selector-choice-row").stream()
                            .map(CheckBox.class::cast).filter(c -> !c.isSelected()).findFirst().orElseThrow().getBackground(),
                            "Selection paint must differ independently of the assigned color");
                    assertTrue(row.lookupAll(".button").isEmpty(),"Rows must not contain nested action buttons");
                    String preview = System.getProperty("shale.preview.dir");
                    if (preview != null) {
                        var snap = root.snapshot(null,null); var png = new java.awt.image.BufferedImage((int)snap.getWidth(),(int)snap.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        for(int y=0;y<png.getHeight();y++) for(int x=0;x<png.getWidth();x++) png.setRGB(x,y,snap.getPixelReader().getArgb(x,y));
                        java.nio.file.Files.createDirectories(java.nio.file.Path.of(preview));
                        javax.imageio.ImageIO.write(png,"png",java.nio.file.Path.of(preview,"author-picker-"+theme+".png").toFile());
                    }
                }
            } finally { manager.unregister(scene); }
        });
    }
    @Test void sharedMiniCardsKeepUnicodeInitialsInvalidColorFallbackAndProgrammaticSelection() {
        JavaFxTestSupport.runAndWait(() -> {
            var selector = selector();
            var a = new User(17,"élise 李", "invalid"); var b = new User(18,"Other", "#FFFF00");
            selector.setCandidates(List.of(a,b)); selector.setSelectedUsers(List.of(a,b));
            new Scene(new StackPane(selector), 520, 400).getRoot().applyCss();
            var rows = selector.lookupAll(".user-selector-choice-row").stream().map(CheckBox.class::cast).toList();
            assertTrue(rows.stream().allMatch(CheckBox::isSelected), "Applied selections must restore every checked state");
            var card = (UserCard)rows.stream().filter(r -> r.getUserData().equals(17)).findFirst().orElseThrow().getGraphic();
            assertEquals("É李", ((javafx.scene.control.Label)card.lookup("#user-card-avatar-initials")).getText());
            assertEquals("", card.lookup(".user-card-avatar-circle").getStyle(), "Invalid stored color must use themed neutral fallback");
            selector.setSelectedUsers(List.of(b));
            assertFalse(rows.stream().filter(r -> r.getUserData().equals(17)).findFirst().orElseThrow().isSelected());
            selector.setDisable(true);
            assertTrue(selector.lookupAll(".user-selector-choice-row").stream().allMatch(javafx.scene.Node::isDisabled));
        });
    }

    private static UserSelector<User> selector() {
        var selector = new UserSelector<User>(User::id,User::name,User::color);
        selector.useSemanticFormControl(); selector.useMultipleSelection(); return selector;
    }
}
