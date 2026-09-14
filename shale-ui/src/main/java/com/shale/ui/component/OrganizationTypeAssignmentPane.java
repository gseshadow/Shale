package com.shale.ui.component;

import java.util.List;
import java.util.function.Consumer;
import com.shale.core.service.OrganizationServicePort.OrganizationTypeDefinition;
import com.shale.ui.controller.support.OrganizationTypeAssignmentStage;
import com.shale.ui.util.ControlStyles;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/** Reusable view over the mutation-free Organization Type assignment stage. */
public final class OrganizationTypeAssignmentPane extends VBox {
	private final VBox assignedRows=new VBox(6);private final ComboBox<OrganizationTypeDefinition> available=new ComboBox<>();private final Label empty=new Label();private OrganizationTypeAssignmentStage stage;private Consumer<String> message=m->{};
	public OrganizationTypeAssignmentPane(){setSpacing(8);setPadding(new Insets(8));getStyleClass().add("organization-type-assignment-editor");available.setPromptText("Add an Organization Type…");ControlStyles.formControl(available);available.setCellFactory(v->cell());available.setButtonCell(cell());Button add=new Button("Add");ControlStyles.apply(add,ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL);add.setOnAction(e->{if(available.getValue()!=null)act(()->stage.add(available.getValue()));});HBox chooser=new HBox(8,available,add);HBox.setHgrow(available,Priority.ALWAYS);getChildren().addAll(new Label("Assigned Organization Types"),assignedRows,empty,chooser);}
	public void setStage(OrganizationTypeAssignmentStage value){stage=value;render();}public OrganizationTypeAssignmentStage getStage(){return stage;}public void setMessageHandler(Consumer<String> handler){message=handler==null?m->{}:handler;}
	private void render(){assignedRows.getChildren().clear();if(stage==null){empty.setText("Organization Types are loading…");available.getItems().clear();return;}for(var item:stage.assigned())assignedRows.getChildren().add(row(item));empty.setText(stage.assigned().isEmpty()?"No types assigned. Add at least one type.":stage.available("").isEmpty()?"All available types are assigned.":"");available.getItems().setAll(stage.available(""));available.setValue(null);}
	private HBox row(OrganizationTypeAssignmentStage.Item item){Circle swatch=new Circle(5,color(item.definition().color()));Label name=new Label(item.definition().name());Label badges=new Label((item.primary()?"Primary":"")+(item.historical()?(item.primary()?" • ":"")+(item.definition().deleted()?"Removed":"Inactive"):""));Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);Button primary=new Button("Set Primary");ControlStyles.apply(primary,ControlStyles.Purpose.GHOST,ControlStyles.Size.SMALL);primary.setDisable(item.primary()||!item.eligible());primary.setOnAction(e->act(()->stage.setPrimary(item.definition().organizationTypeId())));Button remove=new Button("Remove");ControlStyles.apply(remove,ControlStyles.Purpose.DANGER,ControlStyles.Size.SMALL);remove.setOnAction(e->act(()->stage.remove(item.definition().organizationTypeId())));return new HBox(8,swatch,name,badges,spacer,primary,remove);}
	private void act(Runnable action){try{action.run();message.accept("");}catch(RuntimeException ex){message.accept(ex.getMessage());}render();}
	private static ListCell<OrganizationTypeDefinition> cell(){return new ListCell<>(){protected void updateItem(OrganizationTypeDefinition d,boolean e){super.updateItem(d,e);setText(e||d==null?null:d.name());setGraphic(e||d==null?null:new Circle(5,color(d.color())));}};}
	private static Color color(String value){try{return Color.web(value);}catch(RuntimeException e){return Color.GRAY;}}
}
