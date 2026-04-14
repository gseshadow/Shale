package com.shale.ui.component.dialog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.services.CaseTaskService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

final class AssignedUserPickerDialog {

    private static final double PICKER_MIN_WIDTH = 420;
    private static final double PICKER_PREF_WIDTH = 440;
    private static final double PICKER_MAX_WIDTH = 460;
    private static final double PICKER_MIN_HEIGHT = 420;
    private static final double PICKER_PREF_HEIGHT_RATIO = 0.85;
    private static final double PICKER_MAX_HEIGHT_RATIO = 0.90;
    private static final double SCREEN_MARGIN = 40;

    private AssignedUserPickerDialog() {
    }

    static Optional<CaseTaskService.AssignableUserOption> show(
            Window owner,
            List<CaseTaskService.AssignableUserOption> candidates,
            Class<?> cssAnchor) {
        Stage stage = AppDialogs.createModalStage(owner, "Add Assigned User");
        Label heading = new Label("Add to assigned");
        heading.getStyleClass().add("app-dialog-title");

        VBox list = new VBox(8);
        list.setFillWidth(true);
        UserCardFactory cardFactory = new UserCardFactory(id -> {
        });
        List<CaseTaskService.AssignableUserOption> safeCandidates = candidates == null ? List.of() : candidates;
        ResultHolderAssignable holder = new ResultHolderAssignable();
        if (safeCandidates.isEmpty()) {
            Label emptyLabel = new Label("No additional users available");
            emptyLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: rgba(17,37,66,0.70);");
            list.getChildren().add(emptyLabel);
        } else {
            for (CaseTaskService.AssignableUserOption user : safeCandidates) {
                if (user == null || user.id() <= 0) {
                    continue;
                }
                var card = cardFactory.create(
                        new UserCardModel(user.id(), safe(user.displayName()), user.color(), null),
                        UserCardFactory.Variant.MINI);
                Button cardButton = new Button();
                cardButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
                cardButton.setMaxWidth(Double.MAX_VALUE);
                cardButton.setGraphic(card);
                cardButton.setOnAction(e -> {
                    holder.value = user;
                    stage.close();
                });
                list.getChildren().add(cardButton);
            }
        }

        ScrollPane listScrollPane = new ScrollPane(list);
        listScrollPane.setFitToWidth(true);
        listScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        listScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        listScrollPane.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(listScrollPane, Priority.ALWAYS);

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        closeButton.setOnAction(e -> stage.close());
        Region closeSpacer = new Region();
        HBox.setHgrow(closeSpacer, Priority.ALWAYS);
        HBox closeRow = new HBox(8, closeSpacer, closeButton);
        closeRow.setAlignment(Pos.CENTER_RIGHT);

        VBox topContent = new VBox(heading);
        topContent.setFillWidth(true);

        VBox root = new VBox(12, topContent, listScrollPane, closeRow);
        root.getStyleClass().add("app-dialog-root");
        root.setPadding(new Insets(18));
        root.setMinWidth(PICKER_MIN_WIDTH);
        root.setPrefWidth(PICKER_PREF_WIDTH);
        root.setMaxWidth(PICKER_MAX_WIDTH);

        PickerSize pickerSize = resolveSize(owner);
        root.setMinHeight(pickerSize.minHeight());
        root.setPrefHeight(pickerSize.prefHeight());
        root.setMaxHeight(pickerSize.maxHeight());
        listScrollPane.setMinHeight(Math.max(220, pickerSize.minHeight() - 130));
        listScrollPane.setPrefHeight(Math.max(220, pickerSize.prefHeight() - 130));
        listScrollPane.setMaxHeight(Double.MAX_VALUE);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(cssAnchor.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.setMinHeight(pickerSize.minHeight());
        stage.setHeight(pickerSize.prefHeight());
        stage.setMaxHeight(pickerSize.maxHeight());
        stage.setMinWidth(PICKER_MIN_WIDTH);
        stage.setWidth(PICKER_PREF_WIDTH);
        stage.setMaxWidth(PICKER_MAX_WIDTH);
        stage.showAndWait();
        return Optional.ofNullable(holder.value);
    }

    private static PickerSize resolveSize(Window owner) {
        double ownerHeight = owner == null ? 0 : owner.getHeight();
        double preferred = ownerHeight > 0 ? ownerHeight * PICKER_PREF_HEIGHT_RATIO : 560;
        double ownerMax = ownerHeight > 0 ? ownerHeight * PICKER_MAX_HEIGHT_RATIO : 620;
        double screenMax = maxHeightForScreen(owner);

        double maxHeight = Math.max(PICKER_MIN_HEIGHT, Math.min(ownerMax, screenMax));
        double prefHeight = Math.max(PICKER_MIN_HEIGHT, Math.min(preferred, maxHeight));
        double minHeight = Math.min(PICKER_MIN_HEIGHT, maxHeight);
        return new PickerSize(minHeight, prefHeight, maxHeight);
    }

    private static double maxHeightForScreen(Window owner) {
        Screen screen = resolveScreen(owner);
        Rectangle2D bounds = screen == null ? Screen.getPrimary().getVisualBounds() : screen.getVisualBounds();
        return Math.max(PICKER_MIN_HEIGHT, bounds.getHeight() - SCREEN_MARGIN);
    }

    private static Screen resolveScreen(Window owner) {
        if (owner == null) {
            return Screen.getPrimary();
        }
        List<Screen> screens = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight());
        if (screens != null && !screens.isEmpty()) {
            return screens.get(0);
        }
        return Screen.getPrimary();
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static final class ResultHolderAssignable {
        private CaseTaskService.AssignableUserOption value;
    }

    private record PickerSize(double minHeight, double prefHeight, double maxHeight) {
    }
}
