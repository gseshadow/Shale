package com.shale.ui.controller;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.model.Organization;
import com.shale.core.service.OrganizationServicePort;
import com.shale.core.service.OrganizationServicePort.OrganizationFields;
import com.shale.core.service.OrganizationServicePort.UpdateOrganizationAggregateCommand;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.EnhancedTextArea;
import com.shale.ui.component.OrganizationTypeAssignmentPane;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.controller.support.OrganizationTypeAssignmentStage;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.WindowSizingUtil;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Dedicated, dialog-local editor for the complete Organization aggregate. */
final class EditOrganizationDialog {
	private static final Logger LOG = LoggerFactory.getLogger(EditOrganizationDialog.class);
    record LoadResult(Organization organization, byte[] rowVer, OrganizationTypeAssignmentStage assignments) {
        LoadResult { rowVer = rowVer == null ? null : rowVer.clone(); }
        @Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
    }

    private final int organizationId;
    private final OrganizationDao dao;
    private final OrganizationServicePort service;
    private final AppState state;
    private final Executor executor;
    private final Consumer<OrganizationServicePort.OrganizationAggregateResult> saved;
    private final Runnable closed;
    private final Dialog<Void> dialog = new Dialog<>();
    private final Label status = new Label("Loading authoritative Organization details…");
    private final VBox content = new VBox(14);
    private final ScrollPane scroll = new ScrollPane(content);
    private final ButtonType saveType = new ButtonType("Save Changes", ButtonData.OK_DONE);
    private final TextField name = field(), phone = field(), fax = field(), email = field(), website = field();
    private final TextField address1 = field(), address2 = field(), city = field(), province = field(), postal = field(), country = field();
    private final EnhancedTextArea notes = new EnhancedTextArea();
    private final OrganizationTypeAssignmentPane assignments = new OrganizationTypeAssignmentPane();
    private LoadResult baseline;
    private boolean saving;
    private boolean loading = true;
    private boolean forceClose;
    private long generation;

    EditOrganizationDialog(int organizationId, OrganizationDao dao, OrganizationServicePort service, AppState state,
            Executor executor, Consumer<OrganizationServicePort.OrganizationAggregateResult> saved, Runnable closed) {
        this.organizationId = organizationId;
        this.dao = Objects.requireNonNull(dao);
        this.service = Objects.requireNonNull(service);
        this.state = Objects.requireNonNull(state);
        this.executor = Objects.requireNonNull(executor);
        this.saved = Objects.requireNonNull(saved);
        this.closed = Objects.requireNonNull(closed);
    }

    void show(Window owner) {
        AppDialogs.applySecondaryDialogShell(dialog, "Edit Organization");
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().setAll(saveType, ButtonType.CANCEL);
        status.setWrapText(true);
        status.getStyleClass().add("dialog-error-text");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("contact-editor-section-scroll");
        content.setPadding(new Insets(4));
        VBox shell = new VBox(10, status, scroll);
        shell.getStyleClass().add("contact-editor-surface");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        dialog.getDialogPane().setContent(shell);
        dialog.getDialogPane().getStyleClass().add("contact-editor-dialog");
        dialog.getDialogPane().setPrefSize(820, 680);
        Button saveButton = button(saveType);
        Button cancelButton = button(ButtonType.CANCEL);
        ControlStyles.apply(saveButton, ControlStyles.Purpose.PRIMARY);
        ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
        saveButton.setDisable(true);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> { event.consume(); save(); });
        cancelButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            requestClose();
        });
        dialog.setOnCloseRequest(event -> {
            if (!forceClose && !confirmDiscard()) event.consume();
        });
        dialog.setOnHidden(event -> { generation++; closed.run(); });
        dialog.show();
        if (dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
            WindowSizingUtil.sizeModalStage(stage, owner, 820, 680, 620, 480);
        }
        reload();
    }

    private void reload() {
        loading = true;
        baseline = null;
        content.getChildren().clear();
        showStatus("Loading authoritative Organization details…");
        button(saveType).setDisable(true);
        long request = ++generation;
        int tenant = requirePositive(state.getShaleClientId(), "No tenant is selected.");
        executor.execute(() -> {
            try {
                Organization organization = dao.findById(organizationId);
                if (organization == null || !Objects.equals(organization.getShaleClientId(), tenant))
                    throw new IllegalStateException("Organization was not found.");
                var definitions = service.listEffectiveOrganizationTypes(tenant);
                var profile = service.getOrganizationTypeProfile(organizationId, tenant)
                        .orElseThrow(() -> new IllegalStateException("Organization Type profile was not found."));
                var stage = OrganizationTypeAssignmentStage.forEdit(definitions, profile);
                byte[] rowVer = dao.findOrganizationRowVer(organizationId, tenant);
                if (rowVer == null) throw new IllegalStateException("Organization concurrency data was not found.");
                LoadResult result = new LoadResult(organization, rowVer, stage);
                Platform.runLater(() -> applyLoad(request, result));
            } catch (RuntimeException failure) {
                Platform.runLater(() -> applyLoadFailure(request));
            }
        });
    }

    private void applyLoad(long request, LoadResult loaded) {
        if (request != generation || !dialog.isShowing()) return;
        baseline = loaded;
        loading = false;
        populate(loaded.organization());
        assignments.setStage(loaded.assignments());
        assignments.setMessageHandler(this::showStatus);
        content.getChildren().setAll(section("Organization Details", detailsGrid()), section("Organization Types", assignments));
        hideStatus();
        button(saveType).setDisable(false);
    }

    private void applyLoadFailure(long request) {
        if (request != generation || !dialog.isShowing()) return;
        loading = false;
        showStatus("Unable to load authoritative Organization details. Close this window and try again.");
    }

    private void save() {
        if (saving || loading || baseline == null) return;
        if (safe(name.getText()).isBlank()) { showStatus("Name is required."); return; }
        if (!baseline.assignments().isValid()) { showStatus("Exactly one eligible primary Organization Type is required."); return; }
        int tenant;
        int actor;
        try {
            tenant = requirePositive(state.getShaleClientId(), "No tenant is selected.");
            actor = requirePositive(state.getUserId(), "You are not authorized to edit Organizations.");
        } catch (IllegalStateException failure) { showStatus(failure.getMessage()); return; }
        var fields = new OrganizationFields(value(name), value(phone), value(fax), value(email), value(website),
                value(address1), value(address2), value(city), value(province), value(postal), value(country), safe(notes.getText()));
        var command = new UpdateOrganizationAggregateCommand(organizationId, tenant, actor, baseline.rowVer(), fields,
                baseline.assignments().commandAssignments());
        saving = true;
        setControlsDisabled(true);
        showStatus("Saving Organization…");
        executor.execute(() -> {
            try {
                var result = service.updateOrganizationAggregate(command);
                Platform.runLater(() -> {
                    if (!dialog.isShowing()) return;
                    saving = false;
                    forceClose = true;
                    dialog.close();
                    saved.accept(result);
                });
            } catch (RuntimeException failure) {
				LOG.warn("Organization aggregate save failed operation=updateOrganizationAggregate tenantId={} actorId={} organizationId={} exceptionClass={}",
						tenant, actor, organizationId, failure.getClass().getName(), failure);
                Platform.runLater(() -> {
                    if (!dialog.isShowing()) return;
                    saving = false;
					String safeFailure = safeFailureMessage(failure);
					boolean conflict = safeFailure.contains("changed by another user");
                    if (conflict) {
                        showStatus("Organization changed elsewhere. Authoritative values are being reloaded.");
                        reload();
					} else if (safeFailure.contains("compatibility ownership")) {
						setControlsDisabled(false);
						showStatus("Organization contact data changed. Reload the Organization and try again.");
					} else if (failure instanceof IllegalArgumentException && !safeFailure.isBlank()) {
						setControlsDisabled(false);
						showStatus(failure.getMessage());
                    } else {
                        setControlsDisabled(false);
                        showStatus("Unable to save Organization. No changes were applied.");
                    }
                });
            }
        });
    }

	private static String safeFailureMessage(Throwable failure) {
		return failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(java.util.Locale.ROOT);
	}

    private GridPane detailsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(9);
        ColumnConstraints labels = new ColumnConstraints(130, 150, 180);
        ColumnConstraints controls = new ColumnConstraints(); controls.setHgrow(Priority.ALWAYS); controls.setFillWidth(true);
        grid.getColumnConstraints().addAll(labels, controls);
        List.of(name, phone, fax, email, website, address1, address2, city, province, postal, country).forEach(ControlStyles::formControl);
        notes.setEditorTitle("Organization Notes"); notes.setPrefRowCount(5); notes.setMaxWidth(Double.MAX_VALUE);
        int row = 0;
        add(grid, row++, "Name", name); add(grid, row++, "Phone", phone); add(grid, row++, "Fax", fax);
        add(grid, row++, "Email", email); add(grid, row++, "Website", website); add(grid, row++, "Address 1", address1);
        add(grid, row++, "Address 2", address2); add(grid, row++, "City", city); add(grid, row++, "State", province);
        add(grid, row++, "Postal Code", postal); add(grid, row++, "Country", country); add(grid, row, "Notes", notes);
        return grid;
    }

    private boolean isDirty() {
        if (baseline == null) return false;
        Organization o = baseline.organization();
        return !Objects.equals(value(name), safe(o.getName())) || !Objects.equals(value(phone), safe(o.getPhone()))
                || !Objects.equals(value(fax), safe(o.getFax())) || !Objects.equals(value(email), safe(o.getEmail()))
                || !Objects.equals(value(website), safe(o.getWebsite())) || !Objects.equals(value(address1), safe(o.getAddress1()))
                || !Objects.equals(value(address2), safe(o.getAddress2())) || !Objects.equals(value(city), safe(o.getCity()))
                || !Objects.equals(value(province), safe(o.getState())) || !Objects.equals(value(postal), safe(o.getPostalCode()))
                || !Objects.equals(value(country), safe(o.getCountry())) || !Objects.equals(safe(notes.getText()), safe(o.getNotes()))
                || baseline.assignments().isDirty();
    }

    private void requestClose() { if (confirmDiscard()) { forceClose = true; dialog.close(); } }
    private boolean confirmDiscard() {
        if (saving) return false;
        return !isDirty() || AppDialogs.showConfirmation(dialog.getOwner(), "Discard Changes?", "Discard unsaved changes?",
                "Closing will discard all Organization and Organization Type changes.", "Discard Changes", AppDialogs.DialogActionKind.DANGER);
    }
    private void populate(Organization o) {
        name.setText(safe(o.getName())); phone.setText(safe(o.getPhone())); fax.setText(safe(o.getFax())); email.setText(safe(o.getEmail()));
        website.setText(safe(o.getWebsite())); address1.setText(safe(o.getAddress1())); address2.setText(safe(o.getAddress2()));
        city.setText(safe(o.getCity())); province.setText(safe(o.getState())); postal.setText(safe(o.getPostalCode()));
        country.setText(safe(o.getCountry())); notes.setText(safe(o.getNotes()));
    }
    private void setControlsDisabled(boolean disabled) { content.setDisable(disabled); button(saveType).setDisable(disabled); button(ButtonType.CANCEL).setDisable(disabled); }
    private void showStatus(String message) { status.setText(message == null ? "" : message); status.setVisible(true); status.setManaged(true); }
    private void hideStatus() { status.setText(""); status.setVisible(false); status.setManaged(false); }
    private Button button(ButtonType type) { return (Button) dialog.getDialogPane().lookupButton(type); }
    private static TextField field() { TextField f = new TextField(); f.setMaxWidth(Double.MAX_VALUE); return f; }
    private static VBox section(String title, Node body) { Label heading = new Label(title); heading.getStyleClass().add("contact-editor-section-heading"); return new VBox(9, heading, body); }
    private static void add(GridPane grid, int row, String text, Node field) { Label label = new Label(text); label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); label.setLabelFor(field); label.getStyleClass().add("contact-editor-field-label"); grid.add(label, 0, row); grid.add(field, 1, row); GridPane.setHgrow(field, Priority.ALWAYS); }
    private static int requirePositive(Integer value, String message) { if (value == null || value <= 0) throw new IllegalStateException(message); return value; }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String value(TextField field) { return safe(field.getText()); }
}
