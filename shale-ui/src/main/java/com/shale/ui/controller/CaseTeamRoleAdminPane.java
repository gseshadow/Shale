package com.shale.ui.controller;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.dto.CaseTeamRoleDefinitionDto;
import com.shale.core.service.CaseServicePort;
import com.shale.ui.component.CommittedChangeTracker;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Case-Team-Role-specific definition management. It never mutates team assignments. */
public final class CaseTeamRoleAdminPane {
    private static final Logger LOG=LoggerFactory.getLogger(CaseTeamRoleAdminPane.class);
    private final CaseServicePort service; private final int tenantId,actorId; private final Executor executor;
    private final CommittedChangeTracker changes; private final AtomicBoolean mutation=new AtomicBoolean();
    private final VBox root=new VBox(12); private final FlowPane cards=new FlowPane(10,10); private final Label status=new Label();
    private volatile boolean disposed; private int generation;

    public CaseTeamRoleAdminPane(CaseServicePort service,int tenantId,int actorId,Executor executor,CommittedChangeTracker changes){
        this.service=service;this.tenantId=tenantId;this.actorId=actorId;this.executor=executor;this.changes=changes;
        Button add=button("Add Role",ControlStyles.Purpose.PRIMARY,()->editor(null).ifPresent(i->save(null,i)));
        status.getStyleClass().add("search-summary-text");status.setWrapText(true);cards.setPrefWrapLength(820);
        root.getChildren().addAll(new HBox(8,add),status,cards);reload(null);
    }
    public Node node(){return root;} boolean mutationInFlight(){return mutation.get();}
    public void dispose(){disposed=true;generation++;} int loadGeneration(){return generation;}
    void reload(String message){if(disposed)return;int requested=++generation;status.setText("Loading Case Team roles…");cards.getChildren().clear();
        executor.execute(()->{try{List<CaseTeamRoleDefinitionDto> rows=List.copyOf(service.listCaseTeamRolesForAdministration(tenantId,actorId));Platform.runLater(()->applyLoad(requested,rows,message));}
        catch(RuntimeException ex){LOG.error("Case Team Role administration load failed tenantId={} actorId={}",tenantId,actorId,ex);Platform.runLater(()->{if(current(requested))status.setText("Case Team roles could not be loaded. Try again.");});}});}
    void applyLoad(int requested,List<CaseTeamRoleDefinitionDto> rows,String message){if(!current(requested))return;cards.getChildren().setAll(rows.stream().map(this::card).toList());status.setText(message==null?(rows.isEmpty()?"No Case Team roles are configured.":""):message);}
    private boolean current(int requested){return !disposed&&requested==generation;}
    private Node card(CaseTeamRoleDefinitionDto d){VBox card=new VBox(7);card.getStyleClass().addAll("shale-entity-card","shale-entity-card-compact");card.setPadding(new Insets(10));card.setPrefWidth(380);
        Label name=new Label(d.name());name.getStyleClass().add("app-dialog-field-label");Label detail=new Label(scope(d)+" · Order "+d.sortOrder()+" · "+(d.deleted()?"Removed":d.active()?"Active":"Inactive"));detail.getStyleClass().add("search-summary-text");
        Actions a=actions(d);HBox buttons=new HBox(8);if(a.edit())buttons.getChildren().add(button(d.shaleClientId()==null?"Customize":"Edit",ControlStyles.Purpose.GHOST,()->editor(d).ifPresent(i->save(d,i))));
        if(a.toggle())buttons.getChildren().add(button(d.active()?"Deactivate":"Activate",ControlStyles.Purpose.GHOST,()->toggle(d)));
        if(a.remove())buttons.getChildren().add(button("Remove",ControlStyles.Purpose.DANGER,()->lifecycle(d,false)));
        if(a.restore())buttons.getChildren().add(button("Restore",ControlStyles.Purpose.SECONDARY,()->lifecycle(d,true)));
        if(a.reset())buttons.getChildren().add(button("Reset Override",ControlStyles.Purpose.SECONDARY,()->reset(d)));
        card.getChildren().addAll(name,detail,buttons);return card;}
    static Actions actions(CaseTeamRoleDefinitionDto d){boolean global=d.shaleClientId()==null;boolean protectedRole=d.protectedSystemRole();return new Actions(!d.deleted(),!d.deleted()&&!global,d.deleted()&&!protectedRole,!d.deleted()&&!global&&!protectedRole,d.tenantOverride());}
    private void save(CaseTeamRoleDefinitionDto d,Input i){CaseServicePort.CaseTeamRoleCommand command=command(d,i,tenantId,actorId);mutate(d==null?"Role added.":"Role saved.",()->{if(d==null)service.createCaseTeamRole(command);else service.updateCaseTeamRole(command);});}
    private void toggle(CaseTeamRoleDefinitionDto d){Input i=new Input(d.name(),d.description(),d.color(),d.sortOrder(),!d.active());save(d,i);}
    private void lifecycle(CaseTeamRoleDefinitionDto d,boolean restore){CaseServicePort.CaseTeamRoleLifecycleCommand command=life(d,tenantId,actorId);mutate(restore?"Role restored.":"Role removed.",()->{if(restore)service.restoreCaseTeamRole(command);else service.removeCaseTeamRole(command);});}
    private void reset(CaseTeamRoleDefinitionDto d){CaseServicePort.CaseTeamRoleLifecycleCommand command=life(d,tenantId,actorId);mutate("Override reset to the system role.",()->service.resetCaseTeamRoleOverride(command));}
    private void mutate(String success,Runnable operation){if(disposed||!mutation.compareAndSet(false,true))return;int requested=++generation;status.setText("Saving…");executor.execute(()->{try{operation.run();if(disposed||requested!=generation)return;changes.markCommitted();Platform.runLater(()->{if(current(requested)){mutation.set(false);reload(success);}});}catch(RuntimeException ex){LOG.error("Case Team Role administration mutation failed tenantId={} actorId={}",tenantId,actorId,ex);Platform.runLater(()->{if(current(requested)){mutation.set(false);status.setText("The role could not be saved. Review the values or reload and try again.");}});}});}
    static CaseServicePort.CaseTeamRoleCommand command(CaseTeamRoleDefinitionDto d,Input i,int tenant,int actor){return new CaseServicePort.CaseTeamRoleCommand(d==null?null:d.id(),tenant,actor,i.name(),i.description(),i.color(),i.sortOrder(),i.active(),d==null?null:d.rowVer());}
    static CaseServicePort.CaseTeamRoleLifecycleCommand life(CaseTeamRoleDefinitionDto d,int tenant,int actor){return new CaseServicePort.CaseTeamRoleLifecycleCommand(tenant,actor,d.id(),d.rowVer());}
    private Optional<Input> editor(CaseTeamRoleDefinitionDto d){Dialog<Input> dialog=new Dialog<>();AppDialogs.applySecondaryDialogShell(dialog,d==null?"Add Case Team Role":d.shaleClientId()==null?"Customize Case Team Role":"Edit Case Team Role");dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK,ButtonType.CANCEL);
        TextField name=new TextField(d==null?"":d.name()),description=new TextField(d==null||d.description()==null?"":d.description());Spinner<Integer> order=new Spinner<>(-100000,100000,d==null?0:d.sortOrder());ColorPicker color=new ColorPicker(color(d==null?null:d.color()));CheckBox active=new CheckBox("Active");active.setSelected(d==null||d.active());Label validation=new Label();validation.getStyleClass().add("dialog-error-text");GridPane grid=new GridPane();grid.setHgap(8);grid.setVgap(8);grid.addRow(0,new Label("Name"),name);grid.addRow(1,new Label("Description"),description);grid.addRow(2,new Label("Sort order"),order);grid.addRow(3,new Label("Color"),color);grid.add(active,1,4);grid.add(validation,1,5);dialog.getDialogPane().setContent(grid);
        Button ok=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);ControlStyles.apply(ok,ControlStyles.Purpose.PRIMARY);ok.addEventFilter(javafx.event.ActionEvent.ACTION,e->{String n=name.getText()==null?"":name.getText().trim();if(n.isEmpty()||n.length()>200){validation.setText(n.isEmpty()?"Name is required.":"Name must be 200 characters or fewer.");e.consume();}});
        dialog.setResultConverter(b->b==ButtonType.OK?new Input(name.getText().trim(),description.getText().trim(),hex(color.getValue()),order.getValue(),active.isSelected()):null);return dialog.showAndWait();}
    private Button button(String text,ControlStyles.Purpose purpose,Runnable action){return ActionButtonFactory.semantic(text,e->action.run(),purpose,ControlStyles.Size.SMALL);}
    private static String scope(CaseTeamRoleDefinitionDto d){if(d.tenantOverride())return "Tenant override";if(d.shaleClientId()==null)return d.protectedSystemRole()?"Protected system role":"System role";return "Tenant custom role";}
    private static Color color(String value){try{return value==null?Color.web("#6C757D"):Color.web(value);}catch(IllegalArgumentException ex){return Color.web("#6C757D");}}
    private static String hex(Color c){return String.format("#%02X%02X%02X",Math.round(c.getRed()*255),Math.round(c.getGreen()*255),Math.round(c.getBlue()*255));}
    public record Input(String name,String description,String color,int sortOrder,boolean active){} record Actions(boolean edit,boolean toggle,boolean restore,boolean remove,boolean reset){}
}
