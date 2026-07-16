package com.shale.ui.controller;

import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.dto.LinkTypeDto;
import com.shale.core.service.CaseServicePort;
import com.shale.data.dao.UserDao;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.notification.NotificationPreferenceKey;
import com.shale.ui.notification.NotificationPreferences;
import com.shale.ui.notification.NotificationPreferencesService;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ActionButtonFactory;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Button;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.shape.Circle;
import com.shale.ui.util.ColorUtil;
import com.shale.ui.component.factory.StatusIndicatorFactory;
import com.shale.ui.component.factory.PracticeAreaIndicatorFactory;
import com.shale.ui.component.factory.LinkTypeIndicatorFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SettingsController {
	@FXML
	private CheckBox taskAssignedToMeCheck;
	@FXML
	private CheckBox taskOverdueCheck;
	@FXML
	private CheckBox taskDueTodayCheck;
	@FXML
	private CheckBox taskDueTomorrowCheck;
	@FXML
	private CheckBox appUpdatesCheck;
	@FXML
	private CheckBox connectivityCheck;
	@FXML
	private CheckBox taskOverdueBannerCheck;
	@FXML
	private CheckBox taskDueTodayBannerCheck;
	@FXML
	private CheckBox appUpdatesBannerCheck;
	@FXML
	private CheckBox connectivityBannerCheck;
	@FXML
	private Label notificationSettingsStatusLabel;
	@FXML
	private VBox auditSection;
	@FXML
	private VBox caseStatusAdministrationSection;
	@FXML
	private VBox caseStatusCardsContainer;
	@FXML
	private HBox caseStatusActionRow;
	@FXML
	private Label caseStatusSettingsStatusLabel;
	@FXML
	private VBox practiceAreaAdministrationSection;
	@FXML
	private VBox practiceAreaCardsContainer;
	@FXML
	private HBox practiceAreaActionRow;
	@FXML
	private Label practiceAreaSettingsStatusLabel;
	@FXML
	private VBox linkTypeAdministrationSection;
	@FXML
	private VBox linkTypeCardsContainer;
	@FXML
	private HBox linkTypeActionRow;
	@FXML
	private Label linkTypeSettingsStatusLabel;
	@FXML
	private TableView<UserManagementViewRow> userManagementTable;
	@FXML
	private TableColumn<UserManagementViewRow, String> userNameColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userEmailColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userInitialsColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userAttorneyColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userAdminColumn;
	@FXML
	private TableColumn<UserManagementViewRow, String> userStatusColumn;
	@FXML
	private CheckBox showInactiveUsersCheck;
	@FXML
	private Button deactivateUserButton;
	@FXML
	private Button reactivateUserButton;
	@FXML
	private Button resetPasswordButton;
	@FXML
	private Label userManagementStatusLabel;

	private NotificationPreferencesService notificationPreferencesService;
	private AppState appState;
	private CaseServicePort caseService;
	private UserDao userDao;
	private Runnable onOpenAuditLog;
	private boolean fxmlReady;
	private final List<CaseStatusViewRow> caseStatusRows = new ArrayList<>();
	private final List<PracticeAreaViewRow> practiceAreaRows = new ArrayList<>();
	private final List<LinkTypeViewRow> linkTypeRows = new ArrayList<>();
	private CaseStatusViewRow selectedCaseStatusRow;
	private PracticeAreaViewRow selectedPracticeAreaRow;
	private LinkTypeViewRow selectedLinkTypeRow;
	private int caseStatusLoadGeneration;
	private int practiceAreaLoadGeneration;
	private int linkTypeLoadGeneration;
	private int userManagementLoadGeneration;

	private final ExecutorService settingsLoadExecutor = Executors.newFixedThreadPool(4, runnable -> {
		Thread thread = new Thread(runnable, "settings-section-loader");
		thread.setDaemon(true);
		return thread;
	});

	@FXML
	private void initialize() {
		fxmlReady = true;
		configureLookupActionRows();
		configureUserManagementTable();
		updateAdminControlsVisibility();
		if (notificationPreferencesService != null) {
			loadFromPreferences();
		}
		loadAdminSectionsAsync();
	}

	public void init(NotificationPreferencesService notificationPreferencesService, AppState appState, Runnable onOpenAuditLog, CaseServicePort caseService, UserDao userDao) {
		this.notificationPreferencesService = Objects.requireNonNull(notificationPreferencesService, "notificationPreferencesService");
		this.appState = Objects.requireNonNull(appState, "appState");
		this.onOpenAuditLog = Objects.requireNonNull(onOpenAuditLog, "onOpenAuditLog");
		this.caseService = Objects.requireNonNull(caseService, "caseService");
		this.userDao = Objects.requireNonNull(userDao, "userDao");
		if (fxmlReady) {
			loadFromPreferences();
			updateAdminControlsVisibility();
			loadAdminSectionsAsync();
		}
	}

	@FXML
	private void onApplyNotificationPreferences() {
		if (notificationPreferencesService == null) {
			return;
		}
		NotificationPreferences preferences = notificationPreferencesService.getForCurrentUser();
		Map<NotificationPreferenceKey, Boolean> selected = selectedValues();
		for (Map.Entry<NotificationPreferenceKey, Boolean> entry : selected.entrySet()) {
			preferences = preferences.withEnabled(entry.getKey(), entry.getValue());
		}
		notificationPreferencesService.setForCurrentUser(preferences);
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("Notification settings applied for this session.");
		}
	}

	@FXML
	private void onResetNotificationPreferences() {
		loadFromPreferences();
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("Notification settings reset to saved values.");
		}
	}

	@FXML
	private void onViewAuditLog() {
		if (!isAdminUser() || onOpenAuditLog == null) {
			return;
		}
		onOpenAuditLog.run();
	}

	private void loadAdminSectionsAsync() {
		if (!fxmlReady || !isAdminUser()) return;
		loadCaseStatusesAsync(null);
		loadPracticeAreasAsync(null);
		loadLinkTypesAsync(null);
		loadManagedUsersAsync(null);
	}

	private void setCaseStatusLoadingState(String message) {
		if (caseStatusCardsContainer != null) caseStatusCardsContainer.getChildren().setAll(loadingLabel(message));
		setCaseStatusMessage(message);
	}

	private void applyCaseStatusRows(int generation, List<CaseStatusViewRow> rows, String successMessage) {
		if (generation != caseStatusLoadGeneration) return;
		Integer selectedId = selectedCaseStatusRow == null ? null : selectedCaseStatusRow.id();
		caseStatusRows.clear();
		caseStatusRows.addAll(rows);
		selectedCaseStatusRow = rows.stream()
				.filter(row -> selectedId != null && row.id() == selectedId)
				.findFirst()
				.orElse(null);
		renderCaseStatusCards();
		setCaseStatusMessage(successMessage != null && !successMessage.isBlank() ? successMessage : rows.isEmpty() ? "No case statuses are configured for this tenant." : "");
	}

	private void setPracticeAreaLoadingState(String message) {
		if (practiceAreaCardsContainer != null) practiceAreaCardsContainer.getChildren().setAll(loadingLabel(message));
		setPracticeAreaMessage(message);
	}

	private void applyPracticeAreaRows(int generation, List<PracticeAreaViewRow> rows, String successMessage) {
		if (generation != practiceAreaLoadGeneration) return;
		Integer selectedId = selectedPracticeAreaRow == null ? null : selectedPracticeAreaRow.id();
		practiceAreaRows.clear();
		practiceAreaRows.addAll(rows);
		selectedPracticeAreaRow = rows.stream()
				.filter(row -> selectedId != null && row.id() == selectedId)
				.findFirst()
				.orElse(null);
		renderPracticeAreaCards();
		setPracticeAreaMessage(successMessage != null && !successMessage.isBlank() ? successMessage : rows.isEmpty() ? "No practice areas are configured for this tenant." : "");
	}

	private Label loadingLabel(String message) {
		Label label = new Label(message);
		label.getStyleClass().add("search-summary-text");
		label.setWrapText(true);
		return label;
	}

	private void configureLookupActionRows() {
		if (caseStatusActionRow != null) {
			caseStatusActionRow.getChildren().setAll(
					ActionButtonFactory.primary("Add Status", event -> onAddCaseStatus()),
					ActionButtonFactory.neutral("Edit Status", event -> onEditCaseStatus()),
					ActionButtonFactory.neutral("Move Up", event -> onMoveCaseStatusUp()),
					ActionButtonFactory.neutral("Move Down", event -> onMoveCaseStatusDown()),
					caseStatusSettingsStatusLabel);
		}
		if (linkTypeActionRow != null) {
			linkTypeActionRow.getChildren().setAll(
					ActionButtonFactory.primary("Add Link Type", event -> onAddLinkType()),
					ActionButtonFactory.neutral("Edit/Customize", event -> onEditLinkType()),
					ActionButtonFactory.neutral("Activate/Deactivate", event -> onToggleLinkTypeActive()),
					ActionButtonFactory.neutral("Reset/Remove", event -> onResetOrRemoveLinkType()),
					linkTypeSettingsStatusLabel);
		}
		if (practiceAreaActionRow != null) {
			practiceAreaActionRow.getChildren().setAll(
					ActionButtonFactory.primary("Add Practice Area", event -> onAddPracticeArea()),
					ActionButtonFactory.neutral("Edit Practice Area", event -> onEditPracticeArea()),
					ActionButtonFactory.neutral("Remove Practice Area", event -> onRemovePracticeArea()),
					practiceAreaSettingsStatusLabel);
		}
	}


	@FXML
	private void onAddLinkType() {
		if (!requireAdminLookupManagement("Link Types")) return;
		showLinkTypeDialog(null).ifPresent(input -> {
			try {
				caseService.createLinkType(new CaseServicePort.LinkTypeCommand(null, requireTenantId(), requireActorUserId(), input.name(), input.color(), input.active(), input.systemKey(), null));
				loadLinkTypesAsync("Link type added.");
			} catch (RuntimeException ex) { showLinkTypeError(ex); }
		});
	}

	@FXML
	private void onEditLinkType() {
		if (!requireAdminLookupManagement("Link Types")) return;
		LinkTypeViewRow selected = selectedLinkTypeRow();
		if (selected == null) return;
		showLinkTypeDialog(selected.linkType()).ifPresent(input -> {
			try {
				caseService.updateLinkType(new CaseServicePort.LinkTypeCommand(selected.id(), requireTenantId(), requireActorUserId(), input.name(), input.color(), input.active(), linkTypeSystemKeyForSave(selected.linkType()), selected.rowVer()));
				loadLinkTypesAsync(selected.global() ? "Tenant override saved for global link type." : "Link type updated.");
			} catch (RuntimeException ex) { showLinkTypeError(ex); }
		});
	}

	@FXML
	private void onToggleLinkTypeActive() {
		if (!requireAdminLookupManagement("Link Types")) return;
		LinkTypeViewRow selected = selectedLinkTypeRow();
		if (selected == null) return;
		try {
			caseService.setLinkTypeActive(new CaseServicePort.SetLinkTypeActiveCommand(requireTenantId(), requireActorUserId(), selected.id(), !selected.active(), selected.rowVer()));
			loadLinkTypesAsync(selected.active() ? "Link type deactivated for future selections." : "Link type activated.");
		} catch (RuntimeException ex) { showLinkTypeError(ex); }
	}

	@FXML
	private void onResetOrRemoveLinkType() {
		if (!requireAdminLookupManagement("Link Types")) return;
		LinkTypeViewRow selected = selectedLinkTypeRow();
		if (selected == null) return;
		String action = selected.custom() ? "Remove" : "Reset to Default";
		boolean confirmed = AppDialogs.showConfirmation(
				linkTypeCardsContainer == null || linkTypeCardsContainer.getScene() == null ? null : linkTypeCardsContainer.getScene().getWindow(),
				"Link Types",
				action + " " + selected.getName() + "?",
				"This affects future effective selections. Existing links retain their stored Link Type relationship.",
				action,
				selected.custom() ? AppDialogs.DialogActionKind.DANGER : AppDialogs.DialogActionKind.PRIMARY);
		if (!confirmed) return;
		try {
			caseService.resetLinkTypeOverride(new CaseServicePort.ResetLinkTypeOverrideCommand(requireTenantId(), requireActorUserId(), selected.id()));
			loadLinkTypesAsync(selected.custom() ? "Custom link type removed from future selections. Existing links retain their stored Link Type relationship." : "Tenant override reset to global default for future selections. Existing links retain their stored Link Type relationship.");
		} catch (RuntimeException ex) { showLinkTypeError(ex); }
	}

	private void loadLinkTypesAsync(String successMessage) {
		if (caseService == null || linkTypeCardsContainer == null) return;
		if (!requireAdminLookupManagement("Link Types")) { linkTypeRows.clear(); selectedLinkTypeRow = null; linkTypeCardsContainer.getChildren().clear(); return; }
		final int generation = ++linkTypeLoadGeneration;
		final int tenantId;
		final int actorUserId;
		try { tenantId = requireTenantId(); actorUserId = requireActorUserId(); } catch (RuntimeException ex) { setLinkTypeMessage(rootMessage(ex)); return; }
		setLinkTypeLoadingState("Loading link types…");
		settingsLoadExecutor.submit(() -> {
			try {
				List<LinkTypeViewRow> rows = buildLinkTypeRows(caseService.listLinkTypesForAdministration(tenantId, actorUserId), tenantId);
				Platform.runLater(() -> applyLinkTypeRows(generation, rows, successMessage));
			} catch (RuntimeException ex) {
				System.err.println("Failed to load Settings link types: " + rootMessage(ex));
				Platform.runLater(() -> { if (generation != linkTypeLoadGeneration) return; linkTypeRows.clear(); selectedLinkTypeRow = null; linkTypeCardsContainer.getChildren().clear(); setLinkTypeMessage("Failed to load link types. " + rootMessage(ex)); });
			}
		});
	}

	static List<LinkTypeViewRow> buildLinkTypeRows(List<LinkTypeDto> rows, int tenantId) {
		Map<String, LinkTypeDto> globals = new java.util.LinkedHashMap<>();
		Map<String, LinkTypeDto> tenantKeyed = new java.util.LinkedHashMap<>();
		List<LinkTypeViewRow> out = new ArrayList<>();
		for (LinkTypeDto row : rows == null ? List.<LinkTypeDto>of() : rows) {
			if (row == null || (row.shaleClientId() != null && row.shaleClientId() != tenantId)) continue;
			String key = safe(row.systemKey()).trim().toLowerCase(java.util.Locale.ROOT);
			if (row.shaleClientId() == null && !key.isBlank()) globals.put(key, row);
			else if (!key.isBlank()) tenantKeyed.put(key, row);
			else if (!row.deleted()) out.add(new LinkTypeViewRow(row, LinkTypeScope.TENANT_CUSTOM));
		}
		for (Map.Entry<String, LinkTypeDto> e : globals.entrySet()) {
			LinkTypeDto tenant = tenantKeyed.get(e.getKey());
			out.add(tenant == null || tenant.deleted() ? new LinkTypeViewRow(e.getValue(), LinkTypeScope.GLOBAL_DEFAULT) : new LinkTypeViewRow(tenant, LinkTypeScope.TENANT_OVERRIDE));
		}
		for (Map.Entry<String, LinkTypeDto> e : tenantKeyed.entrySet()) if (!globals.containsKey(e.getKey()) && !e.getValue().deleted()) out.add(new LinkTypeViewRow(e.getValue(), LinkTypeScope.TENANT_CUSTOM));
		out.sort(java.util.Comparator.comparing(LinkTypeViewRow::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(LinkTypeViewRow::id));
		return List.copyOf(out);
	}

	private void setLinkTypeLoadingState(String message) { if (linkTypeCardsContainer != null) linkTypeCardsContainer.getChildren().setAll(loadingLabel(message)); setLinkTypeMessage(message); }
	private void applyLinkTypeRows(int generation, List<LinkTypeViewRow> rows, String successMessage) { if (generation != linkTypeLoadGeneration) return; Integer selectedId = selectedLinkTypeRow == null ? null : selectedLinkTypeRow.id(); linkTypeRows.clear(); linkTypeRows.addAll(rows); selectedLinkTypeRow = rows.stream().filter(row -> selectedId != null && row.id() == selectedId).findFirst().orElse(null); renderLinkTypeCards(); setLinkTypeMessage(successMessage != null && !successMessage.isBlank() ? successMessage : rows.isEmpty() ? "No link types are configured for this tenant." : ""); }
	private void renderLinkTypeCards() { if (linkTypeCardsContainer == null) return; linkTypeCardsContainer.getChildren().clear(); for (LinkTypeViewRow row : linkTypeRows) linkTypeCardsContainer.getChildren().add(buildLinkTypeCard(row)); }
	private VBox buildLinkTypeCard(LinkTypeViewRow row) {
		VBox card = new VBox(8); card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "shale-entity-card-selectable", "shale-density-compact"); if (selectedLinkTypeRow != null && selectedLinkTypeRow.id() == row.id()) card.getStyleClass().add("link-type-card-selected"); card.setOnMouseClicked(event -> selectLinkTypeRow(row));
		HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT); Circle dot = new Circle(6); String colorCss = safe(ColorUtil.toCssBackgroundColorOrNull(row.getColor())); if (!colorCss.isBlank()) dot.setStyle("-fx-background-color: " + colorCss + "; -fx-fill: " + colorCss + ";"); Label name = new Label(row.getName()); name.getStyleClass().add("app-dialog-field-label"); Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS); header.getChildren().addAll(dot, name, spacer, LinkTypeIndicatorFactory.createLinkTypePill(row.getName(), row.getColor(), LinkTypeIndicatorFactory.PillSize.COMPACT));
		HBox metadata = new HBox(6); metadata.setAlignment(Pos.CENTER_LEFT); metadata.getChildren().addAll(metadataPill(row.getActiveState()), metadataPill(row.scopeLabel())); if (!row.getSystemKey().isBlank()) metadata.getChildren().add(metadataPill("System: " + row.getSystemKey())); if (!row.getColor().isBlank()) metadata.getChildren().add(metadataPill(row.getColor()));
		HBox actions = new HBox(8); actions.setAlignment(Pos.CENTER_LEFT); Button edit = cardButton(row.global() ? "Customize" : "Edit", "app-toolbar-button-neutral"); edit.setOnAction(event -> { selectLinkTypeRow(row); onEditLinkType(); event.consume(); }); Button toggle = cardButton(row.active() ? "Deactivate" : "Activate", "app-toolbar-button-neutral"); toggle.setOnAction(event -> { selectLinkTypeRow(row); onToggleLinkTypeActive(); event.consume(); }); Button reset = cardButton(row.custom() ? "Remove" : "Reset to Default", row.custom() ? "app-toolbar-button-danger" : "app-toolbar-button-neutral"); reset.setDisable(row.global()); reset.setOnAction(event -> { selectLinkTypeRow(row); onResetOrRemoveLinkType(); event.consume(); }); Label help = new Label(row.lifecycleText()); help.getStyleClass().add("search-summary-text"); help.setWrapText(true); actions.getChildren().addAll(edit, toggle, reset, help);
		card.getChildren().addAll(header, metadata, actions); return card;
	}
	private void selectLinkTypeRow(LinkTypeViewRow row) { selectedLinkTypeRow = row; renderLinkTypeCards(); }
	private LinkTypeViewRow selectedLinkTypeRow() { if (selectedLinkTypeRow == null) setLinkTypeMessage("Select a link type first."); return selectedLinkTypeRow; }
	private Optional<LinkTypeInput> showLinkTypeDialog(LinkTypeDto existing) {
		Dialog<LinkTypeInput> dialog = new Dialog<>(); String dialogTitle = existing == null ? "Add Link Type" : (existing.shaleClientId() == null ? "Customize Link Type" : "Edit Link Type"); dialog.setTitle(dialogTitle); AppDialogs.applySecondaryDialogShell(dialog, dialogTitle); dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField name = new TextField(existing == null ? "" : existing.name()); name.setPromptText("100 characters max"); CheckBox active = new CheckBox("Active"); active.setSelected(existing == null || existing.active()); ColorPicker colorPicker = new ColorPicker(dbColorToFx(existing == null ? null : existing.color())); GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8); grid.add(new Label("Name"),0,0); grid.add(name,1,0); grid.add(new Label("Color"),0,1); grid.add(colorPicker,1,1); grid.add(active,1,2); if (existing != null && !safe(existing.systemKey()).isBlank()) { grid.add(new Label("System Key"),0,3); grid.add(new Label(existing.systemKey()),1,3); } dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> { if (button != ButtonType.OK) return null; String trimmedName = trim(name.getText()); if (trimmedName.isBlank()) throw new IllegalArgumentException("Name is required."); if (trimmedName.length() > 100) throw new IllegalArgumentException("Name must be 100 characters or fewer."); String systemKey = linkTypeSystemKeyForSave(existing); if (systemKey != null && systemKey.length() > 64) throw new IllegalArgumentException("SystemKey must be 64 characters or fewer."); String color = fxColorToDb(colorPicker.getValue()); if (color.length() > 20) throw new IllegalArgumentException("Color must be 20 characters or fewer."); return new LinkTypeInput(trimmedName, color, active.isSelected(), systemKey); });
		try { return dialog.showAndWait(); } catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Link Types", rootMessage(ex)); return Optional.empty(); }
	}
	private void showLinkTypeError(RuntimeException ex) { AppDialogs.showError(linkTypeCardsContainer == null || linkTypeCardsContainer.getScene() == null ? null : linkTypeCardsContainer.getScene().getWindow(), "Link Types", rootMessage(ex)); }


	@FXML
	private void onAddPracticeArea() {
		if (!requireAdminLookupManagement("Practice Areas")) return;
		showPracticeAreaDialog(null).ifPresent(input -> {
			caseService.createPracticeArea(new CaseServicePort.PracticeAreaCommand(
					null, requireTenantId(), input.name(), input.color(), input.active(), input.systemKey()));
			loadPracticeAreasAsync("Practice area added.");
		});
	}

	@FXML
	private void onEditPracticeArea() {
		if (!requireAdminLookupManagement("Practice Areas")) return;
		PracticeAreaViewRow selected = selectedPracticeAreaRow();
		if (selected == null) return;
		showPracticeAreaDialog(selected.practiceArea()).ifPresent(input -> {
			caseService.updatePracticeArea(new CaseServicePort.PracticeAreaCommand(
					selected.id(), requireTenantId(), input.name(), input.color(), input.active(), input.systemKey()));
			loadPracticeAreasAsync("Practice area updated.");
		});
	}

	@FXML
	private void onRemovePracticeArea() {
		if (!requireAdminLookupManagement("Practice Areas")) return;
		PracticeAreaViewRow selected = selectedPracticeAreaRow();
		if (selected == null) return;
		try {
			caseService.deactivatePracticeArea(requireTenantId(), selected.id());
			loadPracticeAreasAsync("Practice area removed from new selections. Existing cases keep their value.");
		} catch (RuntimeException ex) {
			AppDialogs.showError(practiceAreaCardsContainer.getScene().getWindow(), "Practice Areas", rootMessage(ex));
		}
	}


	private void loadPracticeAreas() {
		loadPracticeAreasAsync(null);
	}

	private void loadPracticeAreasAsync(String successMessage) {
		if (caseService == null || practiceAreaCardsContainer == null) return;
		if (!requireAdminLookupManagement("Practice Areas")) {
			practiceAreaRows.clear();
			selectedPracticeAreaRow = null;
			practiceAreaCardsContainer.getChildren().clear();
			return;
		}
		final int generation = ++practiceAreaLoadGeneration;
		final int tenantId;
		try {
			tenantId = requireTenantId();
		} catch (RuntimeException ex) {
			setPracticeAreaMessage(rootMessage(ex));
			return;
		}
		setPracticeAreaLoadingState("Loading practice areas…");
		settingsLoadExecutor.submit(() -> {
			try {
				List<PracticeAreaViewRow> rows = new ArrayList<>();
				for (PracticeAreaDto area : caseService.listPracticeAreas(tenantId, true)) rows.add(new PracticeAreaViewRow(area));
				Platform.runLater(() -> applyPracticeAreaRows(generation, rows, successMessage));
			} catch (RuntimeException ex) {
				System.err.println("Failed to load Settings practice areas: " + rootMessage(ex));
				Platform.runLater(() -> {
					if (generation != practiceAreaLoadGeneration) return;
					practiceAreaRows.clear();
					selectedPracticeAreaRow = null;
					practiceAreaCardsContainer.getChildren().clear();
					setPracticeAreaMessage("Failed to load practice areas. " + rootMessage(ex));
				});
			}
		});
	}

	private void renderPracticeAreaCards() {
		if (practiceAreaCardsContainer == null) return;
		practiceAreaCardsContainer.getChildren().clear();
		for (PracticeAreaViewRow row : practiceAreaRows) {
			practiceAreaCardsContainer.getChildren().add(buildPracticeAreaCard(row));
		}
	}

	private VBox buildPracticeAreaCard(PracticeAreaViewRow row) {
		VBox card = new VBox(8);
		card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "shale-entity-card-selectable", "shale-density-compact");
		if (selectedPracticeAreaRow != null && selectedPracticeAreaRow.id() == row.id()) {
			card.getStyleClass().add("practice-area-card-selected");
		}
		card.setOnMouseClicked(event -> selectPracticeAreaRow(row));

		HBox header = new HBox(10);
		header.setAlignment(Pos.CENTER_LEFT);
		Circle dot = new Circle(6);
		dot.getStyleClass().addAll("shale-indicator-dot", "shale-indicator-practice-area");
		String colorCss = safe(ColorUtil.toCssBackgroundColorOrNull(row.getColor()));
		if (!colorCss.isBlank()) dot.setStyle("-fx-background-color: " + colorCss + "; -fx-fill: " + colorCss + ";");
		Label name = new Label(row.getName());
		name.getStyleClass().add("app-dialog-field-label");
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		Label preview = PracticeAreaIndicatorFactory.createPracticeAreaPill(row.getName(), row.getColor(), PracticeAreaIndicatorFactory.PillSize.COMPACT);
		header.getChildren().addAll(dot, name, spacer, preview);

		HBox metadata = new HBox(6);
		metadata.setAlignment(Pos.CENTER_LEFT);
		metadata.getChildren().addAll(metadataPill(row.getActiveState()), metadataPill(row.scopeLabel()));
		if (!row.getSystemKey().isBlank()) metadata.getChildren().add(metadataPill("System: " + row.getSystemKey()));
		if (row.deleted()) metadata.getChildren().add(metadataPill("Deleted"));
		if (!row.getColor().isBlank()) metadata.getChildren().add(metadataPill(row.getColor()));

		HBox actions = new HBox(8);
		actions.setAlignment(Pos.CENTER_LEFT);
		Button edit = cardButton("Edit", "app-toolbar-button-neutral");
		edit.setOnAction(event -> { selectPracticeAreaRow(row); onEditPracticeArea(); event.consume(); });
		Button remove = cardButton("Remove", "app-toolbar-button-danger");
		remove.setOnAction(event -> { selectPracticeAreaRow(row); onRemovePracticeArea(); event.consume(); });
		Label restriction = new Label(row.global() ? "Global/default practice area: editing creates or updates a tenant-scoped override when supported." : "Tenant-specific/custom practice area.");
		restriction.getStyleClass().add("search-summary-text");
		actions.getChildren().addAll(edit, remove, restriction);

		card.getChildren().addAll(header, metadata, actions);
		return card;
	}

	private void selectPracticeAreaRow(PracticeAreaViewRow row) {
		selectedPracticeAreaRow = row;
		renderPracticeAreaCards();
	}

	private Optional<PracticeAreaInput> showPracticeAreaDialog(PracticeAreaDto existing) {
		Dialog<PracticeAreaInput> dialog = new Dialog<>();
		String dialogTitle = existing == null ? "Add Practice Area" : "Edit Practice Area";
		dialog.setTitle(dialogTitle);
		AppDialogs.applySecondaryDialogShell(dialog, dialogTitle);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField name = new TextField(existing == null ? "" : existing.name());
		CheckBox active = new CheckBox("Active");
		active.setSelected(existing == null || existing.active());
		ColorPicker colorPicker = new ColorPicker(dbColorToFx(existing == null ? null : existing.color()));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("Name"), 0, 0); grid.add(name, 1, 0);
		grid.add(new Label("Color"), 0, 1); grid.add(colorPicker, 1, 1);
		grid.add(active, 1, 2);
		if (existing != null && !safe(existing.systemKey()).isBlank()) {
			grid.add(new Label("System Key"), 0, 3);
			grid.add(new Label(existing.systemKey()), 1, 3);
		}
		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String trimmedName = name.getText() == null ? "" : name.getText().trim();
			if (trimmedName.isBlank()) throw new IllegalArgumentException("Name is required.");
			return new PracticeAreaInput(trimmedName, fxColorToDb(colorPicker.getValue()), active.isSelected(), practiceAreaSystemKeyForSave(existing));
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Practice Areas", rootMessage(ex)); return Optional.empty(); }
	}

	private PracticeAreaViewRow selectedPracticeAreaRow() {
		if (selectedPracticeAreaRow == null) setPracticeAreaMessage("Select a practice area first.");
		return selectedPracticeAreaRow;
	}

	@FXML
	private void onAddCaseStatus() {
		if (!requireAdminLookupManagement("Case Statuses")) return;
		showCaseStatusDialog(null).ifPresent(input -> {
			caseService.createCaseStatus(new CaseServicePort.CaseStatusCommand(
					null,
					requireTenantId(),
					input.name(),
					input.closed(),
					input.sortOrder(),
					input.color(),
					input.lifecycleKey(),
					input.systemKey()));
			loadCaseStatusesAsync("Case status added.");
		});
	}

	@FXML
	private void onEditCaseStatus() {
		if (!requireAdminLookupManagement("Case Statuses")) return;
		CaseStatusViewRow selected = selectedStatusRow();
		if (selected == null) return;
		showCaseStatusDialog(selected.status()).ifPresent(input -> {
			caseService.updateCaseStatus(new CaseServicePort.CaseStatusCommand(
					selected.id(),
					requireTenantId(),
					input.name(),
					input.closed(),
					input.sortOrder(),
					input.color(),
					input.lifecycleKey(),
					input.systemKey()));
			loadCaseStatusesAsync("Case status updated.");
		});
	}

	@FXML
	private void onMoveCaseStatusUp() { if (requireAdminLookupManagement("Case Statuses")) moveSelectedStatus(-1); }

	@FXML
	private void onMoveCaseStatusDown() { if (requireAdminLookupManagement("Case Statuses")) moveSelectedStatus(1); }


	private void loadCaseStatuses() {
		loadCaseStatusesAsync(null);
	}

	private void loadCaseStatusesAsync(String successMessage) {
		if (caseService == null || caseStatusCardsContainer == null) return;
		if (!requireAdminLookupManagement("Case Statuses")) {
			caseStatusRows.clear();
			selectedCaseStatusRow = null;
			caseStatusCardsContainer.getChildren().clear();
			return;
		}
		final int generation = ++caseStatusLoadGeneration;
		final int tenantId;
		try {
			tenantId = requireTenantId();
		} catch (RuntimeException ex) {
			setCaseStatusMessage(rootMessage(ex));
			return;
		}
		setCaseStatusLoadingState("Loading case statuses…");
		settingsLoadExecutor.submit(() -> {
			try {
				List<CaseStatusViewRow> rows = new ArrayList<>();
				for (CaseStatusDto status : caseService.listCaseStatuses(tenantId, true)) rows.add(new CaseStatusViewRow(status));
				Platform.runLater(() -> applyCaseStatusRows(generation, rows, successMessage));
			} catch (RuntimeException ex) {
				System.err.println("Failed to load Settings case statuses: " + rootMessage(ex));
				Platform.runLater(() -> {
					if (generation != caseStatusLoadGeneration) return;
					caseStatusRows.clear();
					selectedCaseStatusRow = null;
					caseStatusCardsContainer.getChildren().clear();
					setCaseStatusMessage("Failed to load case statuses. " + rootMessage(ex));
				});
			}
		});
	}

	private void renderCaseStatusCards() {
		if (caseStatusCardsContainer == null) return;
		caseStatusCardsContainer.getChildren().clear();
		for (int i = 0; i < caseStatusRows.size(); i++) {
			caseStatusCardsContainer.getChildren().add(buildCaseStatusCard(caseStatusRows.get(i), i));
		}
	}

	private VBox buildCaseStatusCard(CaseStatusViewRow row, int index) {
		VBox card = new VBox(8);
		card.getStyleClass().addAll("shale-entity-card", "shale-entity-card-compact", "shale-entity-card-selectable", "shale-density-compact");
		if (selectedCaseStatusRow != null && selectedCaseStatusRow.id() == row.id()) {
			card.getStyleClass().add("case-status-card-selected");
		}
		card.setOnMouseClicked(event -> selectCaseStatusRow(row));

		HBox header = new HBox(10);
		header.setAlignment(Pos.CENTER_LEFT);
		Label name = new Label(row.getName());
		name.getStyleClass().add("app-dialog-field-label");
		Node preview = StatusIndicatorFactory.createStatusPill(row.getName(), row.color());
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		header.getChildren().addAll(name, spacer, preview);

		HBox metadata = new HBox(6);
		metadata.setAlignment(Pos.CENTER_LEFT);
		metadata.getChildren().addAll(
				metadataPill(row.getClosedState()),
				metadataPill("Sort " + row.getSortOrder()),
				metadataPill(row.scopeLabel()));
		if (!row.getLifecycleKey().isBlank()) metadata.getChildren().add(metadataPill("Lifecycle: " + row.getLifecycleKey()));
		if (!row.getSystemKey().isBlank()) metadata.getChildren().add(metadataPill("System: " + row.getSystemKey()));

		HBox actions = new HBox(8);
		actions.setAlignment(Pos.CENTER_LEFT);
		Button edit = cardButton("Edit", "app-toolbar-button-neutral");
		edit.setOnAction(event -> { selectCaseStatusRow(row); onEditCaseStatus(); event.consume(); });
		Button up = cardButton("Move Up", "app-toolbar-button-neutral");
		up.setDisable(index == 0);
		up.setOnAction(event -> { selectCaseStatusRow(row); moveSelectedStatus(-1); event.consume(); });
		Button down = cardButton("Move Down", "app-toolbar-button-neutral");
		down.setDisable(index >= caseStatusRows.size() - 1);
		down.setOnAction(event -> { selectCaseStatusRow(row); moveSelectedStatus(1); event.consume(); });
		Label restriction = new Label(row.global() ? "Global/default status: editing creates a tenant override; reordering requires tenant-specific status." : "Tenant-specific/custom status.");
		restriction.getStyleClass().add("search-summary-text");
		actions.getChildren().addAll(edit, up, down, restriction);

		card.getChildren().addAll(header, metadata, actions);
		return card;
	}

	private Label metadataPill(String text) {
		Label label = new Label(text == null || text.isBlank() ? "—" : text);
		label.getStyleClass().addAll("shale-indicator-chip");
		return label;
	}

	private Button cardButton(String text, String roleClass) {
		Button button = new Button(text);
		button.getStyleClass().addAll("app-toolbar-button", roleClass, "app-toolbar-button-compact");
		return button;
	}

	private void selectCaseStatusRow(CaseStatusViewRow row) {
		selectedCaseStatusRow = row;
		renderCaseStatusCards();
	}

	private void moveSelectedStatus(int delta) {
		if (!requireAdminLookupManagement("Case Statuses")) return;
		CaseStatusViewRow selected = selectedStatusRow();
		if (selected == null) return;
		int index = caseStatusRows.indexOf(selected);
		int otherIndex = index + delta;
		if (otherIndex < 0 || otherIndex >= caseStatusRows.size()) return;
		CaseStatusViewRow other = caseStatusRows.get(otherIndex);
		try {
			caseService.reorderCaseStatuses(requireTenantId(), selected.id(), other.id());
			selectedCaseStatusRow = selected;
			loadCaseStatusesAsync(null);
		} catch (RuntimeException ex) {
			AppDialogs.showError(caseStatusCardsContainer.getScene().getWindow(), "Case Statuses", rootMessage(ex));
		}
	}

	private Optional<CaseStatusInput> showCaseStatusDialog(CaseStatusDto existing) {
		Dialog<CaseStatusInput> dialog = new Dialog<>();
		String dialogTitle = existing == null ? "Add Status" : "Edit Status";
		dialog.setTitle(dialogTitle);
		AppDialogs.applySecondaryDialogShell(dialog, dialogTitle);
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField name = new TextField(existing == null ? "" : existing.name());
		CheckBox closed = new CheckBox("Closed status");
		closed.setSelected(existing != null && existing.closed());
		ColorPicker colorPicker = new ColorPicker(dbColorToFx(existing == null ? null : existing.color()));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("Name"), 0, 0); grid.add(name, 1, 0);
		grid.add(closed, 1, 1);
		grid.add(new Label("Color"), 0, 2); grid.add(colorPicker, 1, 2);
		if (existing != null && !safe(existing.lifecycleKey()).isBlank()) {
			grid.add(new Label("Lifecycle Key"), 0, 3);
			grid.add(new Label(existing.lifecycleKey()), 1, 3);
		}
		if (existing != null && !safe(existing.systemKey()).isBlank()) {
			grid.add(new Label("System Key"), 0, 4);
			grid.add(new Label(existing.systemKey()), 1, 4);
		}
		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String trimmedName = name.getText() == null ? "" : name.getText().trim();
			if (trimmedName.isBlank()) throw new IllegalArgumentException("Name is required.");
			return new CaseStatusInput(
					trimmedName,
					closed.isSelected(),
					sortOrderForSave(existing),
					fxColorToDb(colorPicker.getValue()),
					lifecycleKeyForSave(existing),
					systemKeyForSave(existing));
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Case Statuses", rootMessage(ex)); return Optional.empty(); }
	}

	private CaseStatusViewRow selectedStatusRow() {
		if (selectedCaseStatusRow == null) setCaseStatusMessage("Select a case status first.");
		return selectedCaseStatusRow;
	}

	private boolean requireAdminLookupManagement(String sectionName) {
		if (isAdminUser()) {
			return true;
		}
		String message = "Only admin users can manage " + sectionName.toLowerCase() + ".";
		if ("Case Statuses".equals(sectionName)) {
			setCaseStatusMessage(message);
		} else if ("Practice Areas".equals(sectionName)) {
			setPracticeAreaMessage(message);
		} else if ("Link Types".equals(sectionName)) {
			setLinkTypeMessage(message);
		}
		return false;
	}

	private int requireActorUserId() {
		Integer id = appState == null ? null : appState.getUserId();
		if (id == null || id <= 0) throw new IllegalStateException("No actor user is selected.");
		return id;
	}

	private int requireTenantId() {
		Integer id = appState == null ? null : appState.getShaleClientId();
		if (id == null || id <= 0) throw new IllegalStateException("No tenant is selected.");
		return id;
	}

	private static final Color DEFAULT_STATUS_COLOR = Color.rgb(108, 117, 125);


	static Color dbColorToFx(String value) {
		String normalized = value == null ? "" : value.trim();
		if (normalized.matches("(?i)^#[0-9a-f]{6}$")) {
			try { return Color.web(normalized); } catch (RuntimeException ignored) { return DEFAULT_STATUS_COLOR; }
		}
		if (normalized.matches("(?i)^0x[0-9a-f]{8}$")) {
			try {
				int red = Integer.parseInt(normalized.substring(2, 4), 16);
				int green = Integer.parseInt(normalized.substring(4, 6), 16);
				int blue = Integer.parseInt(normalized.substring(6, 8), 16);
				int alpha = Integer.parseInt(normalized.substring(8, 10), 16);
				return Color.rgb(red, green, blue, alpha / 255.0);
			} catch (RuntimeException ignored) {
				return DEFAULT_STATUS_COLOR;
			}
		}
		return DEFAULT_STATUS_COLOR;
	}

	static String fxColorToDb(Color color) {
		Color safeColor = color == null ? DEFAULT_STATUS_COLOR : color;
		return String.format("0x%02X%02X%02X%02X",
				toColorByte(safeColor.getRed()),
				toColorByte(safeColor.getGreen()),
				toColorByte(safeColor.getBlue()),
				toColorByte(safeColor.getOpacity()));
	}

	private static int toColorByte(double value) {
		return Math.max(0, Math.min(255, (int) Math.round(value * 255.0)));
	}

	static Integer sortOrderForSave(CaseStatusDto existing) {
		return existing == null ? null : existing.sortOrder();
	}

	static String lifecycleKeyForSave(CaseStatusDto existing) {
		return existing == null ? null : existing.lifecycleKey();
	}

	static String practiceAreaSystemKeyForSave(PracticeAreaDto existing) {
		return existing == null ? null : existing.systemKey();
	}

	static String linkTypeSystemKeyForSave(LinkTypeDto existing) {
		return existing == null ? null : existing.systemKey();
	}

	static String systemKeyForSave(CaseStatusDto existing) {
		return existing == null ? null : existing.systemKey();
	}

	private void setCaseStatusMessage(String message) { if (caseStatusSettingsStatusLabel != null) caseStatusSettingsStatusLabel.setText(message == null ? "" : message); }
	@FXML
	private void onAddUser() {
		if (!isAdminUser()) {
			AppDialogs.showError(null, "Add User", "Only admin users can create users.");
			return;
		}
		showAddUserDialog().ifPresent(request -> {
			try {
				userDao.createUser(request);
				loadManagedUsersAsync(null);
				AppDialogs.showInfo(null, "Add User", "User added.");
			} catch (RuntimeException ex) {
				AppDialogs.showError(null, "Add User", rootMessage(ex));
			}
		});
	}

	private Optional<UserDao.UserCreateRequest> showAddUserDialog() {
		Dialog<UserDao.UserCreateRequest> dialog = new Dialog<>();
		dialog.setTitle("Add User");
		AppDialogs.applySecondaryDialogShell(dialog, "Add User");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField firstName = new TextField();
		TextField lastName = new TextField();
		TextField email = new TextField();
		Label emailValidation = new Label("");
		emailValidation.getStyleClass().add("dialog-error-text");
		email.focusedProperty().addListener((obs, oldValue, focused) -> {
			if (!focused) validateAddUserEmail(email, emailValidation);
		});
		PasswordField password = new PasswordField();
		TextField initials = new TextField();
		ColorPicker colorPicker = new ColorPicker(DEFAULT_STATUS_COLOR);
		CheckBox attorney = new CheckBox("Attorney");
		CheckBox admin = new CheckBox("Admin");
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("First Name"), 0, 0); grid.add(firstName, 1, 0);
		grid.add(new Label("Last Name"), 0, 1); grid.add(lastName, 1, 1);
		grid.add(new Label("Email"), 0, 2); grid.add(email, 1, 2);
		grid.add(emailValidation, 1, 3);
		grid.add(new Label("Temporary Password"), 0, 4); grid.add(password, 1, 4);
		grid.add(new Label("Initials"), 0, 5); grid.add(initials, 1, 5);
		grid.add(new Label("Color"), 0, 6); grid.add(colorPicker, 1, 6);
		grid.add(attorney, 1, 7);
		grid.add(admin, 1, 8);
		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> {
			if (button != ButtonType.OK) return null;
			String duplicateMessage = validateAddUserEmail(email, emailValidation);
			if (!duplicateMessage.isBlank()) throw new IllegalArgumentException(duplicateMessage);
			return new UserDao.UserCreateRequest(
					trim(firstName.getText()),
					trim(lastName.getText()),
					trim(email.getText()),
					password.getText(),
					fxColorToDb(colorPicker.getValue()),
					trim(initials.getText()),
					attorney.isSelected(),
					admin.isSelected());
		});
		try { return dialog.showAndWait(); }
		catch (RuntimeException ex) { AppDialogs.showError(dialog.getOwner(), "Add User", rootMessage(ex)); return Optional.empty(); }
	}


	private String validateAddUserEmail(TextField emailField, Label emailValidation) {
		String normalized = UserDao.normalizeEmail(trim(emailField == null ? null : emailField.getText()));
		if (normalized.isBlank() || userDao == null) {
			if (emailValidation != null) emailValidation.setText("");
			return "";
		}
		try {
			UserDao.ExistingEmailRow existing = userDao.findExistingEmailForCurrentTenant(normalized);
			String message = existing == null ? "" : UserDao.duplicateEmailMessage(existing.deleted());
			if (emailValidation != null) emailValidation.setText(message);
			return message;
		} catch (RuntimeException ex) {
			String message = rootMessage(ex);
			if (emailValidation != null) emailValidation.setText(message);
			return message;
		}
	}

	@FXML
	private void onToggleInactiveUsers() { loadManagedUsersAsync(null); }

	@FXML
	private void onDeactivateUser() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null) return;
		boolean confirmed = AppDialogs.showConfirmation(null, "Deactivate User", "Deactivate this user?", "This will disable their access while preserving historical records.", "Deactivate", AppDialogs.DialogActionKind.DANGER);
		if (!confirmed) return;
		try {
			userDao.deactivateUser(selected.id());
			loadManagedUsersAsync("User deactivated.");
			setUserManagementMessage("User deactivated.");
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Deactivate User", rootMessage(ex));
		}
	}

	@FXML
	private void onReactivateUser() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null) return;
		try {
			userDao.reactivateUser(selected.id());
			loadManagedUsersAsync("User reactivated.");
			setUserManagementMessage("User reactivated.");
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Reactivate User", rootMessage(ex));
		}
	}

	@FXML
	private void onResetUserPassword() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null) return;
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Reset Password");
		AppDialogs.applySecondaryDialogShell(dialog, "Reset Password");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		PasswordField password = new PasswordField();
		PasswordField confirm = new PasswordField();
		Label validation = new Label("");
		validation.getStyleClass().add("dialog-error-text");
		password.textProperty().addListener((obs, oldValue, newValue) -> validation.setText(""));
		confirm.textProperty().addListener((obs, oldValue, newValue) -> validation.setText(""));
		GridPane grid = new GridPane(); grid.setHgap(8); grid.setVgap(8);
		grid.add(new Label("New Password"), 0, 0); grid.add(password, 1, 0);
		grid.add(new Label("Confirm Password"), 0, 1); grid.add(confirm, 1, 1);
		grid.add(validation, 1, 2);
		dialog.getDialogPane().setContent(grid);
		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
			String message = resetPasswordValidationMessage(password.getText(), confirm.getText());
			if (!message.isBlank()) {
				validation.setText(message);
				event.consume();
			}
		});
		dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText() : null);
		try {
			dialog.showAndWait().ifPresent(newPassword -> {
				boolean confirmed = AppDialogs.showConfirmation(null, "Reset Password", "Reset password for " + selected.name() + "?", "Password access will change immediately.", "Reset", AppDialogs.DialogActionKind.PRIMARY);
				if (!confirmed) return;
				userDao.resetPassword(selected.id(), newPassword);
				AppDialogs.showInfo(null, "Reset Password", "Password successfully updated.");
			});
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Reset Password", rootMessage(ex));
		}
	}

	static String resetPasswordValidationMessage(String password, String confirmPassword) {
		if (password == null || password.isBlank()) return "Password is required.";
		if (confirmPassword == null || confirmPassword.isBlank()) return "Confirm password is required.";
		if (!Objects.equals(password, confirmPassword)) return "Passwords do not match.";
		return "";
	}

	private void configureUserManagementTable() {
		if (userManagementTable == null) return;
		userNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
		userEmailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
		userInitialsColumn.setCellValueFactory(new PropertyValueFactory<>("initials"));
		userAttorneyColumn.setCellValueFactory(new PropertyValueFactory<>("attorneyState"));
		userAdminColumn.setCellValueFactory(new PropertyValueFactory<>("adminState"));
		userStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
		userManagementTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> updateUserActionButtons(newRow));
	}

	private void loadManagedUsers() {
		loadManagedUsersAsync(null);
	}

	private void loadManagedUsersAsync(String successMessage) {
		if (userDao == null || userManagementTable == null || !isAdminUser()) return;
		final int generation = ++userManagementLoadGeneration;
		boolean includeInactive = showInactiveUsersCheck != null && showInactiveUsersCheck.isSelected();
		userManagementTable.getItems().clear();
		updateUserActionButtons(null);
		setUserManagementMessage("Loading users…");
		settingsLoadExecutor.submit(() -> {
			try {
				List<UserManagementViewRow> rows = new ArrayList<>();
				for (UserDao.UserManagementRow row : userDao.listUsersForManagement(includeInactive)) rows.add(new UserManagementViewRow(row));
				Platform.runLater(() -> {
					if (generation != userManagementLoadGeneration) return;
					userManagementTable.getItems().setAll(rows);
					updateUserActionButtons(userManagementTable.getSelectionModel().getSelectedItem());
					setUserManagementMessage(successMessage != null && !successMessage.isBlank() ? successMessage : rows.isEmpty() ? "No users found for this tenant." : "");
				});
			} catch (RuntimeException ex) {
				System.err.println("Failed to load Settings users: " + rootMessage(ex));
				Platform.runLater(() -> {
					if (generation != userManagementLoadGeneration) return;
					userManagementTable.getItems().clear();
					updateUserActionButtons(null);
					setUserManagementMessage("Failed to load users. " + rootMessage(ex));
				});
			}
		});
	}

	private UserManagementViewRow selectedManagedUser() {
		UserManagementViewRow selected = userManagementTable == null ? null : userManagementTable.getSelectionModel().getSelectedItem();
		if (selected == null) setUserManagementMessage("Select a user first.");
		return selected;
	}

	private void updateUserActionButtons(UserManagementViewRow selected) {
		boolean has = selected != null;
		if (deactivateUserButton != null) deactivateUserButton.setDisable(!has || selected.deleted());
		if (reactivateUserButton != null) reactivateUserButton.setDisable(!has || !selected.deleted());
		if (resetPasswordButton != null) resetPasswordButton.setDisable(!has || selected.deleted());
	}

	private void setUserManagementMessage(String message) { if (userManagementStatusLabel != null) userManagementStatusLabel.setText(message == null ? "" : message); }

	private static String trim(String value) { return value == null ? "" : value.trim(); }

	private void setPracticeAreaMessage(String message) { if (practiceAreaSettingsStatusLabel != null) practiceAreaSettingsStatusLabel.setText(message == null ? "" : message); }
	private void setLinkTypeMessage(String message) { if (linkTypeSettingsStatusLabel != null) linkTypeSettingsStatusLabel.setText(message == null ? "" : message); }
	private static String safe(String value) { return value == null ? "" : value; }
	private static String rootMessage(Throwable ex) { Throwable t = ex; while (t.getCause() != null) t = t.getCause(); return t.getMessage() == null ? t.toString() : t.getMessage(); }

	public static final class CaseStatusViewRow {
		private final CaseStatusDto status;
		CaseStatusViewRow(CaseStatusDto status) { this.status = status; }
		public int getId() { return status.id(); }
		public int id() { return status.id(); }
		public String getName() { return safe(status.name()); }
		public String getClosedState() { return status.closed() ? "Closed" : "Open"; }
		public Integer getSortOrder() { return status.sortOrder(); }
		public String getLifecycleKey() { return safe(status.lifecycleKey()); }
		public String getSystemKey() { return safe(status.systemKey()); }
		public String color() { return status.color(); }
		public boolean global() { return status.shaleClientId() == null; }
		public String scopeLabel() { return global() ? "Global/default" : "Tenant/custom"; }
		CaseStatusDto status() { return status; }
	}


	public enum LinkTypeScope { GLOBAL_DEFAULT, TENANT_OVERRIDE, TENANT_CUSTOM }

	public static final class LinkTypeViewRow {
		private final LinkTypeDto linkType;
		private final LinkTypeScope scope;
		LinkTypeViewRow(LinkTypeDto linkType, LinkTypeScope scope) { this.linkType = linkType; this.scope = scope; }
		public int getId() { return linkType.id(); }
		public int id() { return linkType.id(); }
		public String getName() { return safe(linkType.name()); }
		public String getColor() { return safe(linkType.color()); }
		public String getActiveState() { return linkType.active() && !linkType.deleted() ? "Active" : "Inactive"; }
		public String getSystemKey() { return safe(linkType.systemKey()); }
		public boolean active() { return linkType.active() && !linkType.deleted(); }
		public boolean global() { return scope == LinkTypeScope.GLOBAL_DEFAULT; }
		public boolean custom() { return scope == LinkTypeScope.TENANT_CUSTOM; }
		public String scopeLabel() { return switch (scope) { case GLOBAL_DEFAULT -> "Global/default"; case TENANT_OVERRIDE -> "Tenant override"; case TENANT_CUSTOM -> "Tenant custom"; }; }
		public String lifecycleText() { return switch (scope) { case GLOBAL_DEFAULT -> "Editing or changing active state creates a tenant override; global rows are never changed."; case TENANT_OVERRIDE -> "Tenant override masks the global default until reset."; case TENANT_CUSTOM -> "Tenant custom type can be edited, activated/deactivated, or removed."; }; }
		byte[] rowVer() { return linkType.rowVer(); }
		LinkTypeDto linkType() { return linkType; }
	}

	public static final class UserManagementViewRow {
		private final UserDao.UserManagementRow row;
		UserManagementViewRow(UserDao.UserManagementRow row) { this.row = row; }
		public int getId() { return row.id(); }
		public int id() { return row.id(); }
		public String getName() { return safe(row.name()); }
		public String name() { return getName(); }
		public String getEmail() { return safe(row.email()); }
		public String getInitials() { return safe(row.initials()); }
		public String getAttorneyState() { return row.attorney() ? "Yes" : "No"; }
		public String getAdminState() { return row.admin() ? "Yes" : "No"; }
		public String getStatus() { return row.deleted() ? "Inactive" : "Active"; }
		public boolean deleted() { return row.deleted(); }
	}

	public static final class PracticeAreaViewRow {
		private final PracticeAreaDto practiceArea;
		PracticeAreaViewRow(PracticeAreaDto practiceArea) { this.practiceArea = practiceArea; }
		public int getId() { return practiceArea.id(); }
		public int id() { return practiceArea.id(); }
		public String getName() { return safe(practiceArea.name()); }
		public String getColor() { return safe(practiceArea.color()); }
		public String getActiveState() { return practiceArea.active() && !practiceArea.deleted() ? "Active" : "Inactive"; }
		public String getSystemKey() { return safe(practiceArea.systemKey()); }
		public boolean deleted() { return practiceArea.deleted(); }
		public boolean active() { return practiceArea.active(); }
		public boolean global() { return practiceArea.shaleClientId() == null; }
		public String scopeLabel() { return global() ? "Global/default" : "Tenant/custom"; }
		PracticeAreaDto practiceArea() { return practiceArea; }
	}

	private record LinkTypeInput(String name, String color, boolean active, String systemKey) {}

	private record PracticeAreaInput(String name, String color, boolean active, String systemKey) {}

	private record CaseStatusInput(
			String name,
			boolean closed,
			Integer sortOrder,
			String color,
			String lifecycleKey,
			String systemKey) {}

	private void loadFromPreferences() {
		if (notificationPreferencesService == null) {
			return;
		}
		NotificationPreferences preferences = notificationPreferencesService.getForCurrentUser();
		setChecked(taskAssignedToMeCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME));
		setChecked(taskOverdueCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE));
		setChecked(taskDueTodayCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY));
		setChecked(taskDueTomorrowCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TOMORROW));
		setChecked(appUpdatesCheck, preferences.isEnabled(NotificationPreferenceKey.APP_UPDATES));
		setChecked(connectivityCheck, preferences.isEnabled(NotificationPreferenceKey.CONNECTIVITY_STATUS));
		setChecked(taskOverdueBannerCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_OVERDUE_BANNER));
		setChecked(taskDueTodayBannerCheck, preferences.isEnabled(NotificationPreferenceKey.TASK_DUE_TODAY_BANNER));
		setChecked(appUpdatesBannerCheck, preferences.isEnabled(NotificationPreferenceKey.APP_UPDATES_BANNER));
		setChecked(connectivityBannerCheck, preferences.isEnabled(NotificationPreferenceKey.CONNECTIVITY_BANNER));
		if (notificationSettingsStatusLabel != null) {
			notificationSettingsStatusLabel.setText("");
		}
	}

	private Map<NotificationPreferenceKey, Boolean> selectedValues() {
		Map<NotificationPreferenceKey, Boolean> values = new EnumMap<>(NotificationPreferenceKey.class);
		values.put(NotificationPreferenceKey.TASK_ASSIGNED_TO_ME, isChecked(taskAssignedToMeCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_OVERDUE, isChecked(taskOverdueCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TODAY, isChecked(taskDueTodayCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TOMORROW, isChecked(taskDueTomorrowCheck));
		values.put(NotificationPreferenceKey.APP_UPDATES, isChecked(appUpdatesCheck));
		values.put(NotificationPreferenceKey.CONNECTIVITY_STATUS, isChecked(connectivityCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_OVERDUE_BANNER, isChecked(taskOverdueBannerCheck));
		values.put(NotificationPreferenceKey.TASK_DUE_TODAY_BANNER, isChecked(taskDueTodayBannerCheck));
		values.put(NotificationPreferenceKey.APP_UPDATES_BANNER, isChecked(appUpdatesBannerCheck));
		values.put(NotificationPreferenceKey.CONNECTIVITY_BANNER, isChecked(connectivityBannerCheck));
		return values;
	}

	private static boolean isChecked(CheckBox checkBox) {
		return checkBox != null && checkBox.isSelected();
	}

	private static void setChecked(CheckBox checkBox, boolean selected) {
		if (checkBox != null) {
			checkBox.setSelected(selected);
		}
	}

	private boolean isAdminUser() {
		return appState != null && appState.isAdmin();
	}

	@FXML
	private VBox userAdministrationSection;

	private void updateAdminControlsVisibility() {
		boolean visible = isAdminUser();
		if (auditSection != null) {
			auditSection.setVisible(visible);
			auditSection.setManaged(visible);
		}
		if (caseStatusAdministrationSection != null) {
			caseStatusAdministrationSection.setVisible(visible);
			caseStatusAdministrationSection.setManaged(visible);
		}
		if (practiceAreaAdministrationSection != null) {
			practiceAreaAdministrationSection.setVisible(visible);
			practiceAreaAdministrationSection.setManaged(visible);
		}
		if (linkTypeAdministrationSection != null) {
			linkTypeAdministrationSection.setVisible(visible);
			linkTypeAdministrationSection.setManaged(visible);
		}
		if (userAdministrationSection != null) {
			userAdministrationSection.setVisible(visible);
			userAdministrationSection.setManaged(visible);
		}
	}
}
