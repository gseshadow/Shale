package com.shale.ui.component.dialog;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.shale.core.dto.CaseTeamMembershipDto;
import com.shale.core.dto.CaseTeamRoleDefinitionDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.CaseServicePort.CaseTeamUpdateCommand;
import com.shale.core.service.CaseServicePort.CaseTeamUpdateMember;
import com.shale.data.dao.CaseDao;
import com.shale.ui.component.UserCard;
import com.shale.ui.component.factory.UserCardFactory;
import com.shale.ui.component.factory.UserCardFactory.UserCardModel;
import com.shale.ui.controller.CaseTeamRoleManagementLauncher;
import com.shale.ui.theme.ThemeManager;
import com.shale.ui.util.ControlAvailability;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

/** Staged, roleless and multi-role Case Team editor presented in the A.2 secondary-window shell. */
public final class TeamEditorDialog {
    static final String TITLE = "Edit Case Team";
    static final double PREFERRED_WIDTH = 980;
    static final double PREFERRED_HEIGHT = 700;
    static final double MIN_WIDTH = 620;
    static final double MIN_HEIGHT = 500;
    static final double STACK_BREAKPOINT = 760;

    private final Stage stage;
    private final CaseTeamEditorState state;
    private final CaseServicePort service;
    private final int tenantId;
    private final int actorId;
    private final long caseId;
    private final Runnable saved;
    private final ListView<CaseTeamEditorState.Member> members = new ListView<>();
    private final ListView<CaseDao.UserRow> results = new ListView<>();
    private final TextField search = new TextField();
    private final Label searchEmpty = new Label();
    private final Label error = new Label();
    private final Button save = new Button("Save");
    private final Button cancel = new Button("Cancel");
    private final AtomicBoolean saving = new AtomicBoolean();
    private boolean closing;

    public TeamEditorDialog(Stage owner, CaseServicePort service, int tenantId, int actorId, long caseId,
            List<CaseDao.UserRow> users, List<CaseTeamMembershipDto> baseline,
            List<CaseTeamRoleDefinitionDto> roles, Runnable saved) {
        this(owner, service, tenantId, actorId, caseId, users, baseline, roles, saved, null, false, () -> { });
    }

    public TeamEditorDialog(Stage owner, CaseServicePort service, int tenantId, int actorId, long caseId,
            List<CaseDao.UserRow> users, List<CaseTeamMembershipDto> baseline,
            List<CaseTeamRoleDefinitionDto> roles, Runnable saved,
            CaseTeamRoleManagementLauncher roleLauncher, boolean administrator, Runnable rolesChanged) {
        this.service = Objects.requireNonNull(service);
        this.tenantId = tenantId;
        this.actorId = actorId;
        this.caseId = caseId;
        this.saved = saved == null ? () -> { } : saved;
        state = new CaseTeamEditorState(users, baseline, roles);

        stage = new Stage();
        AppDialogs.applySecondaryWindowChrome(stage);
        stage.initOwner(Objects.requireNonNull(owner));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(TITLE);
        stage.setResizable(true);

        Label guidance = new Label("Search available tenant users, then assign one or more case roles. Changes are staged until Save.");
        guidance.setWrapText(true);
        guidance.getStyleClass().add("team-window-guidance");

        VBox searchRegion = buildSearchRegion();
        VBox assignedRegion = buildAssignedRegion(roleLauncher, administrator, rolesChanged);
        SplitPane workspace = new SplitPane(searchRegion, assignedRegion);
        workspace.setDividerPositions(0.39);
        workspace.getStyleClass().add("team-window-workspace");
        VBox.setVgrow(workspace, Priority.ALWAYS);

        error.getStyleClass().addAll("team-window-failure", "team-window-validation");
        error.setWrapText(true);
        error.setVisible(false);
        error.setManaged(false);

        ControlStyles.apply(cancel, ControlStyles.Purpose.SECONDARY);
        ControlStyles.apply(save, ControlStyles.Purpose.PRIMARY);
        cancel.setOnAction(event -> requestClose());
        save.setOnAction(event -> save());
        save.setDefaultButton(true);
        Region footerSpacer = new Region();
        HBox.setHgrow(footerSpacer, Priority.ALWAYS);
        HBox footer = new HBox(10, footerSpacer, cancel, save);
        footer.getStyleClass().add("team-window-footer");

        VBox body = new VBox(12, guidance, workspace, error, footer);
        body.getStyleClass().add("team-window-body");
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox shell = AppDialogs.createSecondaryWindowShell(stage, TITLE, this::requestClose, body);
        shell.getStyleClass().add("team-window-root");
        shell.setPrefSize(PREFERRED_WIDTH, PREFERRED_HEIGHT);
        shell.setMinSize(MIN_WIDTH, MIN_HEIGHT);
        AppDialogs.installSecondaryWindowResizeHandlers(stage, shell);

        double width = Math.min(PREFERRED_WIDTH, screenWidth(owner) - 60);
        double height = Math.min(PREFERRED_HEIGHT, screenHeight(owner) - 60);
        Scene scene = new Scene(shell, Math.max(MIN_WIDTH, width), Math.max(MIN_HEIGHT, height));
        ThemeManager.application().register(scene);
        scene.widthProperty().addListener((observable, oldWidth, newWidth) ->
                workspace.setOrientation(newWidth.doubleValue() < STACK_BREAKPOINT ? Orientation.VERTICAL : Orientation.HORIZONTAL));
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                requestClose();
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setOnCloseRequest(event -> {
            event.consume();
            requestClose();
        });
        stage.setOnHidden(event -> ThemeManager.application().unregister(scene));
        refreshMembers();
    }

    private VBox buildSearchRegion() {
        Label title = new Label("Available users");
        title.getStyleClass().add("team-window-region-title");
        Label help = new Label("Search by display name or initials. Select a result to add it roleless.");
        help.setWrapText(true);
        help.getStyleClass().add("team-window-region-guidance");
        search.setPromptText("Search active users by name or initials…");
        search.setAccessibleText("Search available Case Team users by name or initials");
        search.getStyleClass().add("team-window-search-field");
        ControlStyles.formControl(search);
        searchEmpty.getStyleClass().add("team-window-empty");
        results.setFixedCellSize(54);
        results.setMinHeight(150);
        results.setPrefHeight(220);
        results.setMaxHeight(Double.MAX_VALUE);
        results.getStyleClass().add("team-window-results");
        results.setAccessibleText("Available users search results");
        results.setPlaceholder(searchEmpty);
        results.setCellFactory(view -> new SearchResultCell());
        VBox.setVgrow(results, Priority.ALWAYS);
        results.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                event.consume();
                addSelectedResult();
            }
        });
        search.textProperty().addListener((observable, oldText, newText) -> refreshResults());
        search.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN && !results.getItems().isEmpty()) {
                results.requestFocus();
                results.getSelectionModel().selectFirst();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER && !results.getItems().isEmpty()) {
                results.getSelectionModel().selectFirst();
                addSelectedResult();
                event.consume();
            }
        });
        VBox region = new VBox(8, title, help, search, results);
        region.getStyleClass().addAll("team-window-region", "team-window-search-region");
        region.setAccessibleText("Available users search region");
        return region;
    }

    private VBox buildAssignedRegion(CaseTeamRoleManagementLauncher roleLauncher, boolean administrator,
            Runnable rolesChanged) {
        Label title = new Label("Assigned Case Team");
        title.getStyleClass().add("team-window-region-title");
        Label help = new Label("Members may have multiple roles or remain roleless.");
        help.getStyleClass().add("team-window-region-guidance");
        Button manageRoles = new Button("Manage Roles");
        ControlStyles.apply(manageRoles, ControlStyles.Purpose.SECONDARY, ControlStyles.Size.SMALL);
        HBox management = new HBox(manageRoles);
        boolean available = false;
        if (administrator && roleLauncher != null) {
            available = tenantId > 0 && actorId > 0;
        }
        ControlAvailability.apply(manageRoles, management, available, event -> {
            if (state.dirty()) {
                showError("Save or Cancel the pending Case Team changes before managing role definitions.");
                return;
            }
            roleLauncher.open(stage, tenantId, actorId, result -> {
                if (result.changed()) {
                    closing = true;
                    stage.close();
                    rolesChanged.run();
                }
            });
        });
        members.setCellFactory(view -> new MemberCell());
        Label empty = new Label("No team members yet. Add someone from Available users.");
        empty.getStyleClass().add("team-window-empty");
        members.setPlaceholder(empty);
        members.getStyleClass().add("team-window-members");
        members.setAccessibleText("Assigned Case Team members and roles");
        VBox.setVgrow(members, Priority.ALWAYS);
        VBox region = new VBox(8, title, help, management, new Separator(), members);
        region.getStyleClass().addAll("team-window-region", "team-window-assigned-region");
        region.setAccessibleText("Assigned Case Team members region");
        return region;
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisible(true);
        error.setManaged(true);
    }

    private void addSelectedResult() {
        CaseDao.UserRow user = results.getSelectionModel().getSelectedItem();
        if (user == null || state.addMember(user) == null) return;
        search.clear();
        refreshMembers();
        search.requestFocus();
    }

    private void refreshResults() {
        List<CaseDao.UserRow> candidates = state.search(search.getText());
        searchEmpty.setText(candidates.isEmpty() && !search.getText().isBlank()
                ? "No users match this search." : "No available users.");
        searchEmpty.getStyleClass().removeAll("team-window-empty", "team-window-filtered-empty");
        searchEmpty.getStyleClass().add(search.getText().isBlank() ? "team-window-empty" : "team-window-filtered-empty");
        results.setItems(FXCollections.observableArrayList(candidates));
    }

    private void refreshMembers() {
        members.setItems(FXCollections.observableArrayList(state.members()));
        members.refresh();
        refreshResults();
    }

    private void requestClose() {
        if (closing || saving.get()) return;
        if (!state.dirty() || confirmDiscard()) {
            closing = true;
            stage.close();
        }
    }

    private boolean confirmDiscard() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Discard your unsaved Case Team changes?", ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(stage);
        alert.setTitle("Discard changes?");
        alert.setHeaderText("Your staged changes have not been saved.");
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void save() {
        if (!saving.compareAndSet(false, true)) return;
        setSaving(true);
        List<CaseTeamUpdateMember> desired = state.members().stream()
                .map(member -> new CaseTeamUpdateMember(member.membershipId(), member.user().id(),
                        member.rowVer(), List.copyOf(member.roleIds())))
                .toList();
        new Thread(() -> {
            try {
                service.updateCaseTeam(new CaseTeamUpdateCommand(tenantId, actorId, caseId, desired));
                Platform.runLater(() -> {
                    saving.set(false);
                    if (closing || !stage.isShowing()) return;
                    saved.run();
                    closing = true;
                    stage.close();
                });
            } catch (RuntimeException exception) {
                Platform.runLater(() -> {
                    saving.set(false);
                    if (closing || !stage.isShowing()) return;
                    setSaving(false);
                    showError(userMessage(exception));
                });
            }
        }, "case-team-save-" + caseId).start();
    }

    private void setSaving(boolean busy) {
        save.setDisable(busy);
        cancel.setDisable(busy);
        search.setDisable(busy);
        results.setDisable(busy);
        members.setDisable(busy);
        save.setText(busy ? "Saving…" : "Save");
    }

    private static String userMessage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IllegalArgumentException || cause instanceof IllegalStateException) {
                String message = cause.getMessage();
                if (message != null && !message.isBlank()) return message;
            }
        }
        return "The Case Team could not be saved. Reload and try again.";
    }

    private final class SearchResultCell extends ListCell<CaseDao.UserRow> {
        @Override
        protected void updateItem(CaseDao.UserRow user, boolean empty) {
            super.updateItem(user, empty);
            if (empty || user == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            UserCard card = new UserCardFactory(id -> {
                getListView().getSelectionModel().select(getIndex());
                addSelectedResult();
            }).create(new UserCardModel(user.id(), user.displayName(), user.color(), null), UserCardFactory.Variant.MINI);
            card.useAvailableWidth();
            card.setAccessibleText("Add " + user.displayName() + " to the Case Team");
            HBox.setHgrow(card, Priority.ALWAYS);
            Label add = new Label("Add");
            add.getStyleClass().add("team-window-result-add");
            HBox wrapper = new HBox(8, card, add);
            wrapper.getStyleClass().add("team-window-result-card");
            wrapper.setAccessibleText("Add " + user.displayName() + " to the Case Team");
            setGraphic(wrapper);
            setAccessibleText(wrapper.getAccessibleText());
        }
    }

    private final class MemberCell extends ListCell<CaseTeamEditorState.Member> {
        @Override
        protected void updateItem(CaseTeamEditorState.Member member, boolean empty) {
            super.updateItem(member, empty);
            if (empty || member == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }
            Label name = new Label(member.user().displayName());
            name.getStyleClass().add("team-window-member-name");
            name.setWrapText(true);
            name.setMaxWidth(Double.MAX_VALUE);
            FlowPane chips = new FlowPane(6, 6);
            chips.getStyleClass().add("team-window-role-area");
            chips.setAccessibleText("Roles for " + member.user().displayName());
            if (member.roleIds().isEmpty()) {
                Label none = new Label("No roles assigned");
                none.getStyleClass().add("team-window-roleless");
                none.setAccessibleText(member.user().displayName() + " has no assigned roles");
                chips.getChildren().add(none);
            } else {
                member.roleIds().forEach(id -> chips.getChildren().add(roleChip(member, id)));
            }
            MenuButton addRole = new MenuButton("Add role");
            ControlStyles.apply(addRole, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
            addRole.setAccessibleText("Add role for " + member.user().displayName());
            for (CaseTeamRoleDefinitionDto definition : state.availableRoles(member.user().id())) {
                MenuItem item = new MenuItem(definition.name());
                item.setOnAction(event -> assignRole(member, definition));
                addRole.getItems().add(item);
            }
            addRole.setDisable(addRole.getItems().isEmpty());
            Button remove = new Button("Remove member");
            ControlStyles.apply(remove, ControlStyles.Purpose.DANGER, ControlStyles.Size.SMALL);
            remove.setAccessibleText("Remove " + member.user().displayName() + " from the Case Team");
            remove.setTooltip(new Tooltip("Remove member and all roles when saved"));
            remove.setOnAction(event -> {
                event.consume();
                state.removeMember(member.user().id());
                refreshMembers();
            });
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox actions = new HBox(8, addRole, gap, remove);
            VBox card = new VBox(7, name, chips, actions);
            card.getStyleClass().add("team-window-member-card");
            card.setStyle(CaseTeamCardStyles.memberCardStyle(member.user().color()));
            card.setAccessibleText("Case Team member " + member.user().displayName());
            setGraphic(card);
            setAccessibleText(card.getAccessibleText());
        }
    }

    private Node roleChip(CaseTeamEditorState.Member member, int id) {
        CaseTeamRoleDefinitionDto definition = state.definition(id);
        String label = definition == null ? "Unknown role" : definition.name();
        boolean inactive = definition == null || !definition.active() || definition.deleted();
        Label text = new Label(label + (inactive ? " · Inactive" : ""));
        text.setWrapText(true);
        Circle dot = new Circle(4, color(definition == null ? null : definition.color()));
        Button remove = new Button("×");
        ControlStyles.apply(remove, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        remove.setAccessibleText("Remove " + label + " role from " + member.user().displayName());
        remove.setTooltip(new Tooltip("Remove role; the member stays on the team"));
        remove.setOnAction(event -> {
            event.consume();
            state.removeRole(member.user().id(), id);
            refreshMembers();
        });
        HBox chip = new HBox(5, dot, text, remove);
        chip.getStyleClass().addAll("team-window-role-chip",
                inactive ? "team-window-role-chip-inactive" : "team-window-role-chip-active");
        chip.setAccessibleText(label + (inactive ? ", inactive historical role" : ", active role"));
        return chip;
    }

    private void assignRole(CaseTeamEditorState.Member target, CaseTeamRoleDefinitionDto role) {
        boolean confirmed = true;
        if (CaseTeamEditorState.RESPONSIBLE_ATTORNEY.equals(role.systemKey())) {
            CaseTeamEditorState.Member current = state.responsibleAttorney();
            if (current != null && current != target) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "Move the Responsible Attorney role from " + current.user().displayName() + " to "
                                + target.user().displayName() + "? Both people will remain on the team.",
                        ButtonType.CANCEL, ButtonType.OK);
                alert.initOwner(stage);
                alert.setHeaderText("Move Responsible Attorney?");
                confirmed = alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
            }
        }
        if (state.addRole(target.user().id(), role.id(), confirmed)) refreshMembers();
    }

    private static Color color(String value) {
        try {
            return value == null ? Color.web("#64748B") : Color.web(value);
        } catch (IllegalArgumentException exception) {
            return Color.web("#64748B");
        }
    }

    private static double screenWidth(Stage owner) {
        return ownerScreen(owner).getVisualBounds().getWidth();
    }

    private static double screenHeight(Stage owner) {
        return ownerScreen(owner).getVisualBounds().getHeight();
    }

    private static Screen ownerScreen(Stage owner) {
        return Screen.getScreensForRectangle(owner.getX(), owner.getY(), Math.max(1, owner.getWidth()),
                Math.max(1, owner.getHeight())).stream().findFirst().orElse(Screen.getPrimary());
    }
}
