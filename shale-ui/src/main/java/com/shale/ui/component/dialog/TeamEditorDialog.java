package com.shale.ui.component.dialog;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.shale.core.dto.*;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.CaseServicePort.*;
import com.shale.data.dao.CaseDao;
import com.shale.ui.util.ControlStyles;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.*;

/** Modern staged, roleless and multi-role Case Team editor. */
public final class TeamEditorDialog {
    private final Stage stage;
    private final CaseTeamEditorState state;
    private final CaseServicePort service;
    private final int tenantId, actorId;
    private final long caseId;
    private final Runnable saved;
    private final ListView<CaseTeamEditorState.Member> members = new ListView<>();
    private final ListView<CaseDao.UserRow> results = new ListView<>();
    private final TextField search = new TextField();
    private final Label searchEmpty = new Label("No matching users");
    private final Label error = new Label();
    private final Button save = new Button("Save"), cancel = new Button("Cancel");
    private final AtomicBoolean saving = new AtomicBoolean();
    private boolean closing;

    public TeamEditorDialog(Stage owner, CaseServicePort service, int tenantId, int actorId, long caseId,
            List<CaseDao.UserRow> users, List<CaseTeamMembershipDto> baseline,
            List<CaseTeamRoleDefinitionDto> roles, Runnable saved) {
        this.service=Objects.requireNonNull(service);this.tenantId=tenantId;this.actorId=actorId;this.caseId=caseId;this.saved=saved==null?()->{}:saved;
        state=new CaseTeamEditorState(users,baseline,roles);
        stage=new Stage();AppDialogs.applySecondaryWindowChrome(stage);stage.initOwner(owner);stage.initModality(Modality.APPLICATION_MODAL);stage.setTitle("Case Team");
        Label heading=new Label("Case Team");heading.getStyleClass().add("case-team-editor-heading");
        Label support=new Label("Add people to the case and manage any number of roles for each team member.");support.setWrapText(true);support.getStyleClass().add("case-team-editor-support");
        Label addLabel=new Label("Add team member");addLabel.getStyleClass().add("case-team-editor-label");
        search.setPromptText("Search active users by name…");ControlStyles.formControl(search);
        results.setFixedCellSize(48);results.setMinHeight(146);results.setPrefHeight(146);results.setMaxHeight(146);results.getStyleClass().add("case-team-search-results");results.setPlaceholder(searchEmpty);
        results.setCellFactory(v->new SearchResultCell());
        results.setOnMouseClicked(e->{if(e.getClickCount()==1)addSelectedResult();});results.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ENTER)addSelectedResult();});
        search.textProperty().addListener((o,a,b)->refreshResults());search.setOnKeyPressed(e->{if(e.getCode()==KeyCode.DOWN&&!results.getItems().isEmpty()){results.requestFocus();results.getSelectionModel().selectFirst();}else if(e.getCode()==KeyCode.ENTER&&!results.getItems().isEmpty()){results.getSelectionModel().selectFirst();addSelectedResult();}});
        members.setCellFactory(v->new MemberCell());members.setPlaceholder(new Label("No team members yet. Use search above to add someone."));VBox.setVgrow(members,Priority.ALWAYS);
        error.getStyleClass().add("case-team-editor-error");error.setWrapText(true);error.setVisible(false);error.setManaged(false);
        ControlStyles.apply(cancel,ControlStyles.Purpose.SECONDARY);ControlStyles.apply(save,ControlStyles.Purpose.PRIMARY);cancel.setOnAction(e->requestClose());save.setOnAction(e->save());save.setDefaultButton(true);
        Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);HBox footer=new HBox(10,spacer,cancel,save);footer.getStyleClass().add("case-team-editor-footer");
        VBox body=new VBox(8,heading,support,addLabel,search,results,new Separator(),members,error,footer);body.setPadding(new Insets(16));VBox.setVgrow(members,Priority.ALWAYS);
        VBox shell=AppDialogs.createSecondaryWindowShell(stage,"Case Team",this::requestClose,body);shell.getStyleClass().add("case-team-editor");
        Scene scene=new Scene(shell,Math.min(760,screenWidth(owner)-60),Math.min(680,screenHeight(owner)-60));scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());scene.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ESCAPE){e.consume();requestClose();}});stage.setScene(scene);stage.setMinWidth(520);stage.setMinHeight(480);stage.setOnCloseRequest(e->{e.consume();requestClose();});
        refreshMembers();refreshResults();
    }
    public void showAndWait(){stage.showAndWait();}
    private void addSelectedResult(){CaseDao.UserRow u=results.getSelectionModel().getSelectedItem();if(u==null)return;state.addMember(u);search.clear();refreshMembers();search.requestFocus();}
    private void refreshResults(){results.setItems(FXCollections.observableArrayList(state.search(search.getText())));}
    private void refreshMembers(){members.setItems(FXCollections.observableArrayList(state.members()));members.refresh();refreshResults();}
    private void requestClose(){if(closing||saving.get())return;if(!state.dirty()||confirmDiscard()){closing=true;stage.close();}}
    private boolean confirmDiscard(){Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Discard your unsaved Case Team changes?",ButtonType.CANCEL,ButtonType.OK);a.initOwner(stage);a.setTitle("Discard changes?");a.setHeaderText("Your staged changes have not been saved.");return a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}
    private void save(){if(!saving.compareAndSet(false,true))return;setSaving(true);List<CaseTeamUpdateMember> desired=state.members().stream().map(m->new CaseTeamUpdateMember(m.membershipId(),m.user().id(),m.rowVer(),List.copyOf(m.roleIds()))).toList();new Thread(()->{try{service.updateCaseTeam(new CaseTeamUpdateCommand(tenantId,actorId,caseId,desired));Platform.runLater(()->{saving.set(false);saved.run();closing=true;stage.close();});}catch(RuntimeException ex){Platform.runLater(()->{saving.set(false);setSaving(false);error.setText(userMessage(ex));error.setVisible(true);error.setManaged(true);});}},"case-team-save-"+caseId).start();}
    private void setSaving(boolean busy){save.setDisable(busy);cancel.setDisable(busy);search.setDisable(busy);members.setDisable(busy);save.setText(busy?"Saving…":"Save");}
    private static String userMessage(Throwable e){for(Throwable c=e;c!=null;c=c.getCause())if(c instanceof IllegalArgumentException||c instanceof IllegalStateException){String m=c.getMessage();if(m!=null&&!m.isBlank())return m;}return "The Case Team could not be saved. Reload and try again.";}
    private final class SearchResultCell extends ListCell<CaseDao.UserRow>{protected void updateItem(CaseDao.UserRow u,boolean empty){super.updateItem(u,empty);if(empty||u==null){setGraphic(null);setAccessibleText(null);return;}Label initials=new Label(initials(u.displayName()));initials.getStyleClass().add("case-team-search-initials");initials.setStyle(CaseTeamCardStyles.accentStyle(u.color()));Label name=new Label(u.displayName());name.setTextOverrun(OverrunStyle.ELLIPSIS);name.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(name,Priority.ALWAYS);Label add=new Label("Add");add.getStyleClass().add("case-team-search-add");HBox card=new HBox(9,initials,name,add);card.getStyleClass().add("case-team-search-card");setGraphic(card);setAccessibleText("Add "+u.displayName()+" to the case team");}}
    private final class MemberCell extends ListCell<CaseTeamEditorState.Member>{protected void updateItem(CaseTeamEditorState.Member m,boolean empty){super.updateItem(m,empty);if(empty||m==null){setGraphic(null);return;}Label name=new Label(m.user().displayName());name.getStyleClass().add("case-team-member-name");name.setWrapText(true);name.setMaxWidth(Double.MAX_VALUE);FlowPane chips=new FlowPane(6,6);if(m.roleIds().isEmpty()){Label none=new Label("No roles assigned");none.getStyleClass().add("case-team-no-roles");chips.getChildren().add(none);}else for(int id:m.roleIds())chips.getChildren().add(roleChip(m,id));MenuButton addRole=new MenuButton("+ Add role");ControlStyles.apply(addRole,ControlStyles.Purpose.GHOST,ControlStyles.Size.SMALL);for(CaseTeamRoleDefinitionDto d:state.availableRoles(m.user().id())){MenuItem item=new MenuItem(d.name());item.setOnAction(e->assignRole(m,d));addRole.getItems().add(item);}addRole.setDisable(addRole.getItems().isEmpty());Button remove=new Button("Remove member");ControlStyles.apply(remove,ControlStyles.Purpose.DANGER,ControlStyles.Size.SMALL);remove.setAccessibleText("Remove "+m.user().displayName()+" from the case team");remove.setTooltip(new Tooltip("Remove member and all of their roles when saved"));remove.setOnAction(e->{state.removeMember(m.user().id());refreshMembers();});Region gap=new Region();HBox.setHgrow(gap,Priority.ALWAYS);HBox actions=new HBox(8,addRole,gap,remove);VBox card=new VBox(7,name,chips,actions);card.getStyleClass().add("case-team-member-card");card.setStyle(CaseTeamCardStyles.memberCardStyle(m.user().color()));setGraphic(card);}}
    private Node roleChip(CaseTeamEditorState.Member m,int id){CaseTeamRoleDefinitionDto d=state.definition(id);String label=d==null?"Unknown role":d.name();boolean inactive=d==null||!d.active()||d.deleted();Label text=new Label(label+(inactive?" · Inactive":""));Circle dot=new Circle(4,color(d==null?null:d.color()));Button x=new Button("×");ControlStyles.apply(x,ControlStyles.Purpose.GHOST,ControlStyles.Size.SMALL);x.setAccessibleText("Remove "+label+" role from "+m.user().displayName());x.setTooltip(new Tooltip("Remove role; the member stays on the team"));x.setOnAction(e->{state.removeRole(m.user().id(),id);refreshMembers();});HBox chip=new HBox(5,dot,text,x);chip.getStyleClass().addAll("case-team-role-chip",inactive?"case-team-role-chip-inactive":"case-team-role-chip-active");return chip;}
    private void assignRole(CaseTeamEditorState.Member target,CaseTeamRoleDefinitionDto role){boolean confirm=true;if(CaseTeamEditorState.RESPONSIBLE_ATTORNEY.equals(role.systemKey())){var current=state.responsibleAttorney();if(current!=null&&current!=target){Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Move the Responsible Attorney role from "+current.user().displayName()+" to "+target.user().displayName()+"? Both people will remain on the team.",ButtonType.CANCEL,ButtonType.OK);a.initOwner(stage);a.setHeaderText("Move Responsible Attorney?");confirm=a.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;}}if(state.addRole(target.user().id(),role.id(),confirm))refreshMembers();}
    private static Color color(String s){try{return s==null?Color.web("#64748B"):Color.web(s);}catch(IllegalArgumentException e){return Color.web("#64748B");}}
    private static String initials(String name){String[] parts=name==null?new String[0]:name.trim().split("\\s+");if(parts.length==0||parts[0].isBlank())return "?";String first=parts[0].substring(0,1);String last=parts.length>1?parts[parts.length-1].substring(0,1):"";return (first+last).toUpperCase(Locale.ROOT);}
    private static double screenWidth(Stage owner){return Screen.getScreensForRectangle(owner.getX(),owner.getY(),Math.max(1,owner.getWidth()),Math.max(1,owner.getHeight())).stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds().getWidth();}
    private static double screenHeight(Stage owner){return Screen.getScreensForRectangle(owner.getX(),owner.getY(),Math.max(1,owner.getWidth()),Math.max(1,owner.getHeight())).stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds().getHeight();}
}
