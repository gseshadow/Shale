package com.shale.ui.controller;

import com.shale.data.dao.AuditLogDao;
import com.shale.data.dao.EntityActionAuditDao;
import com.shale.data.dao.EntityActionAuditViewerRow;
import com.shale.data.dao.UserDao;
import com.shale.core.runtime.DbSessionProvider;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.services.LiveUpdateEvents;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.state.AppState;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public final class AuditLogViewerController {
    private static final int VIEWER_LIMIT = 500;
    private static final ZoneId DISPLAY_ZONE = ZoneId.systemDefault();
    private static final ExecutorService AUDIT_EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        private final java.util.concurrent.atomic.AtomicInteger ids = new java.util.concurrent.atomic.AtomicInteger();
        @Override public Thread newThread(Runnable r) { Thread t = new Thread(r, "audit-viewer-" + ids.incrementAndGet()); t.setDaemon(true); return t; }
    });

    enum ViewerMode { ALL("All"), PHI_AUDIT("PHI Audit"), ENTITY_ACTIVITY("Entity Activity"); final String label; ViewerMode(String label){this.label=label;} @Override public String toString(){return label;} }
    enum RowSource { PHI_FIELD_AUDIT, ENTITY_ACTIVITY }
    record AuditViewerRow(RowSource source, LocalDateTime occurredLocal, java.time.Instant sortInstant, String actor, String category, String heading, String detail, String context, long sourceId) {}
    record ParsedFilters(Integer userId, Integer objectTypeId, Long objectId, String fieldName, LocalDate startDate, LocalDate endDate) {}

    @FXML private ComboBox<ViewerMode> modeComboBox;
    @FXML private TextField userIdFilterField;
    @FXML private TextField objectTypeIdFilterField;
    @FXML private TextField objectIdFilterField;
    @FXML private TextField fieldNameFilterField;
    @FXML private TextField startDateFilterField;
    @FXML private TextField endDateFilterField;
    @FXML private Label statusLabel;
    @FXML private TableView<AuditViewerRow> auditTable;
    @FXML private TableColumn<AuditViewerRow, LocalDateTime> entryDateColumn;
    @FXML private TableColumn<AuditViewerRow, String> userIdColumn;
    @FXML private TableColumn<AuditViewerRow, String> objectTypeIdColumn;
    @FXML private TableColumn<AuditViewerRow, Long> objectIdColumn;
    @FXML private TableColumn<AuditViewerRow, String> fieldNameColumn;
    @FXML private TableColumn<AuditViewerRow, String> actionColumn;
    @FXML private TableColumn<AuditViewerRow, String> fieldCodeColumn;
    @FXML private TableColumn<AuditViewerRow, String> stringValueColumn;
    @FXML private TableColumn<AuditViewerRow, LocalDate> dateValueColumn;
    @FXML private TableColumn<AuditViewerRow, Boolean> booleanValueColumn;
    @FXML private TableColumn<AuditViewerRow, Integer> intValueColumn;

    private AppState appState;
    private AuditLogDao auditLogDao;
    private EntityActionAuditDao entityActionAuditDao;
    private UserDao userDao;
    private DbSessionProvider dbSessionProvider;
    private UiRuntimeBridge runtimeBridge;
    private final java.util.function.Consumer<UiRuntimeBridge.EntityUpdatedEvent> auditLiveHandler = this::handleAuditLiveEvent;
    private final Map<Integer, String> userDisplayNamesById = new HashMap<>();
    private final AtomicLong requestGeneration = new AtomicLong();
    private boolean fxmlReady;
    private boolean initialLoadPending;

    @FXML private void initialize() {
        fxmlReady = true;
        modeComboBox.setItems(FXCollections.observableArrayList(ViewerMode.ALL, ViewerMode.PHI_AUDIT, ViewerMode.ENTITY_ACTIVITY));
        modeComboBox.getSelectionModel().select(ViewerMode.ALL);
        modeComboBox.valueProperty().addListener((obs, oldMode, newMode) -> { updateFilterCompatibility(); if (oldMode != newMode && isAdminUser()) loadAuditRows(); });
        configureColumns(); configureTableReadability(); configureFilterFieldActions(); runInitialLoadIfReady();
    }

    public void init(AppState appState, AuditLogDao auditLogDao, EntityActionAuditDao entityActionAuditDao, UserDao userDao, DbSessionProvider dbSessionProvider, UiRuntimeBridge runtimeBridge) {
        this.appState = Objects.requireNonNull(appState, "appState");
        this.auditLogDao = Objects.requireNonNull(auditLogDao, "auditLogDao");
        this.entityActionAuditDao = Objects.requireNonNull(entityActionAuditDao, "entityActionAuditDao");
        this.userDao = Objects.requireNonNull(userDao, "userDao");
        this.dbSessionProvider = Objects.requireNonNull(dbSessionProvider, "dbSessionProvider");
        this.runtimeBridge = runtimeBridge;
        if (this.runtimeBridge != null) this.runtimeBridge.subscribeEntityUpdated(auditLiveHandler);
        initialLoadPending = true; runInitialLoadIfReady();
    }

    private void handleAuditLiveEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
        if (event == null || appState == null || !LiveUpdateEvents.ENTITY_AUDIT_ACTIVITY.equals(event.entityType())) return;
        Integer tenantId = appState.getShaleClientId();
        if (tenantId == null || event.shaleClientId() != tenantId || !appState.isAdmin()) return;
        ViewerMode selectedMode = mode();
        if (selectedMode == ViewerMode.PHI_AUDIT) return;
        Platform.runLater(this::loadAuditRows);
    }

    private void runInitialLoadIfReady() { if (!fxmlReady || !initialLoadPending) return; initialLoadPending = false; if (!appState.isAdmin()) { auditTable.setItems(FXCollections.emptyObservableList()); setStatus("Only admin users can view audit logs."); return; } loadAuditRows(); }
    @FXML private void onApplyFilters() { if (isAdminUser()) loadAuditRows(); }
    @FXML private void onClearFilters() { for (TextField f : List.of(userIdFilterField, objectTypeIdFilterField, objectIdFilterField, fieldNameFilterField, startDateFilterField, endDateFilterField)) f.clear(); updateFilterCompatibility(); if (isAdminUser()) loadAuditRows(); }

    private void configureColumns() {
        entryDateColumn.setText("Occurred"); entryDateColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().occurredLocal()));
        userIdColumn.setText("Actor"); userIdColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().actor()));
        objectTypeIdColumn.setText("Category"); objectTypeIdColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().category()));
        objectIdColumn.setText("Record Id"); objectIdColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().sourceId()));
        fieldNameColumn.setText("Summary"); fieldNameColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().heading()));
        actionColumn.setText("Action"); actionColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().source() == RowSource.ENTITY_ACTIVITY ? c.getValue().heading() : c.getValue().detail()));
        fieldCodeColumn.setText("Context"); fieldCodeColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().context()));
        stringValueColumn.setText("Detail"); stringValueColumn.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().detail()));
        dateValueColumn.setVisible(false); booleanValueColumn.setVisible(false); intValueColumn.setVisible(false);
    }
    private void configureTableReadability() { auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); auditTable.setPlaceholder(new Label("No audit activity matches the current filters.")); }
    private void configureFilterFieldActions() { for (TextField f : List.of(userIdFilterField, objectTypeIdFilterField, objectIdFilterField, fieldNameFilterField, startDateFilterField, endDateFilterField)) f.setOnAction(e -> onApplyFilters()); }
    private void updateFilterCompatibility() { boolean entityOnly = mode() == ViewerMode.ENTITY_ACTIVITY; objectTypeIdFilterField.setDisable(entityOnly); objectIdFilterField.setDisable(entityOnly); fieldNameFilterField.setDisable(entityOnly); if (entityOnly) { objectTypeIdFilterField.clear(); objectIdFilterField.clear(); fieldNameFilterField.clear(); } }
    private ViewerMode mode() { return modeComboBox == null || modeComboBox.getValue() == null ? ViewerMode.ALL : modeComboBox.getValue(); }

    private void loadAuditRows() {
        Integer tenant = appState == null ? null : appState.getShaleClientId(); if (tenant == null || tenant <= 0) { auditTable.setItems(FXCollections.emptyObservableList()); setStatus("No audit activity matches the current filters."); return; }
        ParsedFilters filters = parseFilters(mode()); if (filters == null) return;
        long generation = requestGeneration.incrementAndGet(); ViewerMode selectedMode = mode(); auditTable.setItems(FXCollections.emptyObservableList()); setStatus("Loading " + selectedMode.label + "...");
        CompletableFuture.supplyAsync(() -> loadRowsForMode(tenant, selectedMode, filters), AUDIT_EXECUTOR).whenComplete((result, error) -> Platform.runLater(() -> {
            if (generation != requestGeneration.get()) return;
            if (error != null) { error.printStackTrace(); auditTable.setPlaceholder(new Label("Failed to load audit activity. Use Refresh/Apply to retry.")); setStatus("Failed to load " + selectedMode.label + "."); return; }
            auditTable.setItems(FXCollections.observableArrayList(result)); auditTable.setPlaceholder(new Label(emptyCopy(selectedMode))); setStatus(result.isEmpty() ? emptyCopy(selectedMode) : result.size() + " audit entries");
        }));
    }

    private List<AuditViewerRow> loadRowsForMode(int tenant, ViewerMode selectedMode, ParsedFilters f) {
        loadUserDisplayNames(tenant); List<AuditViewerRow> rows = new ArrayList<>();
        if (selectedMode != ViewerMode.ENTITY_ACTIVITY) rows.addAll(auditLogDao.listAuditLogEntries(tenant, f.userId, f.objectId, f.fieldName, f.objectTypeId, f.startDate, f.endDate, VIEWER_LIMIT).stream().map(this::fromPhi).toList());
        if (selectedMode != ViewerMode.PHI_AUDIT) rows.addAll(entityActionAuditDao.listViewerRows(dbSessionProvider, tenant, f.userId, f.startDate, f.endDate, VIEWER_LIMIT).stream().map(this::fromEntity).toList());
        rows.sort(Comparator.comparing(AuditViewerRow::sortInstant, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(r -> r.source().name()).thenComparing(AuditViewerRow::sourceId, Comparator.reverseOrder()));
        return rows.size() > VIEWER_LIMIT ? new ArrayList<>(rows.subList(0, VIEWER_LIMIT)) : rows;
    }

    private AuditViewerRow fromPhi(AuditLogDao.AuditLogEntryRow r) { LocalDateTime dt = r.entryDate(); return new AuditViewerRow(RowSource.PHI_FIELD_AUDIT, dt, dt == null ? java.time.Instant.EPOCH : dt.atZone(DISPLAY_ZONE).toInstant(), formatUserDisplay(r.userId()), "PHI Audit", r.fieldName(), deriveAction(r), "ObjectType " + r.objectTypeId() + " / Object #" + r.objectId(), r.objectId() == null ? 0 : r.objectId()); }
    private AuditViewerRow fromEntity(EntityActionAuditViewerRow r) { LocalDateTime local = LocalDateTime.ofInstant(r.occurredAtUtc(), DISPLAY_ZONE); String entity = friendlyEntity(r.entityType()); return new AuditViewerRow(RowSource.ENTITY_ACTIVITY, local, r.occurredAtUtc(), r.actorDisplayName(), "Entity Activity", entityHeading(r, entity), entityDetail(r), parentContext(r), r.auditEventId()); }
    private String entityHeading(EntityActionAuditViewerRow r, String entity) { String action = friendlyAction(r.entityType(), r.action()); if ("PRIMARY_SET".equals(r.action())) return "Primary Link changed"; if ("OVERRIDE_RESET".equals(r.action())) return "Tenant customization reset for " + entity + " #" + r.entityId(); if ("CASE_LINK_SHARE".equals(r.entityType()) && r.safeMetadata().containsKey("CONTACT_ID")) return "Contact #" + r.safeMetadata().get("CONTACT_ID") + " " + switch (r.action()) { case "ADDED" -> "shared with"; case "REMOVED" -> "unshared from"; default -> "updated for"; } + " Case Link #" + r.safeMetadata().getOrDefault("CASE_LINK_ID", String.valueOf(r.parentEntityId())); return entity + " #" + r.entityId() + " " + action.toLowerCase(Locale.ROOT); }
    private String entityDetail(EntityActionAuditViewerRow r) { return friendlyAction(r.entityType(), r.action()) + safeSource(r.source()); }
    private String parentContext(EntityActionAuditViewerRow r) { if (r.safeMetadata().containsKey("CASE_ID")) return "Case #" + r.safeMetadata().get("CASE_ID"); if (r.parentEntityType() != null && r.parentEntityId() != null) return friendlyEntity(r.parentEntityType()) + " #" + r.parentEntityId(); return ""; }
    private static String safeSource(String source) { if (source == null || source.isBlank()) return ""; return " • " + switch (source) { case "SHALE_DESKTOP" -> "Desktop"; case "API" -> "API"; case "SYSTEM" -> "System"; default -> "Source"; }; }
    static String friendlyEntity(String entityType) { return switch (entityType == null ? "" : entityType) { case "CASE" -> "Case"; case "CASE_STATUS" -> "Case Status"; case "LINK_TYPE" -> "Link Type"; case "CASE_LINK" -> "Case Link"; case "CASE_LINK_SHARE" -> "Shared Contact"; case "CASE_DATE" -> "Case Date"; case "CASE_DATE_TYPE" -> "Case Date Type"; case "CALENDAR_EVENT" -> "Calendar Event"; case "CASE_DATE_ROLE_MAPPING" -> "Case Date Role Mapping"; case "FORM_CONFIGURATION" -> "Form Configuration"; case "MATERIAL_TYPE" -> "Material Type"; case "MATERIAL_REQUEST" -> "Material Request"; case "MATERIAL_REQUEST_FOLLOW_UP" -> "Material Request Follow-up"; case "MATERIAL_ITEM" -> "Material Item"; case "USER" -> "User"; default -> "Entity (" + (entityType == null ? "unknown" : entityType) + ")"; }; }
    static String friendlyAction(String entityType, String action) { return switch ((entityType == null ? "" : entityType) + ":" + (action == null ? "" : action)) { case "LINK_TYPE:OVERRIDE_CREATED" -> "Tenant customization created"; case "LINK_TYPE:ACTIVATED" -> "Activated"; case "LINK_TYPE:DEACTIVATED" -> "Deactivated"; case "LINK_TYPE:OVERRIDE_RESET" -> "Tenant customization reset"; case "CASE_LINK:PRIMARY_SET" -> "Primary Link changed"; case "CASE_LINK:REORDERED" -> "Links reordered"; case "CASE_LINK_SHARE:ADDED" -> "Contact shared"; case "CASE_LINK_SHARE:UPDATED" -> "Sharing details updated"; case "CASE_LINK_SHARE:REMOVED" -> "Contact unshared"; case "MATERIAL_REQUEST:STATUS_CHANGED" -> "Status changed"; case "MATERIAL_REQUEST:FOLLOW_UP_ADDED" -> "Follow-up added"; case "MATERIAL_ITEM:LINKED" -> "Linked"; case "MATERIAL_ITEM:UNLINKED" -> "Unlinked"; case "MATERIAL_ITEM:LOCATION_UPDATED" -> "Location updated"; case "MATERIAL_ITEM:RELEASED" -> "Released"; default -> switch (action == null ? "" : action) { case "CREATED" -> "Created"; case "UPDATED" -> "Updated"; case "DELETED" -> "Deleted"; case "RESTORED" -> "Restored"; default -> "Action (" + (action == null ? "unknown" : action) + ")"; }; }; }

    private ParsedFilters parseFilters(ViewerMode selectedMode) { Integer userId = parseOptionalInt(userIdFilterField, "UserId"); if (userId == null && hasText(userIdFilterField.getText())) return null; Integer objectTypeId = selectedMode == ViewerMode.ENTITY_ACTIVITY ? null : parseOptionalInt(objectTypeIdFilterField, "ObjectTypeId"); if (objectTypeId == null && selectedMode != ViewerMode.ENTITY_ACTIVITY && hasText(objectTypeIdFilterField.getText())) return null; Long objectId = selectedMode == ViewerMode.ENTITY_ACTIVITY ? null : parseOptionalLong(objectIdFilterField, "ObjectId"); if (objectId == null && selectedMode != ViewerMode.ENTITY_ACTIVITY && hasText(objectIdFilterField.getText())) return null; LocalDate startDate = parseOptionalDate(startDateFilterField, "StartDate"); if (startDate == null && hasText(startDateFilterField.getText())) return null; LocalDate endDate = parseOptionalDate(endDateFilterField, "EndDate"); if (endDate == null && hasText(endDateFilterField.getText())) return null; return new ParsedFilters(userId, objectTypeId, objectId, selectedMode == ViewerMode.ENTITY_ACTIVITY ? null : trimToNull(fieldNameFilterField.getText()), startDate, endDate); }
    private void loadUserDisplayNames(int tenant) { if (userDao == null || !userDisplayNamesById.isEmpty()) return; try { for (UserDao.DirectoryUserRow u : userDao.listUsersForTenant(tenant)) if (u != null && u.id() > 0 && trimToNull(u.displayName()) != null) userDisplayNamesById.put(u.id(), trimToNull(u.displayName())); } catch (RuntimeException ignored) {} }
    private String formatUserDisplay(Integer userId) { if (userId == null || userId <= 0) return ""; String n = userDisplayNamesById.get(userId); return n == null ? "User #" + userId : n + " (#" + userId + ")"; }
    private boolean isAdminUser() { if (appState == null || appState.isAdmin()) return true; AppDialogs.showError(dialogOwner(), "Audit Log", "Only admin users can view audit logs."); return false; }
    private Integer parseOptionalInt(TextField f, String label) { String v = f == null ? null : trimToNull(f.getText()); if (v == null) return null; try { return Integer.valueOf(v); } catch (NumberFormatException ex) { AppDialogs.showError(dialogOwner(), "Audit Log", label + " must be a whole number."); return null; } }
    private Long parseOptionalLong(TextField f, String label) { String v = f == null ? null : trimToNull(f.getText()); if (v == null) return null; try { return Long.valueOf(v); } catch (NumberFormatException ex) { AppDialogs.showError(dialogOwner(), "Audit Log", label + " must be a whole number."); return null; } }
    private LocalDate parseOptionalDate(TextField f, String label) { String v = f == null ? null : trimToNull(f.getText()); if (v == null) return null; try { return LocalDate.parse(v); } catch (DateTimeParseException ex) { AppDialogs.showError(dialogOwner(), "Audit Log", label + " must use YYYY-MM-DD."); return null; } }
    private void setStatus(String m) { if (statusLabel != null) statusLabel.setText(m == null ? "" : m); }
    private String emptyCopy(ViewerMode m) { return switch (m) { case PHI_AUDIT -> "No PHI audit records match the current filters."; case ENTITY_ACTIVITY -> "No entity activity matches the current filters."; default -> "No audit activity matches the current filters."; }; }
    private Window dialogOwner() { return auditTable == null || auditTable.getScene() == null ? null : auditTable.getScene().getWindow(); }
    private static boolean hasText(String v) { return v != null && !v.isBlank(); }
    private static String trimToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private static String deriveAction(AuditLogDao.AuditLogEntryRow row) { String p = row == null ? null : row.stringValue(); if (p == null || p.isBlank()) return ""; String oldValue = extractTokenValue(p, "old="); String newValue = extractTokenValue(p, "new="); if (oldValue == null || newValue == null) return p.toLowerCase(Locale.ROOT).contains("action=read") ? "READ" : ""; boolean oldNull = "null".equalsIgnoreCase(oldValue.trim()); boolean newNull = "null".equalsIgnoreCase(newValue.trim()); if (oldNull && !newNull) return "CREATE"; if (!oldNull && newNull) return "DELETE"; return !oldNull && !newNull && !oldValue.equals(newValue) ? "UPDATE" : ""; }
    private static String extractTokenValue(String payload, String token) { int s = payload.indexOf(token); if (s < 0) return null; s += token.length(); int e = payload.indexOf(';', s); return payload.substring(s, e < 0 ? payload.length() : e).trim(); }
}
