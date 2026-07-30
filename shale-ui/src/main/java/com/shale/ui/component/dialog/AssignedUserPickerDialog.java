package com.shale.ui.component.dialog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.ui.component.UserSelector;
import com.shale.ui.services.CaseTaskService;
import com.shale.ui.util.ControlStyles;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class AssignedUserPickerDialog {

    private static final double PICKER_PREF_WIDTH = 440;
    private static final double PICKER_INITIAL_HEIGHT = 700;
    private static final double PICKER_MIN_HEIGHT = 420;
    private static final double PICKER_MAX_HEIGHT = 760;
    private static final double PICKER_OWNER_HEIGHT_RATIO = 0.85;

    private AssignedUserPickerDialog() {
    }

    public static Optional<CaseTaskService.AssignableUserOption> show(
            Window owner,
            List<CaseTaskService.AssignableUserOption> candidates,
            Class<?> cssAnchor) {
        Stage stage = AppDialogs.createModalStage(owner, "Add Assigned User");
        Label heading = new Label("Add to assigned");
        heading.getStyleClass().add("app-dialog-title");

        UserSelector<CaseTaskService.AssignableUserOption> selector = new UserSelector<>(
                CaseTaskService.AssignableUserOption::id,
                CaseTaskService.AssignableUserOption::displayName,
                CaseTaskService.AssignableUserOption::color);
        selector.setPromptText("Search users...");
        selector.setEmptyText("No additional users available");
        selector.setCandidates(candidates == null ? List.of() : candidates);
        selector.useSemanticFormControl();
        ResultHolderAssignable holder = new ResultHolderAssignable();
        selector.selectedUserProperty().addListener((obs, oldValue, selectedUser) -> {
            if (selectedUser != null) {
                holder.value = selectedUser;
                stage.close();
            }
        });

        Button closeButton = new Button("Close");
        ControlStyles.apply(closeButton, ControlStyles.Purpose.SECONDARY);
        closeButton.setCancelButton(true);
        closeButton.setOnAction(e -> stage.close());
        Region closeSpacer = new Region();
        HBox.setHgrow(closeSpacer, Priority.ALWAYS);
        HBox closeRow = new HBox(8, closeSpacer, closeButton);
        closeRow.setAlignment(Pos.CENTER_RIGHT);
        closeRow.setPadding(new Insets(8, 0, 0, 0));

        VBox topContent = new VBox(8, heading);
        topContent.setFillWidth(true);
        topContent.setPadding(new Insets(0, 0, 6, 0));

        double targetHeight = resolveTargetHeight(owner);
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-dialog-root");
        root.setPadding(new Insets(18));
        root.setTop(topContent);
        root.setCenter(selector);
        root.setBottom(closeRow);
        BorderPane.setMargin(selector, new Insets(12, 0, 12, 0));

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

    private static final class ResultHolderAssignable {
        private CaseTaskService.AssignableUserOption value;
    }
}
