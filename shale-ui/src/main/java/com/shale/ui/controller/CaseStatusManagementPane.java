package com.shale.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.shale.core.dto.CaseStatusDto;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.CommittedChangeTracker;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Case Status-specific UI over the existing legacy status service contract. */
final class CaseStatusManagementPane {
    private final CaseServicePort service;
    private final int tenantId;
    private final int actorUserId;
    private final Executor worker;
    private final CommittedChangeTracker changes;
    private final AtomicBoolean mutating = new AtomicBoolean();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicInteger generation = new AtomicInteger();
    private final VBox list = new VBox(10);
    private final Label status = new Label();
    private final Button add = button("Add", ControlStyles.Purpose.PRIMARY);
    private final Button refresh = button("Refresh", ControlStyles.Purpose.SECONDARY);
    private final VBox root = new VBox(12);
    private List<CaseStatusDto> rows = List.of();

    CaseStatusManagementPane(CaseServicePort service, int tenantId, int actorUserId, Executor worker,
            CommittedChangeTracker changes) {
        this.service = Objects.requireNonNull(service);
        this.tenantId = tenantId;
        this.actorUserId = actorUserId;
        this.worker = Objects.requireNonNull(worker);
        this.changes = Objects.requireNonNull(changes);
        root.setId("case-status-management-pane");
        list.setId("case-status-management-list");
        status.setId("case-status-management-status");
        status.getStyleClass().add("search-summary-text");
        status.setWrapText(true);
        add.setId("case-status-management-add");
        refresh.setId("case-status-management-refresh");
        add.setOnAction(e -> edit(null));
        refresh.setOnAction(e -> load());
        root.getChildren().setAll(new FlowPane(Orientation.HORIZONTAL, 8, 8, add, refresh), status, list);
        load();
    }

    Node node() { return root; }
    boolean mutationInFlight() { return mutating.get(); }
    void dispose() { disposed.set(true); generation.incrementAndGet(); }

    private void load() {
        int request = generation.incrementAndGet();
        setBusy(true);
        status.setText("Loading case statuses…");
        worker.execute(() -> {
            try {
                requireWorkerThread();
                List<CaseStatusDto> effective = List.copyOf(service.listCaseStatuses(tenantId, true));
                List<CaseStatusDto> tenant = List.copyOf(service.listTenantCaseStatuses(tenantId, true));
                Platform.runLater(() -> apply(request, effective, tenant, null));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> apply(request, null, null, ex));
            }
        });
    }

    private void apply(int request, List<CaseStatusDto> effective, List<CaseStatusDto> tenant, RuntimeException failure) {
        if (disposed.get() || request != generation.get()) return;
        setBusy(false);
        if (failure != null) {
            status.setText("Case Statuses could not be loaded. Choose Refresh to try again.");
            return;
        }
        ArrayList<CaseStatusDto> merged = new ArrayList<>(effective);
        tenant.stream().filter(t -> merged.stream().noneMatch(e -> e.id() == t.id())).forEach(merged::add);
        rows = List.copyOf(merged);
        status.setText("");
        list.getChildren().setAll(java.util.stream.IntStream.range(0, rows.size()).mapToObj(this::card).toList());
        if (rows.isEmpty()) list.getChildren().setAll(new Label("No Case Statuses are configured."));
    }

    private Node card(int index) {
        CaseStatusDto row = rows.get(index);
        Label name = new Label(row.name());
        name.getStyleClass().add("app-dialog-field-label");
        Region swatch = new Region(); swatch.setMinSize(12, 12); swatch.setPrefSize(12, 12);
        String css = ColorUtil.toCssBackgroundColorOrNull(row.color());
        if (css != null) swatch.setStyle("-fx-background-color: " + css + "; -fx-background-radius: 6;");
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label metadata = new Label((row.shaleClientId() == null ? "Global/default" : "Tenant")
                + " • " + (row.closed() ? "Closed workflow state" : "Open workflow state")
                + " • Order " + (row.sortOrder() == null ? "—" : row.sortOrder())
                + (blank(row.lifecycleKey()) ? "" : " • Lifecycle: " + row.lifecycleKey())
                + (blank(row.systemKey()) ? "" : " • System: " + row.systemKey())
                + (row.active() && !row.deleted() ? " • Active" : " • Removed"));
        metadata.getStyleClass().add("search-summary-text"); metadata.setWrapText(true);
        FlowPane actions = new FlowPane(Orientation.HORIZONTAL, 6, 6);
        if (!row.deleted()) {
            Button edit = button(row.shaleClientId() == null ? "Customize" : "Edit", ControlStyles.Purpose.SECONDARY);
            edit.setOnAction(e -> edit(row)); actions.getChildren().add(edit);
        }
        boolean tenantRow = row.shaleClientId() != null && row.shaleClientId() == tenantId;
        Button up = button("Move Up", ControlStyles.Purpose.GHOST);
        Button down = button("Move Down", ControlStyles.Purpose.GHOST);
        up.setDisable(!canSwap(index, index - 1)); down.setDisable(!canSwap(index, index + 1));
        up.setOnAction(e -> reorder(index, index - 1)); down.setOnAction(e -> reorder(index, index + 1));
        if (!row.deleted()) actions.getChildren().addAll(up, down);
        if (tenantRow) {
            Button lifecycle = button(row.deleted() ? "Restore" : "Remove", row.deleted() ? ControlStyles.Purpose.SECONDARY : ControlStyles.Purpose.DANGER);
            lifecycle.setOnAction(e -> lifecycle(row)); actions.getChildren().add(lifecycle);
        }
        VBox card = new VBox(7, new HBox(8, swatch, name, spacer), metadata, actions);
        card.getStyleClass().addAll("shale-card-surface", "shale-density-card-compact");
        return card;
    }

    private boolean canSwap(int from, int to) {
        return from >= 0 && to >= 0 && from < rows.size() && to < rows.size()
                && owned(rows.get(from)) && owned(rows.get(to)) && !rows.get(from).deleted() && !rows.get(to).deleted();
    }

    private boolean owned(CaseStatusDto row) { return row.shaleClientId() != null && row.shaleClientId() == tenantId; }

    private void edit(CaseStatusDto existing) {
        if (mutating.get()) return;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Case Status" : existing.shaleClientId() == null ? "Customize Case Status" : "Edit Case Status");
        if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        TextField name = new TextField(existing == null ? "" : existing.name());
        CheckBox closed = new CheckBox("Closed workflow state"); closed.setSelected(existing != null && existing.closed());
        ColorPicker color = new ColorPicker(color(existing == null ? null : existing.color()));
        ControlStyles.formControl(name); ControlStyles.formControl(color); ControlStyles.formControl(closed);
        VBox body = new VBox(8, new Label("Name"), name, closed, new Label("Color"), color);
        if (existing != null && !blank(existing.lifecycleKey())) body.getChildren().add(new Label("Lifecycle key: " + existing.lifecycleKey() + " (read-only)"));
        if (existing != null && !blank(existing.systemKey())) body.getChildren().add(new Label("System key: " + existing.systemKey() + " (read-only)"));
        dialog.getDialogPane().setContent(body);
        Button save = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ControlStyles.apply(save, ControlStyles.Purpose.PRIMARY);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, e -> { if (name.getText() == null || name.getText().trim().isBlank()) { e.consume(); status.setText("Name is required."); } });
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        CaseServicePort.CaseStatusCommand command = new CaseServicePort.CaseStatusCommand(existing == null ? null : existing.id(), tenantId,
                name.getText().trim(), closed.isSelected(), existing == null ? null : existing.sortOrder(),
                "#" + ColorUtil.toStoredColor(color.getValue()).substring(0, 6), existing == null ? null : existing.lifecycleKey(), existing == null ? null : existing.systemKey());
        mutate(() -> { if (existing == null) service.createCaseStatus(command); else service.updateCaseStatus(command); });
    }

    private void lifecycle(CaseStatusDto row) {
        if (!row.deleted() && !AppDialogs.showConfirmation(root.getScene() == null ? null : root.getScene().getWindow(), "Case Statuses",
                "Remove “" + row.name() + "”?", "It will no longer be selectable. Existing Cases and history keep this status.",
                "Remove", AppDialogs.DialogActionKind.DANGER)) return;
        mutate(() -> { var command = new CaseServicePort.StatusLifecycleCommand(tenantId, actorUserId, row.id());
            if (row.deleted()) service.restoreCaseStatus(command); else service.removeCaseStatus(command); });
    }

    private void reorder(int from, int to) {
        if (!canSwap(from, to)) return;
        int first = rows.get(from).id(), second = rows.get(to).id();
        mutate(() -> service.reorderCaseStatuses(tenantId, first, second));
    }

    private void mutate(Runnable operation) {
        if (!mutating.compareAndSet(false, true)) return;
        setBusy(true); status.setText("Saving…");
        worker.execute(() -> {
            try {
                requireWorkerThread(); operation.run();
                Platform.runLater(() -> { if (disposed.get()) return; mutating.set(false); changes.markCommitted(); load(); });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> { if (disposed.get()) return; mutating.set(false); setBusy(false); status.setText("Change failed. The authoritative order and values will be reloaded."); load(); });
            }
        });
    }

    private void setBusy(boolean busy) { add.setDisable(busy); refresh.setDisable(busy); list.setDisable(busy); }
    private static void requireWorkerThread() { if (Platform.isFxApplicationThread()) throw new IllegalStateException("Case Status service work must run off the JavaFX thread."); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static Color color(String value) { try { return blank(value) ? Color.web("#6C757D") : Color.web(value); } catch (IllegalArgumentException ex) { return Color.web("#6C757D"); } }
    private static Button button(String text, ControlStyles.Purpose purpose) { Button button = new Button(text); ControlStyles.apply(button, purpose, ControlStyles.Size.SMALL); return button; }
}
