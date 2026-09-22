package com.shale.ui.component;

import com.shale.ui.theme.ThemeManager;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.geometry.Bounds;
import javafx.geometry.Rectangle2D;

/** Narrow lifecycle bridge for Shale-owned popup scenes, which do not inherit owner Scene stylesheets. */
public final class TransientPopupSupport {
    static final String CONTEXT_MENU_CLASS = "shale-context-menu";

    private TransientPopupSupport() {}

    public static void style(ContextMenu menu, String... additionalClasses) {
        addOnce(menu.getStyleClass(), CONTEXT_MENU_CLASS);
        for (String styleClass : additionalClasses) addOnce(menu.getStyleClass(), styleClass);
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN, event -> register(menu.getScene().getRoot()));
        menu.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, event -> unregister(menu.getScene().getRoot()));
    }

    static void register(Parent root) {
        if (root != null) ThemeManager.application().register(root);
    }

    static void unregister(Parent root) {
        if (root != null) ThemeManager.application().unregister(root);
    }

    static PopupPosition position(Bounds anchor, double width, double height, Rectangle2D screen, double gap) {
        double x = anchor.getMaxX() + gap;
        if (x + width > screen.getMaxX()) x = anchor.getMinX() - gap - width;
        double y = anchor.getMinY();
        if (y + height > screen.getMaxY()) y = anchor.getMaxY() - height;
        x = Math.max(screen.getMinX() + gap, Math.min(x, screen.getMaxX() - width - gap));
        y = Math.max(screen.getMinY() + gap, Math.min(y, screen.getMaxY() - height - gap));
        return new PopupPosition(x, y);
    }

    record PopupPosition(double x, double y) {}

    private static void addOnce(javafx.collections.ObservableList<String> classes, String styleClass) {
        if (styleClass != null && !styleClass.isBlank() && !classes.contains(styleClass)) classes.add(styleClass);
    }
}
