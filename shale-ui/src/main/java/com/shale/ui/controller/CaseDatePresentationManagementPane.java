package com.shale.ui.controller;

import com.shale.core.dto.CaseDatePresentationConfigurationDto;
import com.shale.core.dto.CaseDatePresentationSelectionDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.core.model.CaseDatePresentationPurpose;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.CommittedChangeTracker;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Administrator editor for the two tenant Case Date presentation defaults. */
final class CaseDatePresentationManagementPane {
    private static final Logger LOG = LoggerFactory.getLogger(CaseDatePresentationManagementPane.class);
    private final CaseServicePort service;
    private final int tenantId;
    private final int actorId;
    private final Executor executor;
    private final Consumer<CaseDatePresentationPurpose> publisher;
    private final CommittedChangeTracker changed;
    private final VBox root = new VBox(12);
    private final Label status = new Label("Loading presentation settings…");
    private final Map<CaseDatePresentationPurpose, Editor> editors = new LinkedHashMap<>();
    private final AtomicBoolean saving = new AtomicBoolean();
    private volatile boolean disposed;
    private int generation;

    CaseDatePresentationManagementPane(CaseServicePort service, int tenantId, int actorId, Executor executor,
            Consumer<CaseDatePresentationPurpose> publisher, CommittedChangeTracker changed) {
        this.service = service;
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.executor = executor;
        this.publisher = publisher == null ? ignored -> { } : publisher;
        this.changed = changed;
        Label heading = new Label("Presentation defaults");
        heading.getStyleClass().add("shale-section-title");
        Label help = new Label("Choose and order the dates shown on Case Cards and on Case Overview. Overview defaults apply only to cases without a per-case Overview override. Intake may be hidden here without changing its protected workflow identity.");
        help.setWrapText(true);
        help.getStyleClass().add("search-summary-text");
        status.setWrapText(true);
        status.getStyleClass().add("search-summary-text");
        editors.put(CaseDatePresentationPurpose.CASE_CARD, new Editor(CaseDatePresentationPurpose.CASE_CARD, "Case Card dates"));
        editors.put(CaseDatePresentationPurpose.CASE_OVERVIEW, new Editor(CaseDatePresentationPurpose.CASE_OVERVIEW, "Default Case Overview dates"));
        root.getChildren().addAll(heading, help, status, editors.get(CaseDatePresentationPurpose.CASE_CARD).root,
                editors.get(CaseDatePresentationPurpose.CASE_OVERVIEW).root);
        reload(false);
    }

    Node node() { return root; }
    boolean mutationInFlight() { return saving.get(); }
    boolean hasUnsavedChanges() { return editors.values().stream().anyMatch(Editor::dirty); }
    void dispose() { disposed = true; generation++; }

    private void reload(boolean discardDirty) {
        if (disposed || (!discardDirty && hasUnsavedChanges())) {
            status.setText("Save or discard your unsaved presentation changes before reloading or closing.");
            return;
        }
        int request = ++generation;
        status.setText("Loading presentation settings…");
        setDisabled(true);
        executor.execute(() -> {
            try {
                List<EffectiveCaseDateTypeDto> types = service.listEffectiveCaseDateTypes(tenantId, actorId);
                var card = service.getCaseDatePresentationConfiguration(tenantId, actorId, CaseDatePresentationPurpose.CASE_CARD);
                var overview = service.getCaseDatePresentationConfiguration(tenantId, actorId, CaseDatePresentationPurpose.CASE_OVERVIEW);
                Platform.runLater(() -> applyLoad(request, types, card, overview));
            } catch (RuntimeException ex) {
                LOG.error("Case Date presentation administration load failed tenantId={} actorId={}", tenantId, actorId, ex);
                Platform.runLater(() -> { if (current(request)) { setDisabled(false); status.setText("Presentation settings could not be loaded. Select Reload to try again."); } });
            }
        });
    }

    private void applyLoad(int request, List<EffectiveCaseDateTypeDto> types,
            CaseDatePresentationConfigurationDto card, CaseDatePresentationConfigurationDto overview) {
        if (!current(request)) return;
        List<EffectiveCaseDateTypeDto> eligible = eligibleTypes(types);
        editors.get(CaseDatePresentationPurpose.CASE_CARD).load(card, eligible);
        editors.get(CaseDatePresentationPurpose.CASE_OVERVIEW).load(overview, eligible);
        setDisabled(false);
        status.setText("");
    }

    static List<EffectiveCaseDateTypeDto> eligibleTypes(List<EffectiveCaseDateTypeDto> types) {
        if (types == null) return List.of();
        Map<String, EffectiveCaseDateTypeDto> distinct = new LinkedHashMap<>();
        types.stream().filter(t -> t != null && t.active() && !t.deleted())
                .forEach(t -> distinct.putIfAbsent(identity(t), t));
        return List.copyOf(distinct.values());
    }

    static String identity(EffectiveCaseDateTypeDto type) {
        return type.systemKey() == null || type.systemKey().isBlank()
                ? "TYPE:" + type.id()
                : "SYSTEM:" + type.systemKey().trim().toLowerCase(Locale.ROOT);
    }

    private void save(Editor editor) {
        if (disposed || !editor.dirty() || !saving.compareAndSet(false, true)) return;
        setDisabled(true);
        status.setText("Saving " + editor.title + "…");
        int request = ++generation;
        var command = new CaseServicePort.ReplaceCaseDatePresentationConfigurationCommand(
                tenantId, actorId, editor.purpose, editor.identities(), editor.configuration.rowVer());
        executor.execute(() -> {
            try {
                CaseDatePresentationConfigurationDto saved = service.replaceCaseDatePresentationConfiguration(command);
                changed.markCommitted();
                try { publisher.accept(editor.purpose); }
                catch (RuntimeException ex) { LOG.warn("Case Date presentation committed but invalidation failed tenantId={} purpose={}", tenantId, editor.purpose, ex); }
                Platform.runLater(() -> { if (current(request)) { saving.set(false); editor.load(saved, editor.eligible); setDisabled(false); status.setText(editor.title + " saved."); } });
            } catch (RuntimeException ex) {
                LOG.error("Case Date presentation save failed tenantId={} actorId={} purpose={}", tenantId, actorId, editor.purpose, ex);
                Platform.runLater(() -> { if (current(request)) { saving.set(false); setDisabled(false); status.setText(isStale(ex)
                        ? "This configuration changed elsewhere. Select Reload, review the latest order, and save again."
                        : "The presentation configuration could not be saved. Review the selections or reload and try again."); } });
            }
        });
    }

    private static boolean isStale(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause())
            if (current.getMessage() != null && current.getMessage().toLowerCase(Locale.ROOT).contains("reload before saving")) return true;
        return false;
    }

    private boolean current(int request) { return !disposed && request == generation; }
    private void setDisabled(boolean value) { editors.values().forEach(e -> e.root.setDisable(value)); }
    private Button button(String text, ControlStyles.Purpose purpose, Runnable action) {
        return ActionButtonFactory.semantic(text, event -> action.run(), purpose, ControlStyles.Size.SMALL);
    }

    private final class Editor {
        private final CaseDatePresentationPurpose purpose;
        private final String title;
        private final VBox root = new VBox(8);
        private final VBox rows = new VBox(6);
        private final ComboBox<EffectiveCaseDateTypeDto> available = ControlStyles.formControl(new ComboBox<>());
        private final Button remove;
        private final Button up;
        private final Button down;
        private final Button save;
        private final List<CaseDatePresentationSelectionDto> working = new ArrayList<>();
        private CaseDatePresentationConfigurationDto configuration;
        private List<EffectiveCaseDateTypeDto> eligible = List.of();
        private int selected = -1;

        Editor(CaseDatePresentationPurpose purpose, String title) {
            this.purpose = purpose; this.title = title;
            Label label = new Label(title); label.getStyleClass().add("app-dialog-field-label");
            available.setPromptText("Add an active Case Date Type");
            available.setConverter(new StringConverter<>() {
                @Override public String toString(EffectiveCaseDateTypeDto value) { return value == null ? "" : value.name(); }
                @Override public EffectiveCaseDateTypeDto fromString(String value) { return null; }
            });
            Button add = button("Add", ControlStyles.Purpose.SECONDARY, this::add);
            remove = button("Remove", ControlStyles.Purpose.DANGER, this::remove);
            up = button("Move up", ControlStyles.Purpose.GHOST, () -> move(-1));
            down = button("Move down", ControlStyles.Purpose.GHOST, () -> move(1));
            save = button("Save", ControlStyles.Purpose.PRIMARY, () -> CaseDatePresentationManagementPane.this.save(this));
            Button reload = button("Reload", ControlStyles.Purpose.GHOST, () -> CaseDatePresentationManagementPane.this.reload(true));
            HBox picker = new HBox(8, available, add); picker.setAlignment(Pos.CENTER_LEFT);
            HBox actions = new HBox(8, remove, up, down, save, reload);
            root.getStyleClass().addAll("shale-section-surface", "management-card");
            root.getChildren().addAll(label, picker, rows, actions);
            refresh();
        }

        void load(CaseDatePresentationConfigurationDto value, List<EffectiveCaseDateTypeDto> eligible) {
            configuration = value; this.eligible = eligible == null ? List.of() : List.copyOf(eligible);
            working.clear(); working.addAll(value.selections()); selected = -1; refresh();
        }
        boolean dirty() { return configuration != null && !identities().equals(configuration.selections().stream().map(CaseDatePresentationSelectionDto::selectionIdentity).toList()); }
        List<String> identities() { return working.stream().map(CaseDatePresentationSelectionDto::selectionIdentity).toList(); }
        void add() { EffectiveCaseDateTypeDto type = available.getValue(); if (type == null) return; working.add(new CaseDatePresentationSelectionDto(identity(type), working.size(), type, false)); selected = working.size()-1; refresh(); }
        void remove() { if (selected < 0) return; working.remove(selected); selected = Math.min(selected, working.size()-1); refresh(); }
        void move(int amount) { int target=selected+amount; if(selected<0||target<0||target>=working.size())return; var row=working.remove(selected);working.add(target,row);selected=target;refresh(); }
        void refresh() {
            rows.getChildren().clear();
            for (int i=0;i<working.size();i++) {
                int index=i; var selection=working.get(i);
                Label row = new Label((i+1)+". "+selection.type().name()+(selection.historical()?" (historical — no longer active)":""));
                row.getStyleClass().addAll("management-collection-row", selection.historical()?"management-historical-row":"management-active-row");
                if(i==selected)row.getStyleClass().add("management-collection-row-selected");
                row.setMaxWidth(Double.MAX_VALUE); row.setOnMouseClicked(event->{selected=index;refresh();}); rows.getChildren().add(row);
            }
            if (working.isEmpty()) rows.getChildren().add(new Label("No dates selected. Saving will explicitly show no dates."));
            var used=identities(); available.getItems().setAll(eligible.stream().filter(t->!used.contains(identity(t))).toList()); available.getSelectionModel().clearSelection();
            remove.setDisable(selected<0);up.setDisable(selected<=0);down.setDisable(selected<0||selected>=working.size()-1);save.setDisable(!dirty());
        }
    }
}
