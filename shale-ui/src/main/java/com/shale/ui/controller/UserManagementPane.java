package com.shale.ui.controller;

import java.util.*; import java.util.concurrent.Executor; import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import com.shale.data.dao.UserDao; import com.shale.data.service.adapter.UserServiceAdapter; import com.shale.core.service.UserServicePort; import com.shale.ui.component.*; import com.shale.ui.component.dialog.AppDialogs; import com.shale.ui.component.factory.UserCardFactory; import com.shale.ui.component.factory.UserCardFactory.UserCardModel; import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/** Feature-owned user administration surface. */
public final class UserManagementPane {
 private static final Logger LOG=LoggerFactory.getLogger(UserManagementPane.class); private static final Color DEFAULT_STATUS_COLOR=Color.rgb(108,117,125);
 private static final double TABLE_CELL_HORIZONTAL_INSETS=20;
 private final UserDao userDao; private final UserServicePort userService; private final Executor settingsLoadExecutor; private final CommittedChangeTracker changes; private final int tenantId,actorUserId; private final AtomicBoolean disposed=new AtomicBoolean();
 private final BorderPane root=new BorderPane(); private final TableView<UserManagementViewRow> userManagementTable=new TableView<>();
 private final TableColumn<UserManagementViewRow,UserManagementViewRow> userNameColumn=new TableColumn<>("Name"); private final TableColumn<UserManagementViewRow,String> userEmailColumn=new TableColumn<>("Email / login"),userInitialsColumn=new TableColumn<>("Initials"),userRolesColumn=new TableColumn<>("Roles"),userStatusColumn=new TableColumn<>("Status");
 private final CheckBox showInactiveUsersCheck=new CheckBox("Show inactive users"); private final TextField userSearchField=ControlStyles.formControl(new TextField());
 private final Button addUserButton=new Button("Add User"),editUserButton=new Button("Edit User"),refreshUsersButton=new Button("Refresh"),removeUserButton=new Button("Remove from Tenant"),deactivateUserButton=new Button("Deactivate User"),reactivateUserButton=new Button("Reactivate User"),resetPasswordButton=new Button("Reset Password"); private final Label userManagementStatusLabel=new Label(); private final FlowPane actionToolbar=new FlowPane(8,8);
 private int userManagementLoadGeneration; private final List<UserManagementViewRow> managedUserRows=new ArrayList<>(); private final UserCardFactory userManagementCardFactory=new UserCardFactory(null); private boolean userMutationRunning;
 private final AutoCloseable roleRefreshSubscription; private volatile List<UserServicePort.FirmWideRoleDefinition> firmWideRoles=List.of(); private final Map<Integer,List<UserServicePort.FirmWideRoleAssignment>> assignmentsByUser=new HashMap<>();
 UserManagementPane(UserDao dao,Executor executor,CommittedChangeTracker changes,int tenantId,int actorUserId){this.userDao=Objects.requireNonNull(dao);this.userService=new UserServiceAdapter(dao);this.settingsLoadExecutor=Objects.requireNonNull(executor);this.changes=Objects.requireNonNull(changes);if(tenantId<=0||actorUserId<=0)throw new IllegalArgumentException("Tenant and actor context are required.");this.tenantId=tenantId;this.actorUserId=actorUserId;this.roleRefreshSubscription=FirmWideRoleDefinitionRefresh.subscribe(changedTenant->{if(changedTenant==tenantId&&!disposed.get())loadManagedUsersAsync("Firm-wide role choices refreshed.");});userSearchField.setPromptText("Search name, email, initials, or role");HBox.setHgrow(userSearchField,Priority.ALWAYS);HBox filters=new HBox(10,userSearchField,showInactiveUsersCheck);filters.setAlignment(Pos.CENTER_LEFT);HBox createActions=new HBox(8,addUserButton);VBox header=new VBox(10,filters,createActions);header.getStyleClass().add("user-window-section");userManagementTable.getColumns().setAll(userNameColumn,userEmailColumn,userInitialsColumn,userRolesColumn,userStatusColumn);userManagementTable.setFixedCellSize(36);userManagementTable.setMinHeight(120);userManagementTable.setPrefHeight(430);userManagementTable.setMaxHeight(Double.MAX_VALUE);userManagementTable.getStyleClass().add("shale-table");actionToolbar.getChildren().setAll(editUserButton,deactivateUserButton,reactivateUserButton,resetPasswordButton,refreshUsersButton,removeUserButton);actionToolbar.setAlignment(Pos.CENTER_LEFT);VBox footer=new VBox(8,actionToolbar,userManagementStatusLabel);footer.getStyleClass().add("user-window-footer");userManagementStatusLabel.getStyleClass().add("user-window-metadata");root.setTop(header);root.setCenter(userManagementTable);root.setBottom(footer);BorderPane.setMargin(userManagementTable,new javafx.geometry.Insets(10,0,10,0));root.getStyleClass().addAll("strong-panel","user-window-root","user-management-window");addUserButton.setOnAction(e->onAddUser());editUserButton.setOnAction(e->onEditUser());refreshUsersButton.setOnAction(e->onRefreshUsers());removeUserButton.setOnAction(e->onRemoveUserFromTenant());deactivateUserButton.setOnAction(e->onDeactivateUser());reactivateUserButton.setOnAction(e->onReactivateUser());resetPasswordButton.setOnAction(e->onResetUserPassword());showInactiveUsersCheck.setOnAction(e->onToggleInactiveUsers());configureSemanticButtons();configureUserManagementTable();updateUserActionButtons(null);}
 Node node(){return root;} boolean mutationInFlight(){return userMutationRunning;} void open(){loadManagedUsersAsync(null);} void dispose(){disposed.set(true);userManagementLoadGeneration++;try{roleRefreshSubscription.close();}catch(Exception ignored){/* no-op subscription close */}} int tenantId(){return tenantId;} int actorUserId(){return actorUserId;}
 private void configureSemanticButtons(){ControlStyles.apply(addUserButton,ControlStyles.Purpose.PRIMARY);ControlStyles.apply(editUserButton,ControlStyles.Purpose.SECONDARY);ControlStyles.apply(deactivateUserButton,ControlStyles.Purpose.DANGER);ControlStyles.apply(reactivateUserButton,ControlStyles.Purpose.SECONDARY);ControlStyles.apply(resetPasswordButton,ControlStyles.Purpose.SECONDARY);ControlStyles.apply(refreshUsersButton,ControlStyles.Purpose.GHOST);ControlStyles.apply(removeUserButton,ControlStyles.Purpose.DANGER);}
 private static String fxColorToDb(Color c){Color x=c==null?DEFAULT_STATUS_COLOR:c;return String.format("#%02X%02X%02X",byteOf(x.getRed()),byteOf(x.getGreen()),byteOf(x.getBlue()));} private static int byteOf(double v){return Math.max(0,Math.min(255,(int)Math.round(v*255)));} private static Color dbColorToFx(String v){try{return v!=null&&v.matches("(?i)^#[0-9a-f]{6}$")?Color.web(v):DEFAULT_STATUS_COLOR;}catch(RuntimeException e){return DEFAULT_STATUS_COLOR;}} private static String rootMessage(Throwable ex){return "User management operation could not be completed.";}

    private void onAddUser() { showAddUserDialog().ifPresent(request -> mutate("Adding user…", "User added.", () -> userDao.createUser(request))); }



    private Optional<UserDao.UserCreateRequest> showAddUserDialog() {
		Dialog<UserDao.UserCreateRequest> dialog = new Dialog<>();
		dialog.setTitle("Add User");
		AppDialogs.applySecondaryDialogShell(dialog, "Add User");
		dialog.getDialogPane().getStyleClass().addAll("user-window", "user-create-window");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		TextField firstName = ControlStyles.formControl(new TextField());
		TextField lastName = ControlStyles.formControl(new TextField());
		TextField email = ControlStyles.formControl(new TextField());
		Label emailValidation = new Label("");
		emailValidation.getStyleClass().addAll("dialog-error-text", "user-window-validation");
		emailValidation.setWrapText(true);
		email.focusedProperty().addListener((obs, oldValue, focused) ->
		{
			if (!focused)
				validateAddUserEmail(email, emailValidation);
		});
		PasswordField password = ControlStyles.formControl(new PasswordField());
		TextField initials = ControlStyles.formControl(new TextField());
		ColorPicker colorPicker = ControlStyles.formControl(new ColorPicker(DEFAULT_STATUS_COLOR));
		colorPicker.setAccessibleText("User color preview and selector");
		colorPicker.getStyleClass().add("user-window-color-preview");
		CheckBox attorney = new CheckBox("Attorney");
		CheckBox admin = new CheckBox("Admin");
		attorney.getStyleClass().add("user-window-role-row"); admin.getStyleClass().add("user-window-role-row");
		GridPane grid = new GridPane();
		grid.getStyleClass().addAll("user-window-section", "user-window-identity-section");
		grid.setHgap(8);
		grid.setVgap(8);
		grid.add(new Label("First Name"), 0, 0);
		grid.add(firstName, 1, 0);
		grid.add(new Label("Last Name"), 0, 1);
		grid.add(lastName, 1, 1);
		grid.add(new Label("Email"), 0, 2);
		grid.add(email, 1, 2);
		grid.add(emailValidation, 1, 3);
		grid.add(new Label("Temporary Password"), 0, 4);
		grid.add(password, 1, 4);
		grid.add(new Label("Initials"), 0, 5);
		grid.add(initials, 1, 5);
		grid.add(new Label("Color"), 0, 6);
		grid.add(colorPicker, 1, 6);
		grid.add(attorney, 1, 7);
		grid.add(admin, 1, 8);
		Label guidance=new Label("Create an active user in the current tenant. Temporary password and application roles are administrator-only."); guidance.getStyleClass().add("user-window-guidance"); guidance.setWrapText(true);
		VBox content=new VBox(12,guidance,grid); content.getStyleClass().add("user-window-root");
		dialog.getDialogPane().setContent(content);
		styleDialogLabels(grid);
		configureDialogButtons(dialog, ButtonType.OK, ButtonType.CANCEL);
		dialog.setResultConverter(button ->
		{
			if (button != ButtonType.OK)
				return null;
			String duplicateMessage = validateAddUserEmail(email, emailValidation);
			if (!duplicateMessage.isBlank())
				throw new IllegalArgumentException(duplicateMessage);
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
		try {
			return dialog.showAndWait();
		} catch (RuntimeException ex) {
			AppDialogs.showError(dialog.getOwner(), "Add User", rootMessage(ex));
			return Optional.empty();
		}
	}

    private String validateAddUserEmail(TextField field, Label label) { String email=UserDao.normalizeEmail(trim(field==null?null:field.getText()));String message=email.contains("@")?"":"Enter a valid email address.";if(label!=null)label.setText(message);return message;}

    private void applyUserFilter() {
		if (userManagementTable == null)
			return;
		String q = userSearchField == null ? "" : trim(userSearchField.getText()).toLowerCase(java.util.Locale.ROOT);
		List<UserManagementViewRow> filtered = managedUserRows.stream().filter(r -> q.isBlank() || r.searchText().contains(q)).toList();
		userManagementTable.getItems().setAll(filtered);
		if (filtered.isEmpty())
			setUserManagementMessage(managedUserRows.isEmpty() ? "No users exist for this tenant." : "No users match the current search.");
	}

    private void onRefreshUsers() {
		loadManagedUsersAsync(null);
	}

    private void onRemoveUserFromTenant() { UserManagementViewRow selected=selectedManagedUser();if(selected==null||userMutationRunning)return;if(AppDialogs.showConfirmation(null,"Remove from Tenant","Remove "+selected.name()+" from this tenant?","They will no longer be able to sign in. Historical records will be preserved.","Remove from Tenant",AppDialogs.DialogActionKind.DANGER))mutate("Removing user from tenant…","User removed from tenant.",()->userDao.removeUserFromTenant(selected.id(),selected.rowVer()));}

    private record UserEdit(UserDao.UserUpdateRequest profile,Set<Integer> customRoleIds){}
    private void onEditUser() { UserManagementViewRow selected=selectedManagedUser();if(selected==null||userMutationRunning)return;showEditUserDialog(selected).ifPresent(request->mutate("Saving changes…","User updated.",()->{userDao.updateManagedUser(request.profile());reconcileCustomRoles(selected.id(),request.customRoleIds());}));}

    private Optional<UserEdit> showEditUserDialog(UserManagementViewRow row) {
		Dialog<UserEdit> d = new Dialog<>();
		d.setTitle("Edit User");
		AppDialogs.applySecondaryDialogShell(d, "Edit User");
		d.getDialogPane().getStyleClass().addAll("user-window", "user-admin-edit-window");
		ButtonType save = new ButtonType("Save Changes", javafx.scene.control.ButtonBar.ButtonData.OK_DONE), cancel = new ButtonType("Cancel",
				javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
		d.getDialogPane().getButtonTypes().setAll(save, cancel);
		TextField first = ControlStyles.formControl(new TextField(row.firstName())), last = ControlStyles.formControl(new TextField(row.lastName())), email = ControlStyles
				.formControl(new TextField(row.email())), phone = ControlStyles.formControl(new TextField(row.phone())), initials = ControlStyles.formControl(new TextField(row
						.initials()));
		ColorPicker color = ControlStyles.formControl(new ColorPicker(dbColorToFx(row.color())));
		color.setAccessibleText("User color preview and selector");
		color.getStyleClass().add("user-window-color-preview");
		CheckBox attorney = ControlStyles.formControl(new CheckBox("Attorney — eligible for attorney assignments")), admin = ControlStyles.formControl(new CheckBox(
				"Administrator — may manage tenant settings and users"));
		attorney.setSelected(row.attorney());
		admin.setSelected(row.admin());
		attorney.getStyleClass().add("user-window-role-row"); admin.getStyleClass().add("user-window-role-row");
		GridPane g = new GridPane();
		g.getStyleClass().addAll("user-window-section", "user-window-identity-section");
		g.setHgap(12);
		g.setVgap(10);
		g.add(new Label("Identity"), 0, 0, 2, 1);
		g.add(new Label("First name"), 0, 1);
		g.add(first, 1, 1);
		g.add(new Label("Last name"), 0, 2);
		g.add(last, 1, 2);
		g.add(new Label("Email / login"), 0, 3);
		g.add(email, 1, 3);
		g.add(new Label("Phone"), 0, 4);
		g.add(phone, 1, 4);
		g.add(new Label("Initials"), 0, 5);
		g.add(initials, 1, 5);
		g.add(new Label("User color"), 0, 6);
		g.add(color, 1, 6);
		g.add(new Label("Application roles"), 0, 7, 2, 1);
		g.add(attorney, 1, 8);
		g.add(admin, 1, 9);
		VBox customRoles=new VBox(6);Set<Integer> selectedCustom=new HashSet<>();List<UserServicePort.FirmWideRoleAssignment> existing=assignmentsByUser.getOrDefault(row.id(),List.of());for(UserServicePort.FirmWideRoleDefinition role:firmWideRoles){if(role.builtIn()||role.deleted())continue;CheckBox box=ControlStyles.formControl(new CheckBox(role.name()+(role.active()?"":" (inactive)")));boolean assigned=existing.stream().anyMatch(a->a.definitionId()==role.id()&&!a.deleted());box.setSelected(assigned);box.setDisable(!role.active()&&!assigned);box.selectedProperty().addListener((o,was,is)->{if(is)selectedCustom.add(role.id());else selectedCustom.remove(role.id());});if(assigned)selectedCustom.add(role.id());customRoles.getChildren().add(box);}g.add(new Label("Tenant-defined roles"),0,10);g.add(customRoles,1,10);
		g.add(new Label("User ID " + row.id() + " · Status " + row.getStatus() + " (managed separately)"), 0, 11, 2, 1);
		Label guidance=new Label("Update this user's identity, contact information, appearance, and application roles. Lifecycle and password actions remain separate."); guidance.getStyleClass().add("user-window-guidance"); guidance.setWrapText(true);
		VBox content=new VBox(12,guidance,g); content.getStyleClass().add("user-window-root");
		d.getDialogPane().setContent(content);
		styleDialogLabels(g);
		g.getChildren().stream().filter(n->n instanceof Label label&&label.getText()!=null&&label.getText().startsWith("User ID ")).forEach(n->n.getStyleClass().add("user-window-metadata"));
		ControlStyles.apply((ButtonBase) d.getDialogPane().lookupButton(save), ControlStyles.Purpose.PRIMARY);
		ControlStyles.apply((ButtonBase) d.getDialogPane().lookupButton(cancel), ControlStyles.Purpose.SECONDARY);
		Node saveButton = d.getDialogPane().lookupButton(save);
		saveButton.addEventFilter(ActionEvent.ACTION, e ->
		{
			boolean invalid = trim(first.getText()).isBlank() || trim(last.getText()).isBlank() || !UserDao.normalizeEmail(email.getText()).contains("@");
			ControlStyles.setInvalid(first, trim(first.getText()).isBlank());
			ControlStyles.setInvalid(last, trim(last.getText()).isBlank());
			ControlStyles.setInvalid(email, !UserDao.normalizeEmail(email.getText()).contains("@"));
			if (invalid)
				e.consume();
		});
		d.setResultConverter(b ->
		{
			if (b != save)
				return null;
			java.util.Set<Integer> roles = new java.util.HashSet<>();
			if (admin.isSelected())
				roles.add(com.shale.core.semantics.RoleSemantics.ROLE_ADMIN);
			if (attorney.isSelected())
				roles.add(com.shale.core.semantics.RoleSemantics.ROLE_ATTORNEY);
			return new UserEdit(new UserDao.UserUpdateRequest(row.id(), row.rowVer(), first.getText(), last.getText(), email.getText(), phone.getText(), initials.getText(), fxColorToDb(color
					.getValue()), roles),Set.copyOf(selectedCustom));
		});
		return d.showAndWait();
	}

    private void reconcileCustomRoles(int userId,Set<Integer> desired){List<UserServicePort.FirmWideRoleAssignment> old=assignmentsByUser.getOrDefault(userId,List.of());for(UserServicePort.FirmWideRoleDefinition role:firmWideRoles){if(role.builtIn())continue;UserServicePort.FirmWideRoleAssignment active=old.stream().filter(a->a.definitionId()==role.id()&&!a.deleted()).findFirst().orElse(null);if(desired.contains(role.id())&&active==null){UserServicePort.FirmWideRoleAssignment removed=old.stream().filter(a->a.definitionId()==role.id()&&a.deleted()).findFirst().orElse(null);if(removed==null)userService.assignFirmWideRole(new UserServicePort.FirmWideRoleAssignmentCommand(tenantId,actorUserId,userId,role.id()));else userService.restoreFirmWideRoleAssignment(new UserServicePort.FirmWideRoleAssignmentLifecycleCommand(tenantId,actorUserId,removed.id(),removed.rowVer()));}else if(!desired.contains(role.id())&&active!=null)userService.removeFirmWideRoleAssignment(new UserServicePort.FirmWideRoleAssignmentLifecycleCommand(tenantId,actorUserId,active.id(),active.rowVer()));}}

    private void onToggleInactiveUsers() {
		loadManagedUsersAsync(null);
	}

    private void onDeactivateUser() { UserManagementViewRow selected=selectedManagedUser();if(selected!=null&&AppDialogs.showConfirmation(null,"Deactivate User","Deactivate this user?","This will disable their access while preserving historical records.","Deactivate",AppDialogs.DialogActionKind.DANGER))mutate("Deactivating user…","User deactivated.",()->userDao.deactivateUser(selected.id()));}

    private void onReactivateUser() { UserManagementViewRow selected=selectedManagedUser();if(selected!=null)mutate("Reactivating user…","User reactivated.",()->userDao.reactivateUser(selected.id()));}

    private void onResetUserPassword() {
		UserManagementViewRow selected = selectedManagedUser();
		if (selected == null)
			return;
		Dialog<String> dialog = new Dialog<>();
		dialog.setTitle("Reset Password");
		AppDialogs.applySecondaryDialogShell(dialog, "Reset Password");
		dialog.getDialogPane().getStyleClass().addAll("user-window", "user-security-window");
		dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		PasswordField password = new PasswordField();
		PasswordField confirm = new PasswordField();
		Label validation = new Label("");
		validation.getStyleClass().addAll("dialog-error-text", "user-window-validation");
		validation.setWrapText(true);
		password.textProperty().addListener((obs, oldValue, newValue) -> validation.setText(""));
		confirm.textProperty().addListener((obs, oldValue, newValue) -> validation.setText(""));
		GridPane grid = new GridPane();
		grid.getStyleClass().addAll("user-window-section", "user-window-security-section");
		grid.setHgap(8);
		grid.setVgap(8);
		grid.add(new Label("New Password"), 0, 0);
		grid.add(password, 1, 0);
		grid.add(new Label("Confirm Password"), 0, 1);
		grid.add(confirm, 1, 1);
		grid.add(validation, 1, 2);
		Label guidance=new Label("Set a new password for the selected user. The change takes effect immediately after confirmation."); guidance.getStyleClass().add("user-window-guidance"); guidance.setWrapText(true);
		VBox content=new VBox(12,guidance,grid); content.getStyleClass().add("user-window-root");
		dialog.getDialogPane().setContent(content);
		styleDialogLabels(grid);
		configureDialogButtons(dialog, ButtonType.OK, ButtonType.CANCEL);
		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
		okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event ->
		{
			String message = resetPasswordValidationMessage(password.getText(), confirm.getText());
			if (!message.isBlank()) {
				validation.setText(message);
				event.consume();
			}
		});
		dialog.setResultConverter(button -> button == ButtonType.OK ? password.getText() : null);
		try {
			dialog.showAndWait().ifPresent(newPassword ->
			{
				boolean confirmed = AppDialogs.showConfirmation(null, "Reset Password", "Reset password for " + selected.name() + "?", "Password access will change immediately.",
						"Reset", AppDialogs.DialogActionKind.PRIMARY);
				if (!confirmed)
					return;
				mutate("Resetting password…", "Password successfully updated.", () -> userDao.resetPassword(selected.id(), newPassword));
			});
		} catch (RuntimeException ex) {
			AppDialogs.showError(null, "Reset Password", rootMessage(ex));
		}
	}

    static String resetPasswordValidationMessage(String password, String confirmPassword) {
		if (password == null || password.isBlank())
			return "Password is required.";
		if (confirmPassword == null || confirmPassword.isBlank())
			return "Confirm password is required.";
		if (!Objects.equals(password, confirmPassword))
			return "Passwords do not match.";
		return "";
	}

    private void mutate(String busy,String success,Runnable operation){if(userMutationRunning||disposed.get())return;userMutationRunning=true;updateUserActionButtons(userManagementTable.getSelectionModel().getSelectedItem());setUserManagementMessage(busy);settingsLoadExecutor.execute(()->{try{operation.run();Platform.runLater(()->{if(disposed.get())return;userMutationRunning=false;changes.markCommitted();loadManagedUsersAsync(success);});}catch(RuntimeException ex){LOG.warn("User management operation failed tenantId={} actorId={}",tenantId,actorUserId);Platform.runLater(()->{if(disposed.get())return;userMutationRunning=false;updateUserActionButtons(userManagementTable.getSelectionModel().getSelectedItem());setUserManagementMessage(rootMessage(ex));});}});}

    private void configureUserManagementTable() {
		if (userManagementTable == null)
			return;
		userNameColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
		userNameColumn.setCellFactory(column -> new UserNameCell(userManagementCardFactory));
		userEmailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
		userInitialsColumn.setCellValueFactory(new PropertyValueFactory<>("initials"));
		userRolesColumn.setCellValueFactory(new PropertyValueFactory<>("roles"));
		userStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
		userEmailColumn.setCellFactory(column -> new FullValueTextCell());
		userRolesColumn.setCellFactory(column -> new FullValueTextCell());
		configureColumn(userNameColumn,190,230,Double.MAX_VALUE);
		configureColumn(userEmailColumn,220,270,Double.MAX_VALUE);
		configureColumn(userInitialsColumn,70,76,100);
		configureColumn(userRolesColumn,140,190,Double.MAX_VALUE);
		configureColumn(userStatusColumn,76,88,120);
		userManagementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
		userManagementTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> updateUserActionButtons(newRow));
		if (userSearchField != null)
			userSearchField.textProperty().addListener((obs, o, n) -> applyUserFilter());
		userManagementTable.setOnMouseClicked(e ->
		{
			if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && selectedManagedUser() != null)
				onEditUser();
		});
		userManagementTable.setOnKeyPressed(e ->
		{
			if (e.getCode() == KeyCode.ENTER && userManagementTable.getSelectionModel().getSelectedItem() != null) {
				onEditUser();
				e.consume();
			}
		});
	}

	private static void configureColumn(TableColumn<?,?> column,double minimum,double preferred,double maximum){column.setResizable(true);column.setMinWidth(minimum);column.setPrefWidth(preferred);column.setMaxWidth(maximum);}

	static final class UserNameCell extends TableCell<UserManagementViewRow,UserManagementViewRow>{
		private static final PseudoClass INACTIVE=PseudoClass.getPseudoClass("inactive"); private final UserCardFactory factory;
		UserNameCell(UserCardFactory factory){this.factory=Objects.requireNonNull(factory);}
		@Override protected void updateItem(UserManagementViewRow row,boolean empty){super.updateItem(row,empty);setText(null);setGraphic(null);setTooltip(null);setStyle("");pseudoClassStateChanged(INACTIVE,false);if(empty||row==null)return;UserCard card=factory.create(new UserCardModel(row.id(),row.name(),row.color(),row.initials()),UserCardFactory.Variant.MINI);card.setInactive(row.deleted());card.useAvailableWidth();card.prefWidthProperty().bind(Bindings.max(0,widthProperty().subtract(TABLE_CELL_HORIZONTAL_INSETS)));setGraphic(card);setTooltip(new Tooltip(row.name()));}
	}

	static final class FullValueTextCell extends TableCell<UserManagementViewRow,String>{
		@Override protected void updateItem(String value,boolean empty){super.updateItem(value,empty);setGraphic(null);setStyle("");if(empty||value==null){setText(null);setTooltip(null);return;}setText(value);setTextOverrun(OverrunStyle.ELLIPSIS);setTooltip(new Tooltip(value));}
	}

    private void loadManagedUsers() {
		loadManagedUsersAsync(null);
	}

    private void loadManagedUsersAsync(String successMessage) {
		if (userDao == null || userManagementTable == null)
			return;
		final int generation = ++userManagementLoadGeneration;
		boolean includeInactive = showInactiveUsersCheck != null && showInactiveUsersCheck.isSelected();
		int selectedId = userManagementTable.getSelectionModel().getSelectedItem() == null ? 0 : userManagementTable.getSelectionModel().getSelectedItem().id();
		updateUserActionButtons(userManagementTable.getSelectionModel().getSelectedItem());
		setUserManagementMessage("Loading users…");
		settingsLoadExecutor.execute(() ->
		{
			try {
				List<UserServicePort.FirmWideRoleDefinition> loadedRoles=userService.listFirmWideRolesForAdministration(tenantId,actorUserId);
				List<UserManagementViewRow> rows = new ArrayList<>();
				Map<Integer,List<UserServicePort.FirmWideRoleAssignment>> loadedAssignments=new HashMap<>();
				for (UserDao.UserManagementRow row : userDao.listUsersForManagement(includeInactive)){List<UserServicePort.FirmWideRoleAssignment> assignments=userService.listUserFirmWideRoleAssignments(tenantId,actorUserId,row.id());loadedAssignments.put(row.id(),assignments);Set<Integer> effectiveIds=loadedRoles.stream().filter(r->r.active()&&!r.deleted()).map(UserServicePort.FirmWideRoleDefinition::id).collect(java.util.stream.Collectors.toSet());rows.add(new UserManagementViewRow(row,assignments.stream().filter(a->effectiveIds.contains(a.definitionId())).toList()));}
				Platform.runLater(() ->
				{
					if (disposed.get() || generation != userManagementLoadGeneration)
						return;
					managedUserRows.clear();
					firmWideRoles=loadedRoles;assignmentsByUser.clear();assignmentsByUser.putAll(loadedAssignments);
					managedUserRows.addAll(rows);
					applyUserFilter();
					if (selectedId > 0)
						managedUserRows.stream().filter(r -> r.id() == selectedId).findFirst().ifPresent(userManagementTable.getSelectionModel()::select);
					updateUserActionButtons(userManagementTable.getSelectionModel().getSelectedItem());
					setUserManagementMessage(successMessage != null && !successMessage.isBlank() ? successMessage : rows.isEmpty() ? "No users found for this tenant." : "");
				});
			} catch (RuntimeException ex) {
				LOG.warn("User management load failed tenantId={} actorId={}",tenantId,actorUserId);
				Platform.runLater(() ->
				{
					if (disposed.get() || generation != userManagementLoadGeneration)
						return;
					userManagementTable.getItems().clear();
					updateUserActionButtons(null);
					setUserManagementMessage("Failed to load users. " + rootMessage(ex));
				});
			}
		});
	}

    private UserManagementViewRow selectedManagedUser() {
		UserManagementViewRow selected = userManagementTable == null ? null : userManagementTable.getSelectionModel().getSelectedItem();
		if (selected == null)
			setUserManagementMessage("Select a user first.");
		return selected;
	}

    private void updateUserActionButtons(UserManagementViewRow selected) {
		boolean has = selected != null && !selected.removed() && !userMutationRunning;
		boolean self = has && selected.id() == actorUserId;
		if (editUserButton != null)
			editUserButton.setDisable(!has);
		if (deactivateUserButton != null)
			deactivateUserButton.setDisable(!has || selected.deleted() || self);
		if (reactivateUserButton != null)
			reactivateUserButton.setDisable(!has || !selected.deleted());
		if (resetPasswordButton != null)
			resetPasswordButton.setDisable(!has || selected.deleted());
		if (removeUserButton != null)
			removeUserButton.setDisable(!has || self);
		if (addUserButton != null)
			addUserButton.setDisable(userMutationRunning);
		if (refreshUsersButton != null)
			refreshUsersButton.setDisable(userMutationRunning);
	}

    private void setUserManagementMessage(String message) {
		if (userManagementStatusLabel != null)
			userManagementStatusLabel.setText(message == null ? "" : message);
	}

    private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String safe(String value) {
		return value == null ? "" : value;
	}

    public static final class UserManagementViewRow {
        private final UserDao.UserManagementRow row;

        private final List<UserServicePort.FirmWideRoleAssignment> assignments;
        UserManagementViewRow(UserDao.UserManagementRow row) {this(row,List.of());}
        UserManagementViewRow(UserDao.UserManagementRow row,List<UserServicePort.FirmWideRoleAssignment> assignments) {this.row = row;this.assignments=List.copyOf(assignments);}

        public int getId() {
            return row.id();
        }

        public int id() {
            return row.id();
        }

        public String getName() {
            return safe(row.name());
        }

        public String name() {
            return safe(row.name());
        }

        public String getEmail() {
            return safe(row.email());
        }

        public String email() {
            return getEmail();
        }

        public String firstName() {
            return safe(row.firstName());
        }

        public String lastName() {
            return safe(row.lastName());
        }

        public String phone() {
            return safe(row.phone());
        }

        public String initials() {
            return safe(row.initials());
        }

        public String color() {
            return safe(row.color());
        }

        public String getInitials() {
            return initials();
        }

        public String getRoles() {
            List<String> names=new ArrayList<>();if(row.admin())names.add("Administrator");if(row.attorney())names.add("Attorney");assignments.stream().filter(a->!a.deleted()).map(UserServicePort.FirmWideRoleAssignment::roleName).forEach(names::add);return String.join(", ",names);
        }

        public String getStatus() {
            return row.deleted() ? "Inactive" : "Active";
        }

        public boolean deleted() {
            return row.deleted();
        }

        public boolean removed() {
            return row.removed();
        }

        public boolean admin() {
            return row.admin();
        }

        public boolean attorney() {
            return row.attorney();
        }

        public byte[] rowVer() {
            return row.rowVer() == null ? null : row.rowVer().clone();
        }

        String searchText() {
            return (name() + " " + email() + " " + initials() + " " + getRoles() + " " + id()).toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static void styleDialogLabels(GridPane grid){for(Node node:grid.getChildren())if(node instanceof Label label&&!label.getStyleClass().contains("dialog-error-text"))label.getStyleClass().add("user-window-field-label");}
    private static void configureDialogButtons(Dialog<?> dialog,ButtonType primary,ButtonType secondary){ControlStyles.apply((ButtonBase)dialog.getDialogPane().lookupButton(primary),ControlStyles.Purpose.PRIMARY);ControlStyles.apply((ButtonBase)dialog.getDialogPane().lookupButton(secondary),ControlStyles.Purpose.SECONDARY);}

}
