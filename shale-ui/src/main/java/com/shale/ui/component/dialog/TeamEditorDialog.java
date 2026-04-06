package com.shale.ui.component.dialog;

import java.util.*;

import com.shale.core.semantics.RoleSemantics;
import com.shale.data.dao.CaseDao;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

public final class TeamEditorDialog {

	private static final int ROLE_RESPONSIBLE_ATTORNEY = RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY;
	private static final int ROLE_PRELITIGATION_STAFF = 5;
	private static final int ROLE_ATTORNEY = RoleSemantics.ROLE_ATTORNEY;
	private static final int ROLE_LEGAL_ASSISTANT = 11;
	private static final int ROLE_PARALEGAL = 12;
	private static final int ROLE_LAW_CLERK = 13;
	private static final int ROLE_CO_COUNSEL = 14;

	private static final List<RoleOption> ROLE_OPTIONS = List.of(
			new RoleOption(ROLE_ATTORNEY, "Attorney"),
			new RoleOption(ROLE_CO_COUNSEL, "Co-counsel"),
			new RoleOption(ROLE_LEGAL_ASSISTANT, "Legal Assistant"),
			new RoleOption(ROLE_PARALEGAL, "Paralegal"),
			new RoleOption(ROLE_LAW_CLERK, "Law Clerk"),
			new RoleOption(ROLE_PRELITIGATION_STAFF, "Prelitigation Staff")
	);

	public record TeamAssignment(int userId, int roleId) {
	}

	public record Result(List<TeamAssignment> assignments) {
	}

	private record RoleOption(int roleId, String label) {
		@Override
		public String toString() {
			return label;
		}
	}

	private static final class AssignedRow {
		final CaseDao.UserRow user;
		int roleId;

		AssignedRow(CaseDao.UserRow user, int roleId) {
			this.user = user;
			this.roleId = roleId;
		}
	}

	private final Stage stage;
	private Optional<Result> result = Optional.empty();

	private final ListView<CaseDao.UserRow> lvAvailable = new ListView<>();
	private final ListView<AssignedRow> lvAssigned = new ListView<>();

	private final ComboBox<RoleOption> cbRole = new ComboBox<>();
	private final ComboBox<CaseDao.UserRow> cbPrimary = new ComboBox<>();

	private final ObservableList<CaseDao.UserRow> availableItems = FXCollections.observableArrayList();
	private final ObservableList<AssignedRow> assignedItems = FXCollections.observableArrayList();

	private final Set<Integer> attorneyUserIds;

	public TeamEditorDialog(
			Stage owner,
			List<CaseDao.UserRow> allUsers,
			List<CaseDao.CaseUserRoleRow> assignedRows,
			Set<Integer> attorneyUserIds) {
		this.attorneyUserIds = (attorneyUserIds == null) ? Set.of() : attorneyUserIds;

		this.stage = new Stage();
		stage.initOwner(owner);
		stage.initModality(Modality.APPLICATION_MODAL);
		stage.setTitle("Edit Team");

		cbRole.setItems(FXCollections.observableArrayList(ROLE_OPTIONS));

		List<CaseDao.UserRow> safeAllUsers = (allUsers == null) ? List.of() : allUsers;
		List<CaseDao.CaseUserRoleRow> safeAssigned = (assignedRows == null) ? List.of() : assignedRows;

		// Map current assigned: userId -> roleId, and detect current primary (role 4)
		Map<Integer, Integer> roleByUser = new HashMap<>();
		Integer primaryUserId = null;

		for (var r : safeAssigned) {
			if (r == null)
				continue;
			roleByUser.put(r.userId(), r.roleId());
			if (r.roleId() == ROLE_RESPONSIBLE_ATTORNEY) {
				primaryUserId = r.userId();
			}
		}

		// Build assigned list from allUsers + roleByUser
		Set<Integer> assignedIds = roleByUser.keySet();
		for (var u : safeAllUsers) {
			if (u == null)
				continue;

			if (assignedIds.contains(u.id())) {
				int rid = roleByUser.getOrDefault(u.id(), ROLE_ATTORNEY);
				assignedItems.add(new AssignedRow(u, rid));
			} else {
				availableItems.add(u);
			}
		}

		sortLists();

		lvAvailable.setItems(availableItems);
		lvAssigned.setItems(assignedItems);

		lvAvailable.setPrefWidth(280);
		lvAssigned.setPrefWidth(360);

		lvAvailable.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(CaseDao.UserRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : safeText(item.displayName()));
			}
		});

		lvAssigned.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(AssignedRow item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null || item.user == null) {
					setText(null);
				} else {
					setText(safeText(item.user.displayName()) + " — " + roleName(item.roleId));
				}
			}
		});

		Button btnAdd = new Button("→");
		Button btnRemove = new Button("←");

		final Integer[] primaryUserIdRef = new Integer[] { primaryUserId };

		// ✅ ADD: default role depends on attorney status
		btnAdd.setOnAction(e ->
		{
			var sel = lvAvailable.getSelectionModel().getSelectedItem();
			if (sel == null)
				return;

			availableItems.remove(sel);

			int defaultRole = this.attorneyUserIds.contains(sel.id())
					? ROLE_ATTORNEY
					: ROLE_LEGAL_ASSISTANT; // pick your preferred non-attorney default

			assignedItems.add(new AssignedRow(sel, defaultRole));
			sortLists();
			rebuildPrimaryOptions(primaryUserIdRef[0]);
		});

		btnRemove.setOnAction(e ->
		{
			var sel = lvAssigned.getSelectionModel().getSelectedItem();
			if (sel == null)
				return;

			assignedItems.remove(sel);
			availableItems.add(sel.user);
			sortLists();

			if (primaryUserIdRef[0] != null && sel.user.id() == primaryUserIdRef[0].intValue()) {
				primaryUserIdRef[0] = null;
			}

			rebuildPrimaryOptions(primaryUserIdRef[0]);
		});

		// Role selection applies to selected assigned row
		lvAssigned.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) ->
		{
			if (newV == null) {
				cbRole.getSelectionModel().clearSelection();
				return;
			}
			for (var opt : cbRole.getItems()) {
				if (opt.roleId() == newV.roleId) {
					cbRole.getSelectionModel().select(opt);
					break;
				}
			}
		});

		cbRole.setOnAction(e ->
		{
			var selAssigned = lvAssigned.getSelectionModel().getSelectedItem();
			var selRole = cbRole.getSelectionModel().getSelectedItem();
			if (selAssigned == null || selRole == null)
				return;

			selAssigned.roleId = selRole.roleId();
			lvAssigned.refresh();

			// If they changed someone to Responsible Attorney inside the list (rare), refresh primary
			// list
			rebuildPrimaryOptions(primaryUserIdRef[0]);
		});

		// ✅ Show only displayName in primary dropdown
		cbPrimary.setConverter(new javafx.util.StringConverter<>() {
			@Override
			public String toString(CaseDao.UserRow u) {
				return (u == null) ? "" : safeText(u.displayName());
			}

			@Override
			public CaseDao.UserRow fromString(String s) {
				return null;
			}
		});

		cbPrimary.setCellFactory(lv -> new ListCell<>() {
			@Override
			protected void updateItem(CaseDao.UserRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : safeText(item.displayName()));
			}
		});
		cbPrimary.setButtonCell(new ListCell<>() {
			@Override
			protected void updateItem(CaseDao.UserRow item, boolean empty) {
				super.updateItem(item, empty);
				setText(empty || item == null ? null : safeText(item.displayName()));
			}
		});

		// ✅ Primary dropdown (filtered to attorneys only)
		rebuildPrimaryOptions(primaryUserIdRef[0]);

//		HBox primaryRow = new HBox(10, new Label("Primary (Responsible Attorney):"), cbPrimary);
		HBox.setHgrow(cbPrimary, Priority.ALWAYS);
		cbPrimary.setMaxWidth(Double.MAX_VALUE);
		cbPrimary.setPromptText("None");

		Button btnClearPrimary = new Button("Clear");
		btnClearPrimary.setOnAction(e -> cbPrimary.getSelectionModel().clearSelection());

		HBox primaryRow = new HBox(10,
				new Label("Responsible Attorney (optional):"),
				cbPrimary,
				btnClearPrimary);

		VBox left = new VBox(6, new Label("Available"), lvAvailable);
		VBox right = new VBox(6,
				new Label("Assigned"),
				lvAssigned,
				new Separator(),
				new Label("Selected member role"),
				cbRole
		);

		VBox moveButtons = new VBox(10, btnAdd, btnRemove);
		moveButtons.setPadding(new Insets(30, 10, 0, 10));

		HBox lists = new HBox(10, left, moveButtons, right);

		Button btnCancel = new Button("Cancel");
		Button btnSave = new Button("Save");

		btnCancel.setOnAction(e ->
		{
			result = Optional.empty();
			stage.close();
		});

		btnSave.setOnAction(e ->
		{
			Integer primaryId = (cbPrimary.getSelectionModel().getSelectedItem() == null)
					? null
					: cbPrimary.getSelectionModel().getSelectedItem().id();

			List<TeamAssignment> out = new ArrayList<>();
			for (var a : assignedItems) {
				int rid = (primaryId != null && a.user.id() == primaryId.intValue())
						? ROLE_RESPONSIBLE_ATTORNEY
						: normalizeRoleForSave(a.roleId);
				out.add(new TeamAssignment(a.user.id(), rid));
			}

			result = Optional.of(new Result(out));
			stage.close();
		});

		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);

		HBox bottom = new HBox(10, spacer, btnCancel, btnSave);

		VBox root = new VBox(12, lists, primaryRow, bottom);
		root.setPadding(new Insets(12));

		Scene scene = new Scene(root, 780, 540);
		scene.getStylesheets().add(Objects.requireNonNull(
				getClass().getResource("/css/app.css")).toExternalForm());
		stage.setScene(scene);
	}

	public Optional<Result> showAndWaitForResult() {
		stage.showAndWait();
		return result;
	}

	private void rebuildPrimaryOptions(Integer preferredPrimaryUserId) {
		List<CaseDao.UserRow> assignedUsers = assignedItems.stream()
				.map(a -> a.user)
				.filter(Objects::nonNull)
				.toList();

		// ONLY attorneys
		List<CaseDao.UserRow> eligible = assignedUsers.stream()
				.filter(u -> attorneyUserIds == null || attorneyUserIds.contains(u.id()))
				.toList();

		cbPrimary.setItems(FXCollections.observableArrayList(eligible));

		if (eligible.isEmpty()) {
			cbPrimary.getSelectionModel().clearSelection();
			return;
		}

		CaseDao.UserRow toSelect = null;

		if (preferredPrimaryUserId != null) {
			for (var u : eligible) {
				if (u.id() == preferredPrimaryUserId.intValue()) {
					toSelect = u;
					break;
				}
			}
		}

		if (toSelect == null) {
			Integer raId = findResponsibleAttorneyUserId();
			if (raId != null) {
				for (var u : eligible) {
					if (u.id() == raId.intValue()) {
						toSelect = u;
						break;
					}
				}
			}
		}

		if (toSelect == null) {
			cbPrimary.getSelectionModel().clearSelection();
		} else {
			cbPrimary.getSelectionModel().select(toSelect);
		}
	}

	private Integer findResponsibleAttorneyUserId() {
		for (var a : assignedItems) {
			if (RoleSemantics.isResponsibleAttorneyRoleId(a.roleId))
				return a.user.id();
		}
		return null;
	}

	private static int normalizeRoleForSave(int roleId) {
		return RoleSemantics.normalizeCaseTeamRoleForSave(roleId);
	}

	private void sortLists() {
		availableItems.sort(Comparator.comparing(u -> safeText(u.displayName()), String.CASE_INSENSITIVE_ORDER));
		assignedItems.sort(Comparator.comparing(a -> safeText(a.user.displayName()), String.CASE_INSENSITIVE_ORDER));
	}

	private static String roleName(int roleId) {
		return switch (roleId) {
		case ROLE_RESPONSIBLE_ATTORNEY -> RoleSemantics.roleLabel(roleId);
		case ROLE_PRELITIGATION_STAFF -> "Prelitigation Staff";
		case ROLE_ATTORNEY -> RoleSemantics.roleLabel(roleId);
		case ROLE_LEGAL_ASSISTANT -> "Legal Assistant";
		case ROLE_PARALEGAL -> "Paralegal";
		case ROLE_LAW_CLERK -> "Law Clerk";
		case ROLE_CO_COUNSEL -> "Co-counsel";
		default -> "Role " + roleId;
		};
	}

	private static String safeText(String s) {
		return s == null ? "" : s;
	}
}
