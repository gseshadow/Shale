package com.shale.ui.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.dto.FieldConfirmationPolicyDto;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.component.CommittedChangeTracker;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/** Case-Date-specific definition UI. Persistence remains exclusively behind CaseServicePort. */
public final class CaseDateTypeManagementPane {
    private static final Logger LOG = LoggerFactory.getLogger(CaseDateTypeManagementPane.class);
    private final CaseServicePort service;
    private final int tenantId;
    private final int actorId;
    private final Executor executor;
    private final IntConsumer publisher;
    private final CommittedChangeTracker changed;
    private final AtomicBoolean mutationInFlight = new AtomicBoolean();
    private final VBox root = new VBox(12);
    private final CaseDatePresentationManagementPane presentation;
    private final FlowPane cards = new FlowPane(10, 10);
    private final Label status = new Label();
    private final Button edit;
    private final Button toggle;
    private final Button remove;
    private final Button confirmation;
    private Map<String, FieldConfirmationPolicyDto> policies = Map.of();
    private List<CaseServicePort.ConfirmationRole> roles = List.of();
    private volatile boolean disposed;
    private int loadGeneration;
    private EffectiveCaseDateTypeDto selected;

    public CaseDateTypeManagementPane(CaseServicePort service, int tenantId, int actorId, Executor executor,
            IntConsumer publisher, CommittedChangeTracker changed) {
        this(service, tenantId, actorId, executor, publisher, ignored -> {}, changed);
    }

    public CaseDateTypeManagementPane(CaseServicePort service, int tenantId, int actorId, Executor executor,
            IntConsumer publisher, java.util.function.Consumer<com.shale.core.model.CaseDatePresentationPurpose> presentationPublisher,
            CommittedChangeTracker changed) {
        this.service = service; this.tenantId = tenantId; this.actorId = actorId; this.executor = executor;
        this.publisher = publisher == null ? ignored -> {} : publisher; this.changed = changed;
        Button add = button("Add Case Date Type", ControlStyles.Purpose.PRIMARY, () -> editDefinition(null));
        edit = button("Edit", ControlStyles.Purpose.SECONDARY, () -> editDefinition(selected));
        toggle = button("Activate/Deactivate", ControlStyles.Purpose.GHOST, this::toggleSelected);
        remove = button("Remove", ControlStyles.Purpose.DANGER, this::removeSelected);
        confirmation = button("Confirmation policy", ControlStyles.Purpose.SECONDARY, this::editConfirmationPolicy);
        status.getStyleClass().add("search-summary-text"); status.setWrapText(true);
        cards.setPrefWrapLength(820);
        presentation = new CaseDatePresentationManagementPane(service, tenantId, actorId, executor,
                presentationPublisher, changed);
        root.getChildren().addAll(new HBox(8, add, edit, toggle, remove, confirmation), status, cards,
                new Separator(), presentation.node());
        updateActions();
        reload(null);
    }

    public Node node() { return root; }
    public void dispose() { disposed = true; loadGeneration++; presentation.dispose(); }
    boolean mutationInFlight() { return mutationInFlight.get() || presentation.mutationInFlight(); }
    boolean canClose() { return !mutationInFlight() && !presentation.hasUnsavedChanges(); }
    int loadGeneration() { return loadGeneration; }

    private Button button(String text, ControlStyles.Purpose purpose, Runnable action) {
        return ActionButtonFactory.semantic(text, e -> action.run(), purpose, ControlStyles.Size.STANDARD);
    }

    void reload(String successMessage) {
        if (disposed) return;
        int generation = ++loadGeneration;
        status.setText("Loading case date types…"); cards.getChildren().clear();
        executor.execute(() -> {
            try {
                List<EffectiveCaseDateTypeDto> loaded = service.listCaseDateTypesForAdministration(tenantId, actorId);
                List<FieldConfirmationPolicyDto> loadedPolicies = service.listFieldConfirmationPolicies(tenantId, actorId);
                List<CaseServicePort.ConfirmationRole> loadedRoles = service.listConfirmationRoles(tenantId, actorId);
                Platform.runLater(() -> { policies=loadedPolicies.stream().collect(Collectors.toUnmodifiableMap(FieldConfirmationPolicyDto::caseDateTypePolicyKey, p->p)); roles=loadedRoles; applyLoad(generation, loaded, successMessage); });
            } catch (RuntimeException ex) {
                LOG.error("Case Date Type administration load failed tenantId={} actorId={}", tenantId, actorId, ex);
                Platform.runLater(() -> { if (current(generation)) status.setText("Case date types could not be loaded. Try again."); });
            }
        });
    }

    void applyLoad(int generation, List<EffectiveCaseDateTypeDto> loaded, String message) {
        if (!current(generation)) return;
        Integer selectedId = selected == null ? null : selected.id();
        List<EffectiveCaseDateTypeDto> rows = manageableRows(loaded, tenantId);
        selected = rows.stream().filter(r -> selectedId != null && r.id() == selectedId).findFirst().orElse(null);
        cards.getChildren().setAll(rows.stream().map(this::card).toList());
        status.setText(message == null ? (rows.isEmpty() ? "No custom Case Date Types yet." : "") : message);
        updateActions();
    }

    private boolean current(int generation) { return !disposed && generation == loadGeneration; }

    static List<EffectiveCaseDateTypeDto> manageableRows(List<EffectiveCaseDateTypeDto> loaded, int tenantId) {
        if (loaded == null) return List.of();
        return loaded.stream().filter(r -> isManageable(r, tenantId) || r != null && r.shaleClientId() == null && r.active() && !r.deleted())
                .sorted(Comparator.comparing(EffectiveCaseDateTypeDto::name, String.CASE_INSENSITIVE_ORDER).thenComparingInt(EffectiveCaseDateTypeDto::id)).toList();
    }

    static boolean isManageable(EffectiveCaseDateTypeDto row, int tenantId) {
        return row != null && row.shaleClientId() != null && row.shaleClientId() == tenantId
                && row.origin() != EffectiveCaseDateTypeDto.Origin.GLOBAL
                && !"intake".equals(normalizeKey(row.systemKey()));
    }

    private static String normalizeKey(String key) { return key == null ? null : key.trim().toLowerCase(Locale.ROOT); }
    static boolean isOrdinaryGlobal(EffectiveCaseDateTypeDto row) { String key=row==null?null:normalizeKey(row.systemKey()); return row!=null&&row.shaleClientId()==null&&("statute_of_limitations".equals(key)||"tort_notice_deadline".equals(key)); }

    static String lifecycleActionLabel(EffectiveCaseDateTypeDto row) {
        return row != null && row.active() ? "Deactivate" : "Activate";
    }

    private Node card(EffectiveCaseDateTypeDto row) {
        VBox card = new VBox(7); card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "case-date-custom-card");
        card.setMinWidth(240); card.setPrefWidth(340); card.setMaxWidth(390); card.setFocusTraversable(true); card.setUserData(row);
        Circle dot = new Circle(6); String css = ColorUtil.toCssBackgroundColorOrNull(row.color());
        if (css != null) dot.setStyle("-fx-fill: " + css + ";");
        Label name = new Label(row.name()); name.getStyleClass().add("app-dialog-field-label");
        HBox heading = new HBox(8, dot, name); heading.setAlignment(Pos.CENTER_LEFT);
        Label details = new Label((row.active() ? "Active" : "Inactive") + " · " + row.calendarCategory() + " · " + (row.supportsTime() ? "Timed or all-day" : "All-day only"));
        details.getStyleClass().add("search-summary-text"); details.setWrapText(true);
        FieldConfirmationPolicyDto policy=policies.get(policyKey(row));
        Label policyLabel=new Label(policy!=null&&policy.requiresConfirmation()?"Requires confirmation":"Confirmation not required");
        policyLabel.getStyleClass().add("search-summary-text");
        card.getChildren().addAll(heading, details, policyLabel);
        card.setOnMouseClicked(e -> select(row));
        card.setOnKeyPressed(e -> { if (e.getCode() == javafx.scene.input.KeyCode.SPACE || e.getCode() == javafx.scene.input.KeyCode.ENTER) select(row); });
        return card;
    }

    private void select(EffectiveCaseDateTypeDto row) { selected = row; updateActions(); status.setText(""); }
    private void updateActions() {
        boolean enabled = selected != null && !mutationInFlight.get();
        boolean definitionEditable=enabled&&(isManageable(selected,tenantId)||isOrdinaryGlobal(selected));
        boolean lifecycleEditable=enabled&&isManageable(selected,tenantId);
        edit.setDisable(!definitionEditable); toggle.setDisable(!lifecycleEditable); remove.setDisable(!lifecycleEditable);
        edit.setText(enabled&&isOrdinaryGlobal(selected)?"Customize for this firm":"Edit");
        confirmation.setDisable(!enabled || !selected.active() || selected.deleted());
        toggle.setText(selected == null ? "Activate/Deactivate" : lifecycleActionLabel(selected));
    }

    private void editConfirmationPolicy() {
        EffectiveCaseDateTypeDto type=selected;if(type==null||mutationInFlight.get())return;
        FieldConfirmationPolicyDto current=policies.get(policyKey(type));
        Dialog<Boolean> dialog=new Dialog<>();AppDialogs.applySecondaryDialogShell(dialog,"Case Date Type Settings");
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);
        CheckBox required=ControlStyles.formControl(new CheckBox("Requires confirmation"));required.setSelected(current!=null&&current.requiresConfirmation());
        ComboBox<CaseServicePort.ConfirmationRole> role=ControlStyles.formControl(new ComboBox<>());role.getItems().setAll(roles);role.setPromptText("Select confirming role");
        role.setConverter(new javafx.util.StringConverter<>(){public String toString(CaseServicePort.ConfirmationRole r){return r==null?"":r.name();}public CaseServicePort.ConfirmationRole fromString(String s){return null;}});
        if(current!=null)role.getSelectionModel().select(roles.stream().filter(r->Objects.equals(r.id(),current.requiredFirmWideRoleDefinitionId())).findFirst().orElse(null));
        role.setDisable(!required.isSelected());required.selectedProperty().addListener((o,a,b)->role.setDisable(!b));
        Label help=new Label("This tenant-wide policy applies whenever this resulting Case Date Type is created, changed, or restored. It is independent of New Intake Required.");help.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(10,new Label(type.name()),required,role,help));
        Button save=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);ControlStyles.apply(save,ControlStyles.Purpose.PRIMARY);
        save.addEventFilter(javafx.event.ActionEvent.ACTION,e->{if(required.isSelected()&&role.getValue()==null)e.consume();});
        dialog.setResultConverter(b->b==ButtonType.OK?Boolean.TRUE:null);
        dialog.showAndWait().ifPresent(ignored->mutate("Confirmation policy updated.",()->{service.setFieldConfirmationPolicy(new CaseServicePort.SetFieldConfirmationPolicyCommand(tenantId,actorId,type.id(),required.isSelected(),required.isSelected()?role.getValue().id():null,current==null?null:current.id(),current==null?null:current.rowVer()));return type.id();}));
    }

    static String policyKey(EffectiveCaseDateTypeDto type){return type.systemKey()==null?"TYPE:"+type.id():"SYSTEM:"+type.systemKey().trim().toLowerCase(Locale.ROOT);}

    private void editDefinition(EffectiveCaseDateTypeDto existing) {
        if (mutationInFlight.get()) return;
        showEditor(existing).ifPresent(input -> mutate(existing == null ? "Case date type added." : "Case date type updated.", () -> {
            boolean createOverride=existing!=null&&isOrdinaryGlobal(existing);
            EffectiveCaseDateTypeDto saved = existing == null
                    ? service.createCaseDateType(command(null, input, null, null))
                    : createOverride
                        ? service.createCaseDateType(command(null, input, existing.systemKey(), null))
                        : service.updateCaseDateType(command(existing.id(), input, existing.systemKey(), existing.rowVer()));
            return saved.id();
        }));
    }

    static CaseServicePort.CaseDateTypeCommand command(Integer id, Input input, String systemKey, byte[] rowVer, int tenantId, int actorId) {
        return new CaseServicePort.CaseDateTypeCommand(id, tenantId, actorId, systemKey, input.name(), input.description(), input.category(), input.color(), input.supportsTime(), input.sortOrder(), input.active(), rowVer);
    }
    private CaseServicePort.CaseDateTypeCommand command(Integer id, Input input, String systemKey, byte[] rowVer) { return command(id, input, systemKey, rowVer, tenantId, actorId); }

    private void toggleSelected() {
        EffectiveCaseDateTypeDto value = selected; if (value == null) return;
        mutate(value.active() ? "Case date type deactivated." : "Case date type activated.", () -> {
            service.setCaseDateTypeActive(new CaseServicePort.SetCaseDateTypeActiveCommand(tenantId, actorId, value.id(), !value.active(), value.rowVer())); return value.id();
        });
    }

    private void removeSelected() {
        EffectiveCaseDateTypeDto value = selected; if (value == null) return;
        if (!AppDialogs.showConfirmation(root.getScene() == null ? null : root.getScene().getWindow(), "Case Date Types", "Remove " + value.name() + "?",
                "This affects future selections only. Existing Case Dates retain their historical presentation.", "Remove", AppDialogs.DialogActionKind.DANGER)) return;
        mutate("Custom case date type removed from future selections.", () -> {
            service.resetCaseDateTypeOverride(new CaseServicePort.ResetCaseDateTypeOverrideCommand(tenantId, actorId, value.id(), value.rowVer())); return value.id();
        });
    }

    private void mutate(String success, Mutation operation) {
        if (disposed || !mutationInFlight.compareAndSet(false, true)) return;
        updateActions(); status.setText("Saving…"); final int generation = ++loadGeneration;
        executor.execute(() -> {
            try {
                int id = operation.run();
                if (disposed || generation != loadGeneration) return;
                changed.markCommitted();
                try { publisher.accept(id); } catch (RuntimeException publishFailure) {
                    LOG.warn("Case Date Type committed but live invalidation publication failed tenantId={} actorId={} typeId={}", tenantId, actorId, id, publishFailure);
                }
                Platform.runLater(() -> { if (current(generation)) { mutationInFlight.set(false); reload(success); } });
            } catch (RuntimeException ex) {
                LOG.error("Case Date Type administration mutation failed tenantId={} actorId={}", tenantId, actorId, ex);
                Platform.runLater(() -> { if (current(generation)) { mutationInFlight.set(false); updateActions(); status.setText("The Case Date Type could not be saved. Review the values or reload and try again."); } });
            }
        });
    }

    private java.util.Optional<Input> showEditor(EffectiveCaseDateTypeDto existing) {
        Dialog<Input> dialog = new Dialog<>(); String title = existing == null ? "Add Case Date Type" : "Edit Case Date Type";
        AppDialogs.applySecondaryDialogShell(dialog, title); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        TextField name = new TextField(existing == null ? "" : existing.name());
        TextField description = new TextField(existing == null || existing.description() == null ? "" : existing.description());
        ChoiceBox<String> category = ControlStyles.formControl(new ChoiceBox<>());
        category.getItems().setAll("DEADLINE", "TRIAL", "HEARING", "MEDIATION", "DEPOSITION", "NOTICE", "APPOINTMENT", "MILESTONE", "OTHER");
        category.setValue(existing == null ? "OTHER" : existing.calendarCategory());
        ColorPicker color = new ColorPicker(color(existing == null ? null : existing.color()));
        CheckBox supports = new CheckBox("Supports time of day"); supports.setSelected(existing != null && existing.supportsTime());
        CheckBox active = new CheckBox("Active"); active.setSelected(existing == null || existing.active());
        Label validation = new Label(); validation.getStyleClass().add("dialog-error-text");
        GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
        grid.addRow(0, new Label("Name"), name); grid.addRow(1, new Label("Description"), description); grid.addRow(2, new Label("Calendar Category"), category);
        grid.addRow(3, new Label("Color"), color); grid.add(supports, 1, 4); grid.add(active, 1, 5); grid.add(validation, 1, 6); dialog.getDialogPane().setContent(grid);
        Button save = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK); ControlStyles.apply(save, ControlStyles.Purpose.PRIMARY);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> { if (name.getText() == null || name.getText().trim().isEmpty()) { validation.setText("Name is required."); event.consume(); } });
        dialog.setResultConverter(b -> b == ButtonType.OK ? new Input(name.getText().trim(), description.getText().trim(), category.getValue(), toDb(color.getValue()), supports.isSelected(), existing == null ? null : existing.sortOrder(), active.isSelected()) : null);
        return dialog.showAndWait();
    }

    private static Color color(String value) { try { return value == null || value.isBlank() ? Color.web("#6C757D") : Color.web(value); } catch (IllegalArgumentException ex) { return Color.web("#6C757D"); } }
    private static String toDb(Color c) { return String.format("#%02X%02X%02X", Math.round(c.getRed()*255), Math.round(c.getGreen()*255), Math.round(c.getBlue()*255)); }
    @FunctionalInterface private interface Mutation { int run(); }
    public record Input(String name, String description, String category, String color, boolean supportsTime, Integer sortOrder, boolean active) {}
}
