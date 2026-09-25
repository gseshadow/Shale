package com.shale.ui.controller;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.service.UserServicePort;
import com.shale.core.service.UserServicePort.FirmWideRoleDefinition;
import com.shale.ui.component.CommittedChangeTracker;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Firm-wide-role definition editor. Assignment membership remains in user editors. */
public final class FirmWideRoleAdminPane {
    private static final Logger LOG = LoggerFactory.getLogger(FirmWideRoleAdminPane.class);
    private final UserServicePort service;
    private final int tenantId;
    private final int actorUserId;
    private final Executor executor;
    private final CommittedChangeTracker changes;
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicBoolean mutation = new AtomicBoolean();
    private final VBox root = new VBox(12);
    private final VBox roles = new VBox(8);
    private final Label feedback = new Label();
    private final Button add = ActionButtonFactory.semantic("Add Role", event -> createRole(), ControlStyles.Purpose.PRIMARY,
            ControlStyles.Size.STANDARD);
    private final Button refresh = ActionButtonFactory.semantic("Refresh", event -> load(null), ControlStyles.Purpose.GHOST,
            ControlStyles.Size.STANDARD);
    private int generation;

    FirmWideRoleAdminPane(UserServicePort service, int tenantId, int actorUserId, Executor executor,
            CommittedChangeTracker changes) {
        this.service = Objects.requireNonNull(service, "service");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.changes = Objects.requireNonNull(changes, "changes");
        if (tenantId <= 0 || actorUserId <= 0) throw new IllegalArgumentException("Tenant and actor context are required.");
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        Label explanation = new Label("Administrator and Attorney are protected built-ins. Their membership comes from the user flags in User Management; tenant-defined roles control separate firm-wide eligibility.");
        explanation.setWrapText(true);
        explanation.getStyleClass().add("management-window-help");
        HBox toolbar = new HBox(8, add, refresh);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        feedback.setWrapText(true);
        feedback.getStyleClass().add("management-result-count");
        root.getStyleClass().add("management-window-content");
        root.getChildren().setAll(explanation, toolbar, roles, feedback);
        load(null);
    }

    Node node() { return root; }
    boolean mutationInFlight() { return mutation.get(); }
    void dispose() { disposed.set(true); generation++; }

    private void load(String success) {
        if (disposed.get()) return;
        int request = ++generation;
        setBusy(true);
        feedback.setText("Loading firm-wide roles…");
        executor.execute(() -> {
            try {
                List<FirmWideRoleDefinition> loaded = service.listFirmWideRolesForAdministration(tenantId, actorUserId);
                Platform.runLater(() -> {
                    if (disposed.get() || request != generation) return;
                    render(loaded == null ? List.of() : List.copyOf(loaded));
                    setBusy(false);
                    feedback.setText(success != null ? success : loaded == null || loaded.isEmpty()
                            ? "No firm-wide role definitions were found." : "");
                });
            } catch (RuntimeException failure) {
                LOG.warn("Firm-wide role definitions could not be loaded tenantId={} actorId={}", tenantId, actorUserId);
                Platform.runLater(() -> {
                    if (disposed.get() || request != generation) return;
                    roles.getChildren().setAll(stateLabel("Firm-wide roles could not be loaded. Select Refresh to try again."));
                    setBusy(false);
                    feedback.setText("Firm-wide roles could not be loaded.");
                });
            }
        });
    }

    private void render(List<FirmWideRoleDefinition> definitions) {
        roles.getChildren().clear();
        if (definitions.isEmpty()) {
            roles.getChildren().add(stateLabel("No role definitions are available."));
            return;
        }
        definitions.forEach(definition -> roles.getChildren().add(card(definition)));
    }

    private Node card(FirmWideRoleDefinition definition) {
        Label name = new Label(definition.name());
        name.getStyleClass().add("shale-subsection-title");
        Label status = new Label((definition.builtIn() ? "Protected · " : "")
                + (definition.deleted() ? "Deleted · Inactive" : definition.active() ? "Active" : "Inactive"));
        status.getStyleClass().add("management-result-count");
        VBox identity = new VBox(3, name, status);
        HBox.setHgrow(identity, Priority.ALWAYS);
        HBox actions = new HBox(6);
        actions.setAlignment(Pos.CENTER_RIGHT);
        if (definition.builtIn()) {
            Label protectedText = new Label("Managed by user flags");
            protectedText.getStyleClass().add("management-result-count");
            actions.getChildren().add(protectedText);
        } else if (!definition.deleted()) {
            actions.getChildren().add(ActionButtonFactory.semantic("Rename", event -> rename(definition),
                    ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL));
            String lifecycle = definition.active() ? "Deactivate" : "Activate";
            actions.getChildren().add(ActionButtonFactory.semantic(lifecycle, event -> setActive(definition, !definition.active()),
                    ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL));
            actions.getChildren().add(ActionButtonFactory.semantic("Delete", event -> delete(definition),
                    ControlStyles.Purpose.DANGER, ControlStyles.Size.SMALL));
        }
        HBox card = new HBox(12, identity, actions);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("shale-card-surface");
        return card;
    }

    private void createRole() {
        editName("Add Firm-wide Role", "Create a tenant-defined firm-wide role", "Create", "")
                .ifPresent(name -> mutate("Creating role…", "Firm-wide role created.",
                        () -> service.createFirmWideRole(new UserServicePort.CreateFirmWideRoleCommand(tenantId, actorUserId, name))));
    }

    private void rename(FirmWideRoleDefinition definition) {
        editName("Rename Firm-wide Role", "Rename " + definition.name(), "Save", definition.name())
                .ifPresent(name -> mutate("Renaming role…", "Firm-wide role renamed.",
                        () -> service.renameFirmWideRole(new UserServicePort.RenameFirmWideRoleCommand(
                                tenantId, actorUserId, definition.id(), name, definition.rowVer()))));
    }

    private void setActive(FirmWideRoleDefinition definition, boolean active) {
        String verb = active ? "Activate" : "Deactivate";
        String effect = active
                ? "This role will become eligible for new and existing active assignments. Assignment history is unchanged."
                : "Existing assignment history will be preserved, but this role will stop granting eligibility until reactivated.";
        if (!AppDialogs.showConfirmation(null, verb + " Firm-wide Role", verb + " " + definition.name() + "?", effect,
                verb, active ? AppDialogs.DialogActionKind.PRIMARY : AppDialogs.DialogActionKind.DANGER)) return;
        mutate(active ? "Activating role…" : "Deactivating role…", active ? "Firm-wide role activated." : "Firm-wide role deactivated.",
                () -> service.setFirmWideRoleActive(new UserServicePort.FirmWideRoleLifecycleCommand(
                        tenantId, actorUserId, definition.id(), definition.rowVer()), active));
    }

    private void delete(FirmWideRoleDefinition definition) {
        if (!AppDialogs.showConfirmation(null, "Delete Firm-wide Role", "Delete " + definition.name() + "?",
                "The definition will be soft-deleted and will no longer grant eligibility. Existing assignment history will be preserved.",
                "Delete", AppDialogs.DialogActionKind.DANGER)) return;
        mutate("Deleting role…", "Firm-wide role deleted.", () -> service.deleteFirmWideRole(
                new UserServicePort.FirmWideRoleLifecycleCommand(tenantId, actorUserId, definition.id(), definition.rowVer())));
    }

    private Optional<String> editName(String title, String heading, String action, String initial) {
        Dialog<String> dialog = new Dialog<>();
        AppDialogs.applySecondaryDialogShell(dialog, title);
        dialog.setTitle(title);
        dialog.setHeaderText(heading);
        ButtonType save = new ButtonType(action, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(save, ButtonType.CANCEL);
        TextField name = ControlStyles.formControl(new TextField(initial));
        name.setPromptText("Role name");
        dialog.getDialogPane().setContent(name);
        ControlStyles.apply((Button) dialog.getDialogPane().lookupButton(save), ControlStyles.Purpose.PRIMARY);
        ControlStyles.apply((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL), ControlStyles.Purpose.SECONDARY);
        Node saveButton = dialog.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            boolean invalid = name.getText() == null || name.getText().trim().isEmpty() || name.getText().trim().length() > 100;
            ControlStyles.setInvalid(name, invalid);
            if (invalid) event.consume();
        });
        dialog.setResultConverter(button -> button == save ? name.getText().trim() : null);
        Platform.runLater(name::requestFocus);
        return dialog.showAndWait();
    }

    private void mutate(String busy, String success, Runnable operation) {
        if (!mutation.compareAndSet(false, true) || disposed.get()) return;
        setBusy(true);
        feedback.setText(busy);
        executor.execute(() -> {
            try {
                operation.run();
                Platform.runLater(() -> {
                    if (disposed.get()) return;
                    mutation.set(false);
                    changes.markCommitted();
                    load(success);
                });
            } catch (RuntimeException failure) {
                LOG.warn("Firm-wide role mutation failed tenantId={} actorId={}", tenantId, actorUserId);
                Platform.runLater(() -> {
                    if (disposed.get()) return;
                    mutation.set(false);
                    setBusy(false);
                    feedback.setText(userMessage(failure));
                });
            }
        });
    }

    private void setBusy(boolean busy) { add.setDisable(busy); refresh.setDisable(busy); roles.setDisable(busy); }
    private static Label stateLabel(String text) { Label label = new Label(text); label.setWrapText(true); return label; }
    private static String userMessage(RuntimeException failure) {
        if (failure instanceof IllegalArgumentException || failure instanceof IllegalStateException || failure instanceof SecurityException) {
            String message = failure.getMessage();
            if (message != null && !message.isBlank()) return message;
        }
        return "The firm-wide role change could not be completed. Refresh and try again.";
    }
}
