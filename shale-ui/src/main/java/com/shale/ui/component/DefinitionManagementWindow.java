package com.shale.ui.component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Presentation-only shell shared by independently composed definition managers. */
public final class DefinitionManagementWindow {
    public enum ContentMode {
        SCROLLABLE,
        FIXED
    }

    private DefinitionManagementWindow() {}

    public static void show(Window owner, String title, String helpText, Node content,
            BooleanSupplier changed, BooleanSupplier canClose, Runnable dispose, Consumer<DefinitionManagementResult> onClosed) {
        show(owner, title, helpText, content, ContentMode.SCROLLABLE, changed, canClose, dispose, onClosed);
    }

    public static void show(Window owner, String title, String helpText, Node content, ContentMode contentMode,
            BooleanSupplier changed, BooleanSupplier canClose, Runnable dispose, Consumer<DefinitionManagementResult> onClosed) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(contentMode, "contentMode");
        Objects.requireNonNull(changed, "changed");
        Objects.requireNonNull(canClose, "canClose");
        Objects.requireNonNull(dispose, "dispose");
        Objects.requireNonNull(onClosed, "onClosed");
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        AppDialogs.applySecondaryDialogShell(dialog, title);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label help = new Label(helpText);
        help.getStyleClass().addAll("search-summary-text", "management-window-help");
        help.setWrapText(true);
        VBox header = new VBox(help);
        header.getStyleClass().add("management-window-header");

        Node body = contentMode == ContentMode.SCROLLABLE ? scrollable(content) : content;
        BorderPane.setMargin(body, new Insets(0, 16, 0, 16));

        Button done = ActionButtonFactory.semantic("Done", e -> { if (canClose.getAsBoolean()) dialog.close(); }, ControlStyles.Purpose.PRIMARY, ControlStyles.Size.STANDARD);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, spacer, done);
        footer.getStyleClass().add("management-window-footer");
        BorderPane root = new BorderPane(body, header, null, footer, null);
        root.getStyleClass().add("management-window-content");
        content.getStyleClass().add("management-manager-root");
        dialog.getDialogPane().getStyleClass().add("management-window");
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setPrefSize(900, 700);
        dialog.getDialogPane().setMinSize(680, 520);
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setVisible(false);
        dialog.getDialogPane().lookupButton(ButtonType.CLOSE).setManaged(false);

        AtomicBoolean completed = new AtomicBoolean();
        dialog.setOnCloseRequest(e -> { if (!canClose.getAsBoolean()) e.consume(); });
        dialog.setOnHidden(e -> {
            if (!completed.compareAndSet(false, true)) return;
            dispose.run();
            onClosed.accept(new DefinitionManagementResult(changed.getAsBoolean()));
            if (owner != null && owner.isShowing()) owner.requestFocus();
        });
        dialog.setOnShown(e -> {
            Window window = dialog.getDialogPane().getScene().getWindow();
            if (window instanceof Stage stage) {
                com.shale.ui.util.WindowSizingUtil.sizeModalStage(stage, owner, 900, 700, 680, 520);
            }
        });
        dialog.show();
        dialog.getDialogPane().requestFocus();
    }

    private static ScrollPane scrollable(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("transparent-scroll", "management-window-scroll");
        return scroll;
    }
}
