package com.shale.ui.controller;

import static com.shale.core.service.ContactServicePort.*;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import com.shale.core.service.ContactServicePort;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Cohesive administrator UI for Phase 2A definition administration. */
public final class ContactClassificationAdminPane {
    private final ContactServicePort service;
    private final AppState state;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "contact-classification-settings"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicBoolean mutating = new AtomicBoolean();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final AtomicInteger loadGeneration = new AtomicInteger();
    private final TabPane tabs = new TabPane();
    private final CheckBox showRemoved = new CheckBox("Show removed");
    private final Label status = new Label();
    private final Button add = button("Add", ControlStyles.Purpose.PRIMARY);
    private final Button refresh = button("Refresh", ControlStyles.Purpose.GHOST);
    private final VBox list = new VBox(12);
    private final VBox root = new VBox(14);
    private volatile List<AdministrationDefinition> rows = List.of();

    public ContactClassificationAdminPane(ContactServicePort service, AppState state) {
        this.service = Objects.requireNonNull(service); this.state = Objects.requireNonNull(state);
        tabs.getTabs().setAll(tab("Contact Types", DefinitionCategory.CONTACT_TYPE),
                tab("Specialties", DefinitionCategory.SPECIALTY), tab("Credentials", DefinitionCategory.CREDENTIAL));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setId("contact-classification-tabs");
        tabs.getStyleClass().add("contact-classification-tabs");
        tabs.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> requestLoad());
        showRemoved.setId("contact-classification-show-removed");
        showRemoved.getStyleClass().add("contact-classification-toggle");
        showRemoved.setAccessibleText("Show removed Contact classification definitions");
        showRemoved.selectedProperty().addListener((o,a,b) -> render());
        add.setId("contact-classification-add");
        add.setAccessibleText("Add definition to selected Contact classification category");
        add.setTooltip(new Tooltip("Create a definition in the selected category"));
        refresh.setId("contact-classification-refresh");
        refresh.setAccessibleText("Refresh Contact classifications");
        refresh.setTooltip(new Tooltip("Reload the selected category"));
        add.setOnAction(e -> openEditor(null)); refresh.setOnAction(e -> requestLoad());
        status.setId("contact-classification-status");
        status.getStyleClass().addAll("search-summary-text", "contact-classification-status"); status.setWrapText(true);
        FlowPane actions = new FlowPane(Orientation.HORIZONTAL, 8, 8, add, refresh, showRemoved);
        actions.getStyleClass().add("contact-classification-toolbar");
        list.setId("contact-classification-list");
        list.getStyleClass().add("contact-classification-list");
        root.getStyleClass().add("contact-classification-admin");
        root.getChildren().setAll(tabs, actions, status, list);
        root.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null && newScene == null) dispose();
        });
        if (!state.isAdmin()) {
            tabs.setDisable(true); actions.setDisable(true);
            setStatus("Access restricted — administrator access is required.", "error");
        } else requestLoad();
    }

    public Node node() { return root; }
    DefinitionCategory selectedCategory() { return (DefinitionCategory) tabs.getSelectionModel().getSelectedItem().getUserData(); }

    private Tab tab(String text, DefinitionCategory category) { Tab t = new Tab(text); t.setUserData(category); return t; }

    private void requestLoad() {
        loadGeneration.incrementAndGet();
        loadLatest();
    }

    private void loadLatest() {
        if (disposed.get() || !state.isAdmin() || !loading.compareAndSet(false, true)) return;
        int generation = loadGeneration.get();
        int tenant = tenant(), actor = actor(); setBusy(true);
        setStatus(rows.isEmpty() ? "Loading classifications…" : "Refreshing classifications…", "loading");
        DefinitionCategory category = selectedCategory();
        worker.submit(() -> {
            try {
                if (Platform.isFxApplicationThread()) throw new IllegalStateException("Settings reads must run off the JavaFX thread.");
                List<AdministrationDefinition> loaded = service.listDefinitionsForAdministration(category, tenant, actor);
                Platform.runLater(() -> finishLoad(generation, category, loaded, null));
            } catch (RuntimeException ex) {
                Platform.runLater(() -> finishLoad(generation, category, null, ex));
            }
        });
    }

    private void finishLoad(int generation, DefinitionCategory category,
            List<AdministrationDefinition> loaded, RuntimeException failure) {
        loading.set(false);
        if (disposed.get()) return;
        if (generation != loadGeneration.get() || category != selectedCategory()) {
            loadLatest();
            return;
        }
        setBusy(false);
        if (failure == null) { rows = loaded; setStatus("", "ready"); render(); }
        else setStatus("Load failed — classifications could not be loaded. Choose Refresh to try again.", "error");
    }

    private void render() {
        List<AdministrationDefinition> visible = rows.stream().filter(r -> showRemoved.isSelected() || !r.deleted()).toList();
        if (visible.isEmpty()) {
            Label empty = new Label(showRemoved.isSelected()
                    ? "No definitions — this category does not contain any definitions."
                    : "No active definitions to display. Turn on Show removed to include removed definitions.");
            empty.setWrapText(true);
            empty.getStyleClass().addAll("contact-classification-empty", "contact-classification-state-message");
            list.getChildren().setAll(empty); return;
        }
        list.getChildren().setAll(visible.stream().map(this::card).toList());
    }

    private Node card(AdministrationDefinition row) {
        Label title = new Label(row.name());
        title.setWrapText(true); title.getStyleClass().add("contact-classification-card-title");
        HBox heading = new HBox(8, title);
        if (row.abbreviation() != null && !row.abbreviation().isBlank()) {
            Label abbreviation = new Label(row.abbreviation());
            abbreviation.getStyleClass().add("contact-classification-abbreviation");
            heading.getChildren().add(abbreviation);
        }
        Label detail = new Label("Internal key: " + row.systemKey() + "  •  Sort order: " + row.sortOrder());
        detail.setWrapText(true); detail.getStyleClass().add("contact-classification-metadata");
        FlowPane badges = new FlowPane(Orientation.HORIZONTAL, 6, 6);
        badges.getStyleClass().add("contact-classification-badges");
        badges.getChildren().setAll(badge(origin(row), "origin-" + origin(row).toLowerCase(Locale.ROOT)),
                badge(stateLabel(row), "state-" + stateSlug(row)));
        if (!row.active() && !row.deleted()) badges.getChildren().add(badge("Inactive", "state-inactive"));
        if (row.deleted()) badges.getChildren().add(badge("Removed", "state-removed"));
        String descriptionText = row.description() == null || row.description().isBlank()
                ? "No description provided." : row.description();
        Label description = new Label(descriptionText);
        description.setWrapText(true); description.setMaxWidth(Double.MAX_VALUE);
        description.getStyleClass().add(row.description() == null || row.description().isBlank()
                ? "contact-classification-description-missing" : "contact-classification-description");
        FlowPane actions = new FlowPane(Orientation.HORIZONTAL, 6, 6);
        actions.getStyleClass().add("contact-classification-card-actions");
        actions.getChildren().setAll(actions(row));
        VBox card = new VBox(8, heading, detail, badges, description, actions);
        card.setFillWidth(true); card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().addAll("shale-card-surface", "contact-classification-card");
        if (!row.active() || row.deleted()) card.getStyleClass().add("contact-classification-card-subdued");
        return card;
    }

    private List<Node> actions(AdministrationDefinition row) {
        if (row.global()) {
            if (row.overlayState() == DefinitionOverlayState.EFFECTIVE)
                return List.of(action("Customize", () -> openEditor(row)));
            return List.of();
        }
        if (row.deleted()) return List.of(action(row.origin() == DefinitionOrigin.OVERRIDE ? "Restore Override" : "Restore", () -> lifecycle(row, "restore")));
        return List.of(action("Edit", () -> openEditor(row)),
                action(row.active() ? "Deactivate" : "Activate", () -> lifecycle(row, row.active() ? "deactivate" : "activate")),
                action(row.origin() == DefinitionOrigin.OVERRIDE ? "Reset to Global" : "Remove", () -> lifecycle(row, "remove")));
    }

    private void lifecycle(AdministrationDefinition row, String operation) {
        if (row.global() || mutating.get()) return;
        if (!"activate".equals(operation) && !"restore".equals(operation)) {
            String effect = "deactivate".equals(operation) && row.origin() == DefinitionOrigin.OVERRIDE
                    ? "The inactive override will mask the global definition. "
                    : "remove".equals(operation) && row.origin() == DefinitionOrigin.OVERRIDE
                    ? "The global definition will become effective again. " : "";
            String verb = "deactivate".equals(operation) ? "Deactivate" : row.origin() == DefinitionOrigin.OVERRIDE ? "Reset to Global" : "Remove";
            if (!AppDialogs.showConfirmation(root.getScene() == null ? null : root.getScene().getWindow(),
                    "Contact Classifications", verb + " “" + row.name() + "”?", effect
                    + "Existing Contact assignments are preserved and historical assignments remain visible. "
                    + "Inactive or removed definitions cannot be selected for new assignments.", verb,
                    AppDialogs.DialogActionKind.DANGER)) return;
        }
        DefinitionLifecycleCommand command = new DefinitionLifecycleCommand(row.category(), row.id(), tenant(), actor(),
                "activate".equals(operation) || "restore".equals(operation), row.rowVer());
        mutate(() -> switch (operation) {
            case "activate", "deactivate" -> service.setDefinitionActive(command);
            case "remove" -> service.removeDefinition(command);
            case "restore" -> service.restoreDefinition(command);
            default -> throw new IllegalArgumentException("Unknown lifecycle action");
        }, failure -> setStatus(stale(failure)
                ? "Stale definition — this definition changed. Choose Refresh before trying the lifecycle action again."
                : "Change failed — " + friendly(failure), stale(failure) ? "stale" : "error"));
    }

    private void openEditor(AdministrationDefinition existing) {
        DefinitionCategory editorCategory = existing == null ? selectedCategory() : existing.category();
        boolean override = existing != null && existing.global();
        Dialog<Void> dialog = new Dialog<>();
        String heading = existing == null ? "Add " + categoryName(editorCategory) : override ? "Customize Global Definition" : "Edit Definition";
        dialog.setTitle(heading);
        if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow());
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().addAll("secondary-window-shell", "contact-classification-dialog");
        pane.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        TextField name = field(existing == null ? "" : existing.name(), "definition-name");
        TextField abbreviation = field(existing == null || existing.abbreviation() == null ? "" : existing.abbreviation(), "definition-abbreviation");
        TextArea description = new TextArea(existing == null || existing.description() == null ? "" : existing.description());
        configureDescription(description);
        TextField order = field(existing == null ? "0" : Integer.toString(existing.sortOrder()), "definition-sort-order");
        TextField key = field(existing == null ? "" : existing.systemKey(), "definition-internal-key");
        configureInternalKey(key, existing == null);
        Label keyHelp = new Label(existing == null ? "Used as the stable system identifier."
                : "Read-only: the stable Internal Key cannot be changed after creation.");
        keyHelp.setWrapText(true); keyHelp.getStyleClass().add("contact-classification-field-help");
        Label context = new Label(override
                ? "This creates an organization-specific override; the global definition remains unchanged."
                : "Required fields are marked with an asterisk (*).");
        context.setWrapText(true); context.getStyleClass().add("contact-classification-dialog-context");
        Label error = new Label(); error.setId("definition-editor-error"); error.setWrapText(true);
        error.getStyleClass().add("contact-classification-editor-error");
        Button reload = button("Reload list", ControlStyles.Purpose.SECONDARY);
        reload.setVisible(false); reload.setManaged(false);
        reload.setAccessibleText("Close editor and reload Contact classifications");
        reload.setOnAction(event -> { dialog.close(); requestLoad(); });
        if (existing == null) name.textProperty().addListener((o,a,b) -> { if (key.getText().isBlank() || key.getText().equals(systemKeyFromName(a))) key.setText(systemKeyFromName(b)); });
        GridPane form = new GridPane(); form.setHgap(12); form.setVgap(10);
        form.getStyleClass().add("contact-classification-form");
        ColumnConstraints labels = new ColumnConstraints(); labels.setMinWidth(105);
        ColumnConstraints controls = new ColumnConstraints(); controls.setHgrow(Priority.ALWAYS); controls.setFillWidth(true);
        form.getColumnConstraints().setAll(labels, controls);
        int line=0; form.addRow(line++, new Label(editorCategory==DefinitionCategory.CREDENTIAL?"Full Name *":"Name *"), name);
        if (editorCategory==DefinitionCategory.CREDENTIAL) form.addRow(line++, new Label("Abbreviation *"), abbreviation);
        form.addRow(line++, new Label("Description"), description); form.addRow(line++, new Label("Sort Order"), order);
        form.addRow(line++, new Label("Internal Key *"), new VBox(4, key, keyHelp));
        VBox body = new VBox(12, context, form, error, reload);
        body.getStyleClass().add("contact-classification-dialog-body");
        pane.setContent(body);
        Button save = (Button) pane.lookupButton(ButtonType.OK); save.setText("Save"); ControlStyles.apply(save, ControlStyles.Purpose.PRIMARY);
        Button cancel = (Button) pane.lookupButton(ButtonType.CANCEL); ControlStyles.apply(cancel, ControlStyles.Purpose.SECONDARY);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            String validation = validate(name.getText(), abbreviation.getText(), order.getText(), key.getText(), editorCategory);
            if (validation != null) { error.setText(validation); return; }
            int sort = Integer.parseInt(order.getText());
            Supplier<DefinitionMutationResult> mutation;
            if (existing == null || override) mutation = () -> service.createDefinition(new CreateDefinitionCommand(editorCategory, tenant(), actor(),
                    override ? existing.systemKey() : key.getText(), override ? existing.id() : null, name.getText(), abbreviation.getText(),
                    description.getText(), sort, true));
            else mutation = () -> service.updateDefinition(new UpdateDefinitionCommand(existing.category(), existing.id(), tenant(), actor(),
                    name.getText(), abbreviation.getText(), description.getText(), sort, existing.rowVer()));
            save.setDisable(true); error.setText("Saving changes…");
            mutate(mutation, failure -> {
                boolean staleData = stale(failure);
                save.setDisable(staleData);
                reload.setVisible(staleData); reload.setManaged(staleData);
                error.setText(staleData
                        ? "This definition changed while you were editing. Your values are preserved. Reload the list before editing again."
                        : friendly(failure));
            }, () -> dialog.close());
        });
        dialog.show();
    }

    private void mutate(Supplier<DefinitionMutationResult> operation, java.util.function.Consumer<RuntimeException> failure) { mutate(operation, failure, null); }
    private void mutate(Supplier<DefinitionMutationResult> operation, java.util.function.Consumer<RuntimeException> failure, Runnable success) {
        if (disposed.get() || !mutating.compareAndSet(false, true)) return; setBusy(true);
        setStatus("Applying classification change…", "loading");
        worker.submit(() -> { try { operation.get(); Platform.runLater(() -> { mutating.set(false); if (disposed.get()) return; setBusy(false); if(success!=null)success.run(); requestLoad(); }); }
            catch (RuntimeException ex) { Platform.runLater(() -> { mutating.set(false); if (disposed.get()) return; setBusy(false); if(failure!=null)failure.accept(ex); else setStatus("Change failed — " + friendly(ex), "error"); }); } });
    }

    void dispose() {
        if (disposed.compareAndSet(false, true)) worker.shutdownNow();
    }

    private void setBusy(boolean busy) { add.setDisable(busy); refresh.setDisable(busy); showRemoved.setDisable(busy); list.setDisable(busy); }
    private int tenant() { Integer id=state.getShaleClientId(); if(id==null||id<=0)throw new SecurityException("A tenant session is required."); return id; }
    private int actor() { Integer id=state.getUserId(); if(id==null||id<=0)throw new SecurityException("An authenticated administrator is required."); return id; }
    private static TextField field(String value, String id) { TextField field=new TextField(value); field.setId(id); ControlStyles.formControl(field); field.setMaxWidth(Double.MAX_VALUE); return field; }
    static void configureDescription(TextArea description) {
        description.setId("definition-description"); description.setPrefRowCount(5); description.setWrapText(true);
        ControlStyles.formControl(description);
    }
    static void configureInternalKey(TextField key, boolean creatingCustomDefinition) {
        key.setEditable(creatingCustomDefinition); key.setPromptText("lowercase_snake_case");
        if (!creatingCustomDefinition) key.getStyleClass().add("contact-classification-readonly");
    }
    private static Button button(String text, ControlStyles.Purpose purpose) { Button b=new Button(text); ControlStyles.apply(b,purpose,ControlStyles.Size.STANDARD); return b; }
    private Button action(String text, Runnable run) {
        ControlStyles.Purpose purpose = text.equals("Remove") ? ControlStyles.Purpose.DANGER
                : text.equals("Deactivate") || text.equals("Reset to Global") ? ControlStyles.Purpose.GHOST
                : ControlStyles.Purpose.SECONDARY;
        Button b=button(text, purpose); b.getStyleClass().add(text.equals("Deactivate") || text.equals("Reset to Global") ? "contact-classification-caution" : "contact-classification-action");
        b.setAccessibleText(text+" definition"); b.setTooltip(new Tooltip(text+" this Contact classification definition")); b.setOnAction(e->run.run()); return b;
    }
    private static Label badge(String text, String semanticClass) { Label l=new Label(text); l.getStyleClass().addAll("metadata-chip", "contact-classification-badge", semanticClass); return l; }
    private static String origin(AdministrationDefinition r) { return switch(r.origin()){case GLOBAL->"Global";case CUSTOM->"Custom";case OVERRIDE->"Override";}; }
    private static String stateLabel(AdministrationDefinition r) { return switch(r.overlayState()){case EFFECTIVE->"Effective";case OVERRIDDEN->"Overridden";case MASKED_GLOBAL->"Global masked";case GLOBAL_FALLBACK->"Global fallback";case INEFFECTIVE->"Not effective";}; }
    private static String stateSlug(AdministrationDefinition r) { return switch(r.overlayState()){case EFFECTIVE->"effective";case OVERRIDDEN->"overridden";case MASKED_GLOBAL->"global-masked";case GLOBAL_FALLBACK->"global-fallback";case INEFFECTIVE->"ineffective";}; }
    private static String categoryName(DefinitionCategory c) { return switch(c){case CONTACT_TYPE->"Contact Type";case SPECIALTY->"Specialty";case CREDENTIAL->"Credential";}; }
    public static String systemKeyFromName(String value) { if(value==null)return ""; String s=Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$",""); return s; }
    public static boolean validSystemKey(String value) { return value!=null && value.length()<=64 && value.matches("[a-z][a-z0-9]*(?:_[a-z0-9]+)*"); }
    private static String validate(String name,String abbreviation,String order,String key,DefinitionCategory category) { if(name==null||name.isBlank())return "Name is required."; if(name.trim().length()>100)return "Name must be at most 100 characters."; if(category==DefinitionCategory.CREDENTIAL&&(abbreviation==null||abbreviation.isBlank()))return "Abbreviation is required."; try{if(Integer.parseInt(order)<0)return "Sort Order must be a nonnegative integer.";}catch(Exception e){return "Sort Order must be a nonnegative integer.";} if(!validSystemKey(key))return "Internal key must be lowercase snake_case and at most 64 characters."; return null; }
    private static boolean stale(Throwable ex) { for(Throwable x=ex;x!=null;x=x.getCause())if(x.getMessage()!=null&&(x.getMessage().contains("changed")||x.getMessage().contains("reload")))return true; return false; }
    private static String friendly(Throwable ex) { String m=ex.getMessage(); if(m!=null&&(m.contains("SystemKey")||m.contains("already uses")||m.contains("shadow")))return m; return "The definition could not be saved. Your values have been kept."; }
    private void setStatus(String message, String stateName) {
        status.setText(message);
        status.getStyleClass().removeIf(style -> style.startsWith("contact-classification-status-"));
        status.getStyleClass().add("contact-classification-status-" + stateName);
        status.setVisible(!message.isBlank()); status.setManaged(!message.isBlank());
    }
}
