package com.shale.ui.component.dialog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.services.CaseTaskService;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

final class AssignedUserPickerDialog {

    private static final double PICKER_WIDTH = 420;
    private static final double PICKER_MIN_HEIGHT = 420;
    private static final double PICKER_MAX_HEIGHT = 760;
    private static final double PICKER_OWNER_HEIGHT_RATIO = 0.82;

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

        VBox root = new VBox(12, heading, listScrollPane, closeButton);
        root.getStyleClass().add("app-dialog-root");
        root.setPadding(new Insets(18));
        root.setMinWidth(PICKER_WIDTH);
        root.setPrefWidth(PICKER_WIDTH);
        root.setMaxWidth(PICKER_WIDTH);

        double targetHeight = resolvedHeight(owner);
        root.setMinHeight(targetHeight);
        root.setPrefHeight(targetHeight);
        listScrollPane.setMinHeight(Math.max(220, targetHeight - 120));
        listScrollPane.setPrefHeight(Math.max(220, targetHeight - 120));

        Scene scene = new Scene(root);
        scene.getStylesheets().add(Objects.requireNonNull(cssAnchor.getResource("/css/app.css")).toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return Optional.ofNullable(holder.value);
    }

    private static double resolvedHeight(Window owner) {
        double ownerHeight = owner == null ? 0 : owner.getHeight();
        double preferred = ownerHeight > 0 ? ownerHeight * PICKER_OWNER_HEIGHT_RATIO : 560;
        return Math.max(PICKER_MIN_HEIGHT, Math.min(PICKER_MAX_HEIGHT, preferred));
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    private static final class ResultHolderAssignable {
        private CaseTaskService.AssignableUserOption value;
    }
}
