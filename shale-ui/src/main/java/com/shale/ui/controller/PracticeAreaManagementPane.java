package com.shale.ui.controller;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.shale.core.dto.PracticeAreaDto;
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
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Practice Area-specific administration over the legacy create/update/deactivate contract. */
public final class PracticeAreaManagementPane {
    private final CaseServicePort service;
    private final int tenantId;
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

    PracticeAreaManagementPane(CaseServicePort service, int tenantId, Executor worker, CommittedChangeTracker changes) {
        this.service = Objects.requireNonNull(service);
        this.tenantId = tenantId;
        this.worker = Objects.requireNonNull(worker);
        this.changes = Objects.requireNonNull(changes);
        root.setId("practice-area-management-pane");
        list.setId("practice-area-management-list");
        status.setId("practice-area-management-status");
        status.getStyleClass().add("search-summary-text");
        status.setWrapText(true);
        add.setId("practice-area-management-add");
        refresh.setId("practice-area-management-refresh");
        add.setTooltip(new Tooltip("Create a tenant Practice Area"));
        add.setOnAction(event -> edit(null));
        refresh.setOnAction(event -> load());
        root.getChildren().setAll(new FlowPane(Orientation.HORIZONTAL, 8, 8, add, refresh), status, list);
        load();
    }

    Node node() { return root; }
    boolean mutationInFlight() { return mutating.get(); }
    void dispose() { disposed.set(true); generation.incrementAndGet(); }

    private void load() {
        int request = generation.incrementAndGet();
        setBusy(true);
        status.setText("Loading practice areas…");
        worker.execute(() -> {
            try {
                requireWorkerThread();
                List<PracticeAreaDto> effective = List.copyOf(service.listPracticeAreas(tenantId, true));
                List<PracticeAreaDto> tenant = List.copyOf(service.listTenantPracticeAreas(tenantId, true));
                Platform.runLater(() -> apply(request, effective, tenant, null));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> apply(request, null, null, ex));
            }
        });
    }

    private void apply(int request, List<PracticeAreaDto> effective, List<PracticeAreaDto> tenant, RuntimeException failure) {
        if (disposed.get() || request != generation.get()) return;
        setBusy(false);
        if (failure != null) {
            status.setText("Practice Areas could not be loaded. Choose Refresh to try again.");
            return;
        }
        status.setText("");
        List<PracticeAreaDto> rows = tenant.stream().filter(t -> effective.stream().noneMatch(e -> e.id() == t.id()))
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        rows.addAll(0, effective);
        list.getChildren().setAll(rows.stream().map(this::card).toList());
        if (rows.isEmpty()) list.getChildren().setAll(new Label("No Practice Areas are configured."));
    }

    private Node card(PracticeAreaDto row) {
        Label name = new Label(row.name());
        name.getStyleClass().add("app-dialog-field-label");
        Region swatch = new Region();
        swatch.setMinSize(12, 12); swatch.setPrefSize(12, 12);
        String css = ColorUtil.toCssBackgroundColorOrNull(row.color());
        if (css != null) swatch.setStyle("-fx-background-color: " + css + "; -fx-background-radius: 6;");
        Label metadata = new Label((row.shaleClientId() == null ? "Global/default" : "Tenant")
                + (row.active() && !row.deleted() ? " • Active" : " • Inactive")
                + (row.systemKey() == null ? "" : " • System key: " + row.systemKey()));
        metadata.getStyleClass().add("search-summary-text");
        FlowPane actions = new FlowPane(Orientation.HORIZONTAL, 6, 6);
        if (!row.deleted()) {
            Button edit = button(row.shaleClientId() == null ? "Customize" : "Edit", ControlStyles.Purpose.SECONDARY);
            edit.setOnAction(event -> edit(row)); actions.getChildren().add(edit);
            if (row.shaleClientId() != null && row.active() && (row.systemKey() == null || row.systemKey().isBlank())) {
                Button deactivate = button("Deactivate", ControlStyles.Purpose.DANGER);
                deactivate.setOnAction(event -> deactivate(row)); actions.getChildren().add(deactivate);
            }
        }
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox heading = new HBox(8, swatch, name, spacer);
        VBox card = new VBox(7, heading, metadata, actions);
        card.getStyleClass().addAll("shale-card-surface", "shale-density-card-compact");
        return card;
    }

    private void edit(PracticeAreaDto existing) {
        if (mutating.get()) return;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Practice Area" : existing.shaleClientId() == null ? "Customize Practice Area" : "Edit Practice Area");
        if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow());
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        TextField name = new TextField(existing == null ? "" : existing.name());
        ColorPicker color = new ColorPicker(color(existing == null ? null : existing.color()));
        ControlStyles.formControl(name); ControlStyles.formControl(color);
        VBox body = new VBox(8, new Label("Name"), name, new Label("Color"), color);
        if (existing != null && existing.systemKey() != null) body.getChildren().add(new Label("System key: " + existing.systemKey() + " (read-only)"));
        dialog.getDialogPane().setContent(body);
        Button save = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ControlStyles.apply(save, ControlStyles.Purpose.PRIMARY);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String value = name.getText() == null ? "" : name.getText().trim();
            if (value.isBlank()) { event.consume(); status.setText("Name is required."); }
        });
        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        String snapshotName = name.getText().trim();
        String snapshotColor = "#" + ColorUtil.toStoredColor(color.getValue()).substring(0, 6);
        String snapshotKey = existing == null ? null : existing.systemKey();
        CaseServicePort.PracticeAreaCommand command = new CaseServicePort.PracticeAreaCommand(
                existing == null ? null : existing.id(), tenantId, snapshotName, snapshotColor,
                existing == null || existing.active(), snapshotKey);
        mutate(() -> { if (existing == null) service.createPracticeArea(command); else service.updatePracticeArea(command); });
    }

    private void deactivate(PracticeAreaDto row) {
        if (!AppDialogs.showConfirmation(root.getScene() == null ? null : root.getScene().getWindow(),
                "Practice Areas", "Deactivate “" + row.name() + "”?",
                "It will no longer be available for new selections. Existing Cases keep their Practice Area.",
                "Deactivate", AppDialogs.DialogActionKind.DANGER)) return;
        int snapshotId = row.id();
        mutate(() -> service.deactivatePracticeArea(tenantId, snapshotId));
    }

    private void mutate(Runnable operation) {
        if (!mutating.compareAndSet(false, true)) return;
        setBusy(true); status.setText("Saving…");
        worker.execute(() -> {
            try {
                requireWorkerThread(); operation.run();
                Platform.runLater(() -> { if (disposed.get()) return; mutating.set(false); changes.markCommitted(); load(); });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> { if (disposed.get()) return; mutating.set(false); setBusy(false); status.setText("Change failed. Check your connection and try again."); });
            }
        });
    }

    private void setBusy(boolean busy) { add.setDisable(busy); refresh.setDisable(busy); list.setDisable(busy); }
    private static void requireWorkerThread() { if (Platform.isFxApplicationThread()) throw new IllegalStateException("Practice Area service work must run off the JavaFX thread."); }
    private static Color color(String value) { try { return value == null || value.isBlank() ? Color.web("#6C757D") : Color.web(value); } catch (IllegalArgumentException ex) { return Color.web("#6C757D"); } }
    private static Button button(String text, ControlStyles.Purpose purpose) { Button b = new Button(text); ControlStyles.apply(b, purpose, ControlStyles.Size.SMALL); return b; }
}
