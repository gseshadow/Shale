package com.shale.ui.component.dialog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.services.CaseTaskService;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

final class AssignedUserPickerDialog {

    private static final double PICKER_PREF_WIDTH = 440;
    private static final double PICKER_INITIAL_HEIGHT = 700;
    private static final double PICKER_MIN_HEIGHT = 420;
    private static final double PICKER_MAX_HEIGHT = 760;
    private static final double PICKER_OWNER_HEIGHT_RATIO = 0.85;
    private static final double LIST_MIN_VIEWPORT_HEIGHT = 260;

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
        list.setPrefHeight(Region.USE_COMPUTED_SIZE);
        list.setMaxHeight(Region.USE_PREF_SIZE);
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
        listScrollPane.setPannable(true);
        listScrollPane.getStyleClass().add("transparent-scroll");
        VBox.setVgrow(listScrollPane, Priority.ALWAYS);

        Button closeButton = new Button("Close");
        closeButton.getStyleClass().addAll("app-dialog-button", "app-dialog-button-secondary");
        closeButton.setOnAction(e -> stage.close());
        Region closeSpacer = new Region();
        HBox.setHgrow(closeSpacer, Priority.ALWAYS);
        HBox closeRow = new HBox(8, closeSpacer, closeButton);
        closeRow.setAlignment(Pos.CENTER_RIGHT);
        closeRow.setPadding(new Insets(8, 0, 0, 0));

        VBox topContent = new VBox(heading);
        topContent.setFillWidth(true);
        topContent.setPadding(new Insets(0, 0, 6, 0));

        double targetHeight = resolveTargetHeight(owner);
        double targetViewportHeight = Math.max(LIST_MIN_VIEWPORT_HEIGHT, targetHeight - 140);
        listScrollPane.setPrefViewportHeight(targetViewportHeight);
        listScrollPane.setMinViewportHeight(LIST_MIN_VIEWPORT_HEIGHT);
        listScrollPane.setPrefHeight(targetViewportHeight);
        listScrollPane.setMinHeight(LIST_MIN_VIEWPORT_HEIGHT);
        listScrollPane.setMaxHeight(Double.MAX_VALUE);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-dialog-root");
        root.setPadding(new Insets(18));
        root.setTop(topContent);
        root.setCenter(listScrollPane);
        root.setBottom(closeRow);
        BorderPane.setMargin(listScrollPane, new Insets(12, 0, 12, 0));

        root.setPrefSize(PICKER_PREF_WIDTH, PICKER_INITIAL_HEIGHT);
        root.setMinSize(PICKER_PREF_WIDTH, PICKER_MIN_HEIGHT);
        root.setMaxSize(PICKER_PREF_WIDTH, PICKER_MAX_HEIGHT);

        Scene scene = new Scene(root, PICKER_PREF_WIDTH, targetHeight);
        scene.getStylesheets().add(Objects.requireNonNull(cssAnchor.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setMinHeight(PICKER_MIN_HEIGHT);
        stage.setHeight(targetHeight);
        stage.setMaxHeight(PICKER_MAX_HEIGHT);
        stage.setMinWidth(PICKER_PREF_WIDTH);
        stage.setWidth(PICKER_PREF_WIDTH);
        stage.setMaxWidth(PICKER_PREF_WIDTH);
        stage.showAndWait();
        return Optional.ofNullable(holder.value);
    }

    private static double resolveTargetHeight(Window owner) {
        double ownerHeight = owner == null ? 0 : owner.getHeight();
        if (ownerHeight <= 0) {
            return PICKER_INITIAL_HEIGHT;
        }
        double preferredFromOwner = ownerHeight * PICKER_OWNER_HEIGHT_RATIO;
        return Math.min(PICKER_MAX_HEIGHT, Math.max(PICKER_MIN_HEIGHT, preferredFromOwner));
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static final class ResultHolderAssignable {
        private CaseTaskService.AssignableUserOption value;
    }
}
