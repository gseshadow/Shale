package com.shale.ui.controller.support;

import com.shale.core.dto.CaseUpdateAuthorDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseUpdatePage;
import com.shale.core.dto.CaseUpdateQuery;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.UserSelectionField;
import com.shale.ui.component.UserSelector;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ControlStyles;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

/** One case-scoped, bounded workspace. It shares persistence/cards/editors with the quick-access rail. */
public final class CaseUpdatesWorkspaceController {
    private final long caseId;
    private final CaseServicePort service;
    private final AppState state;
    private final long identityRevision;
    private final Executor executor;
    private final Consumer<Runnable> dispatch;
    private final Function<CaseUpdateDto, Node> card;
    private final VBox root = new VBox(12);
    private final VBox rows = new VBox(8);
    private final Label status = new Label();
    private final Label pageLabel = new Label();
    private final Label authorStatus = new Label();
    private final TextField search = new TextField();
    private final ComboBox<CaseUpdateQuery.Sort> sort = new ComboBox<>();
    private final ComboBox<CaseUpdateQuery.Visibility> visibility = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker through = new DatePicker();
    private final UserSelectionField<CaseUpdateAuthorDto> author;
    private final Button previous;
    private final Button next;
    private final PauseTransition debounce = new PauseTransition(Duration.millis(350));
    private final List<CaseUpdateQuery.Cursor> cursors = new ArrayList<>();
    private CaseUpdateQuery.Cursor nextCursor;
    private int pageIndex;
    private volatile int generation;
    private volatile int authorGeneration;
    private volatile boolean active;
    private boolean stale = true;
    private boolean resetting;
    private boolean authorsLoaded;

    public CaseUpdatesWorkspaceController(long caseId, CaseServicePort service, AppState state, Executor executor,
            Function<CaseUpdateDto, Node> card, Runnable create) {
        this(caseId, service, state, executor, Platform::runLater, card, create);
    }

    /** Dispatch injection keeps asynchronous request ordering deterministic in regression tests. */
    public CaseUpdatesWorkspaceController(long caseId, CaseServicePort service, AppState state, Executor executor,
            Consumer<Runnable> dispatch, Function<CaseUpdateDto, Node> card, Runnable create) {
        this.caseId = caseId; this.service = service; this.state = state;
        this.identityRevision = state.sessionRevision(); this.executor = executor; this.dispatch = dispatch; this.card = card;
        root.getStyleClass().add("shale-content-plane");
        root.setPadding(new Insets(12)); root.setMinSize(0, 0);
        Label heading = new Label("Case Updates"); heading.getStyleClass().add("shale-section-title");
        Button add = action("New Update", ControlStyles.Purpose.PRIMARY, create);
        Button refresh = action("Refresh", ControlStyles.Purpose.SECONDARY, this::refresh);
        FlowPane headingBar = new FlowPane(8, 8, heading, add, refresh);
        search.setPromptText("Search update text…"); search.setAccessibleText("Search update text");
        search.setMaxWidth(Double.MAX_VALUE); ControlStyles.formControl(search);
        sort.getItems().setAll(CaseUpdateQuery.Sort.values()); sort.setValue(CaseUpdateQuery.Sort.NEWEST);
        visibility.getItems().setAll(CaseUpdateQuery.Visibility.values()); visibility.setValue(CaseUpdateQuery.Visibility.ACTIVE);
        for (Control control : List.of(sort, visibility, from, through)) ControlStyles.formControl(control);
        sort.setAccessibleText("Sort updates"); visibility.setAccessibleText("Active or deleted updates");
        from.setAccessibleText("Created from date"); through.setAccessibleText("Created through date, inclusive");
        author = new UserSelectionField<>(CaseUpdateAuthorDto::id, CaseUpdateAuthorDto::displayName,
                CaseUpdateAuthorDto::color, (field, candidates) -> java.util.Optional.empty(), true);
        author.useUnifiedControlStyles().useMultipleSelection((field, candidates) -> {
            UserSelector<CaseUpdateAuthorDto> selector = new UserSelector<>(CaseUpdateAuthorDto::id,
                    CaseUpdateAuthorDto::displayName, CaseUpdateAuthorDto::color);
            selector.useSemanticFormControl(); selector.useMultipleSelection(); selector.setCandidates(candidates);
            selector.setSelectedUsers(field.getSelectedUsers());
            selector.setPromptText("Search authors…");
            selector.setEmptyText("No matching authors available for this case.");
            Dialog<List<CaseUpdateAuthorDto>> picker = new Dialog<>();
            if (root.getScene() != null) picker.initOwner(root.getScene().getWindow());
            AppDialogs.applySecondaryDialogShell(picker, "Filter by authors");
            picker.setResizable(true);
            picker.getDialogPane().setContent(selector);
            picker.getDialogPane().setPrefSize(560, 480);
            picker.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
            ControlStyles.apply((Button) picker.getDialogPane().lookupButton(ButtonType.APPLY), ControlStyles.Purpose.PRIMARY);
            ControlStyles.apply((Button) picker.getDialogPane().lookupButton(ButtonType.CANCEL), ControlStyles.Purpose.SECONDARY);
            picker.setResultConverter(button -> button == ButtonType.APPLY ? List.copyOf(selector.getSelectedUsers()) : null);
            return picker.showAndWait();
        }, "authors");
        author.setAccessibleText("Filter by update author; empty means all authors");
        authorStatus.setWrapText(true); authorStatus.getStyleClass().add("shale-error-message");
        authorStatus.setVisible(false); authorStatus.setManaged(false);
        VBox authorField = field("Author (all when empty)", author);
        authorField.getChildren().add(authorStatus);
        FlowPane filters = new FlowPane(12, 8, field("Sort", sort), field("Created from", from),
                field("Created through", through), authorField);
        if (state.isAdmin()) filters.getChildren().add(field("Records", visibility));
        Button clear = action("Clear filters", ControlStyles.Purpose.GHOST, this::resetFilters);
        filters.getChildren().add(clear);
        filters.getStyleClass().add("shale-section-card");
        ScrollPane scroll = new ScrollPane(rows); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("transparent-scroll"); scroll.setMinSize(0, 0); VBox.setVgrow(scroll, Priority.ALWAYS);
        status.setWrapText(true); status.getStyleClass().add("shale-metadata"); status.setAccessibleText("Case Updates status");
        previous = action("Previous", ControlStyles.Purpose.SECONDARY, () -> { if (pageIndex > 0) { pageIndex--; load(); } });
        next = action("Next", ControlStyles.Purpose.SECONDARY, () -> {
            if (nextCursor == null) return;
            while (cursors.size() > pageIndex + 1) cursors.removeLast();
            cursors.add(nextCursor); pageIndex++; load();
        });
        previous.setDisable(true); next.setDisable(true);
        HBox paging = new HBox(8, previous, pageLabel, next);
        root.getChildren().setAll(headingBar, search, filters, status, scroll, paging);
        cursors.add(null);
        debounce.setOnFinished(event -> filtersChanged());
        search.textProperty().addListener((o, a, b) -> { if (!resetting) { invalidate(); debounce.playFromStart(); } });
        sort.valueProperty().addListener((o, a, b) -> filtersChanged());
        visibility.valueProperty().addListener((o, a, b) -> filtersChanged());
        from.valueProperty().addListener((o, a, b) -> filtersChanged());
        through.valueProperty().addListener((o, a, b) -> filtersChanged());
        author.selectedUsersProperty().addListener((o, a, b) -> filtersChanged());
    }

    private static VBox field(String text, Node control) {
        Label label = new Label(text); label.getStyleClass().add("shale-field-label"); label.setLabelFor(control);
        return new VBox(4, label, control);
    }
    private static Button action(String text, ControlStyles.Purpose purpose, Runnable action) {
        return ActionButtonFactory.semantic(text, event -> action.run(), purpose, ControlStyles.Size.SMALL);
    }
    public VBox view() { return root; }
    public long caseId() { return caseId; }
    public boolean isCurrentSession() { return state.sessionRevision() == identityRevision; }
    public void dataChanged() {
        authorsLoaded = false; authorGeneration++; invalidate();
        if (active) { loadAuthors(); refresh(); }
    }
    public void activate() {
        active = true;
        if (!authorsLoaded) loadAuthors();
        if (stale) refresh();
    }
    public void deactivate() { active = false; debounce.stop(); invalidate(); }
    public void invalidate() { generation++; stale = true; previous.setDisable(true); next.setDisable(true); }
    public void refresh() {
        if (!authorsLoaded && !author.isLoading()) loadAuthors();
        filtersChanged();
    }
    public void resetFilters() {
        resetting = true;
        try {
            search.clear(); sort.setValue(CaseUpdateQuery.Sort.NEWEST); visibility.setValue(CaseUpdateQuery.Visibility.ACTIVE);
            from.setValue(null); through.setValue(null); author.clearSelection();
        } finally { resetting = false; }
        filtersChanged();
    }
    private void filtersChanged() {
        if (resetting) return;
        debounce.stop(); invalidate(); pageIndex = 0; cursors.clear(); cursors.add(null);
        if (active) load();
    }
    private boolean current(int request) {
        return active && request == generation && state.sessionRevision() == identityRevision;
    }
    private void load() {
        final CaseUpdateQuery query;
        int request = ++generation;
        next.setDisable(true); previous.setDisable(true); rows.getChildren().clear();
        try {
            query = new CaseUpdateQuery(caseId, state.getShaleClientId(), state.getUserId(), search.getText(), sort.getValue(),
                    from.getValue(), through.getValue(), author.getSelectedUsers().stream().map(CaseUpdateAuthorDto::id).collect(java.util.stream.Collectors.toSet()),
                    visibility.getValue(), cursors.get(pageIndex), 50).inZone(java.time.ZoneId.systemDefault());
        } catch (RuntimeException ex) { status.setText("Check the date range and search (maximum 1000 characters)."); return; }
        status.setText("Loading updates…");
        executor.execute(() -> {
            if (!current(request)) return;
            try {
                CaseUpdatePage page = service.searchCaseUpdates(query);
                dispatch.accept(() -> {
                    if (!current(request)) return;
                    stale = false; nextCursor = page.next();
                    rows.getChildren().setAll(page.items().stream().map(card).toList());
                    boolean filtered = !query.text().isBlank() || query.from() != null || query.through() != null
                            || !query.authorUserIds().isEmpty() || query.visibility() != CaseUpdateQuery.Visibility.ACTIVE;
                    status.setText(page.items().isEmpty() ? (filtered ? "No updates match these filters." : "No updates yet. Create the first update.")
                            : page.items().size() + " updates on this page");
                    pageLabel.setText("Page " + (pageIndex + 1));
                    previous.setDisable(pageIndex == 0); next.setDisable(nextCursor == null);
                });
            } catch (RuntimeException ex) {
                dispatch.accept(() -> { if (current(request)) { stale = true; status.setText("Updates could not be loaded. Refresh to try again."); } });
            }
        });
    }
    private void loadAuthors() {
        final Integer tenant = state.getShaleClientId(), actor = state.getUserId();
        if (tenant == null || actor == null) return;
        final int request = ++authorGeneration;
        author.setLoading(true);
        authorStatus.setVisible(false); authorStatus.setManaged(false);
        executor.execute(() -> {
            try {
                if (request != authorGeneration || state.sessionRevision() != identityRevision) return;
                List<CaseUpdateAuthorDto> authors = service.listCaseUpdateAuthors(caseId, tenant, actor);
                dispatch.accept(() -> {
                    if (request != authorGeneration || state.sessionRevision() != identityRevision) return;
                    author.setCandidates(authors); author.setLoading(false); authorsLoaded = true;
                });
            } catch (RuntimeException ex) {
                dispatch.accept(() -> { if (request == authorGeneration && state.sessionRevision() == identityRevision) {
                    author.setLoading(false);
                    authorStatus.setText("Authors could not be loaded. Refresh to retry.");
                    authorStatus.setVisible(true); authorStatus.setManaged(true);
                } });
            }
        });
    }
}
