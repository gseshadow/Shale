package com.shale.ui.controller;

import com.shale.core.dto.CaseTeamRoleDefinitionDto;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import java.util.*;

/** Settings card administration for tenant-effective Case Team role definitions. */
final class CaseTeamRoleAdminPane {
 private final CaseServicePort service; private final AppState state; private final VBox list=new VBox(8); private final Label status=new Label(); private CaseTeamRoleDefinitionDto selected;
 CaseTeamRoleAdminPane(CaseServicePort service,AppState state){this.service=service;this.state=state;}
 Node node(){VBox root=new VBox(8);HBox actions=new HBox(8,button("Add Role",ControlStyles.Purpose.PRIMARY,e->edit(null)),button("Edit / Customize",ControlStyles.Purpose.SECONDARY,e->edit(selected)),button("Activate / Deactivate",ControlStyles.Purpose.GHOST,e->toggle()),button("Remove / Restore",ControlStyles.Purpose.DANGER,e->removeRestore()),button("Reset Override",ControlStyles.Purpose.SECONDARY,e->reset()),status);root.getChildren().addAll(list,actions);reload();return root;}
 private Button button(String text,ControlStyles.Purpose purpose,javafx.event.EventHandler<javafx.event.ActionEvent> h){return ActionButtonFactory.semantic(text,h,purpose,ControlStyles.Size.STANDARD);}
 private void reload(){try{list.getChildren().setAll(service.listCaseTeamRolesForAdministration(tenant(),actor()).stream().map(this::card).toList());status.setText("");}catch(RuntimeException e){status.setText(e.getMessage());}}
 private Node card(CaseTeamRoleDefinitionDto d){VBox c=new VBox(4);c.getStyleClass().addAll("strong-panel","shale-density-card-compact");c.setPadding(new Insets(8));Label title=new Label(d.name()+"  ·  "+(d.systemProvided()?"System role":"Custom role")+(d.tenantOverride()?" (tenant override)":""));title.getStyleClass().add("app-dialog-field-label");Label detail=new Label((d.description()==null?"":d.description()+" · ")+"Order "+d.sortOrder()+" · "+(d.deleted()?"Removed":d.active()?"Active":"Inactive"));detail.getStyleClass().add("search-summary-text");c.getChildren().addAll(title,detail);c.setOnMouseClicked(e->{selected=d;status.setText("Selected "+d.name());});return c;}
 private void edit(CaseTeamRoleDefinitionDto d){Dialog<CaseServicePort.CaseTeamRoleCommand> dialog=new Dialog<>();dialog.setTitle(d==null?"Add Case Team Role":"Edit Case Team Role");dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);TextField name=new TextField(d==null?"":d.name()),description=new TextField(d==null||d.description()==null?"":d.description());Spinner<Integer> order=new Spinner<>(-100000,100000,d==null?0:d.sortOrder());ColorPicker color=new ColorPicker(Color.web(d==null?"#6C757D":d.color()));CheckBox active=new CheckBox("Active");active.setSelected(d==null||d.active());GridPane g=new GridPane();g.setHgap(8);g.setVgap(8);g.addRow(0,new Label("Name"),name);g.addRow(1,new Label("Description"),description);g.addRow(2,new Label("Sort order"),order);g.addRow(3,new Label("Color"),color);g.add(active,1,4);dialog.getDialogPane().setContent(g);dialog.setResultConverter(b->b==ButtonType.OK?new CaseServicePort.CaseTeamRoleCommand(d==null?null:d.id(),tenant(),actor(),name.getText(),description.getText(),hex(color.getValue()),order.getValue(),active.isSelected(),d==null?null:d.rowVer()):null);dialog.showAndWait().ifPresent(x->{try{if(d==null)service.createCaseTeamRole(x);else service.updateCaseTeamRole(x);reload();}catch(RuntimeException e){status.setText(e.getMessage());}});}
 private void toggle(){if(selected==null||selected.deleted()){status.setText("Select an active or inactive role.");return;}edit(new CaseTeamRoleDefinitionDto(selected.id(),selected.shaleClientId(),selected.systemKey(),selected.legacyRoleId(),selected.name(),selected.description(),selected.color(),selected.sortOrder(),!selected.active(),selected.deleted(),selected.protectedSystemRole(),selected.tenantOverride(),selected.createdAt(),selected.createdByUserId(),selected.updatedAt(),selected.updatedByUserId(),selected.deletedAt(),selected.deletedByUserId(),selected.rowVer()));}
 private void removeRestore(){if(selected==null){status.setText("Select a custom role.");return;}var c=life();try{if(selected.deleted())service.restoreCaseTeamRole(c);else service.removeCaseTeamRole(c);selected=null;reload();}catch(RuntimeException e){status.setText(e.getMessage());}}
 private void reset(){if(selected==null||!selected.tenantOverride()){status.setText("Select a tenant override to reset.");return;}try{service.resetCaseTeamRoleOverride(life());selected=null;reload();}catch(RuntimeException e){status.setText(e.getMessage());}}
 private CaseServicePort.CaseTeamRoleLifecycleCommand life(){return new CaseServicePort.CaseTeamRoleLifecycleCommand(tenant(),actor(),selected.id(),selected.rowVer());}
 private int tenant(){return state.getShaleClientId();} private int actor(){return state.getUserId();}
 private static String hex(Color c){return String.format("#%02X%02X%02X",Math.round(c.getRed()*255),Math.round(c.getGreen()*255),Math.round(c.getBlue()*255));}
}
