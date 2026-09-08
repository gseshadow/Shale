package com.shale.ui.component.dialog;

import com.shale.core.dto.CaseOverviewAdministrationDto;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import com.shale.ui.controller.CaseOverviewEditModel;
import com.shale.ui.util.ActionButtonFactory;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.WindowSizingUtil;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Bounded, single-save editor for Overview layout and Intake By. */
public final class CaseOverviewEditorDialog {
    private CaseOverviewEditorDialog() {}
    public record UserOption(Integer id,String displayName,String color,boolean active){@Override public String toString(){return (displayName==null||displayName.isBlank()?"Unknown":displayName)+(active?"":" (Inactive)");}}
    public record Submission(List<Integer> orderedTypeIds,Integer intakeUserId,boolean layoutChanged,boolean intakeChanged){}

    public static void show(Window owner, CaseOverviewAdministrationDto baseline, List<UserOption> users,
            Function<Submission, CompletionStage<String>> saveAction, Runnable saved) {
        Objects.requireNonNull(baseline); CaseOverviewEditModel model=new CaseOverviewEditModel(baseline.availableDateTypes(),baseline.configuration().visibleDateTypes(),baseline.intakeTakenByUserId());
        Stage stage=new Stage();AppDialogs.applySecondaryWindowChrome(stage);if(owner!=null)stage.initOwner(owner);stage.initModality(Modality.WINDOW_MODAL);stage.setTitle("Edit Overview");
        VBox shownRows=new VBox(8); VBox availableRows=new VBox(8); VBox dateGroups=new VBox(12,new Label("Shown on Overview"),shownRows,new Label("Available dates"),availableRows); ScrollPane scroll=new ScrollPane(dateGroups);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);VBox.setVgrow(scroll,Priority.ALWAYS);
        ComboBox<UserOption> intake=new ComboBox<>();ControlStyles.formControl(intake);intake.getItems().add(new UserOption(null,"Unknown",null,true));if(users!=null)intake.getItems().addAll(users);if(baseline.intakeTakenByUserId()!=null&&intake.getItems().stream().noneMatch(u->Objects.equals(u.id(),baseline.intakeTakenByUserId())))intake.getItems().add(new UserOption(baseline.intakeTakenByUserId(),baseline.intakeTakenByDisplayName(),null,false));intake.getSelectionModel().select(intake.getItems().stream().filter(u->Objects.equals(u.id(),baseline.intakeTakenByUserId())).findFirst().orElse(intake.getItems().get(0)));
        Label error=new Label();error.getStyleClass().add("app-dialog-error");error.setVisible(false);error.setManaged(false);
        Runnable close=()->{if(model.saving())return;if(!model.changed()||AppDialogs.showConfirmation(stage,"Discard Changes?","Discard unsaved Overview changes?","Your staged date ordering and Intake By selection will not be saved.","Discard Changes",AppDialogs.DialogActionKind.DANGER))stage.close();};
        Button cancel=ActionButtonFactory.semantic("Cancel",e->close.run(),ControlStyles.Purpose.SECONDARY,ControlStyles.Size.STANDARD);
        Button save=ActionButtonFactory.semantic("Save",null,ControlStyles.Purpose.PRIMARY,ControlStyles.Size.STANDARD);save.setDisable(true);
        Runnable[] render={null};Runnable validate=()->save.setDisable(!model.changed()||model.saving());
        render[0]=()->{shownRows.getChildren().clear();availableRows.getChildren().clear();for(EffectiveCaseDateTypeDto type:model.shownTypes())shownRows.getChildren().add(dateRow(type,true,!baseline.availableDateTypes().stream().anyMatch(t->t.id()==type.id()),model,render[0],validate));for(EffectiveCaseDateTypeDto type:model.availableTypes())availableRows.getChildren().add(dateRow(type,false,false,model,render[0],validate));};
        intake.valueProperty().addListener((o,a,b)->{if(b!=null){model.setIntakeUserId(b.id());validate.run();}});render[0].run();
        save.setOnAction(e->{if(!model.beginSave())return;validate.run();cancel.setDisable(true);intake.setDisable(true);CompletionStage<String> result;try{result=saveAction.apply(new Submission(model.selectedIds(),model.intakeUserId(),model.layoutChanged(),model.intakeChanged()));}catch(RuntimeException failure){result=java.util.concurrent.CompletableFuture.failedFuture(failure);}if(result==null)result=java.util.concurrent.CompletableFuture.completedFuture("Overview save is unavailable.");result.whenComplete((message,failure)->Platform.runLater(()->{if(failure==null&&(message==null||message.isBlank())){stage.hide();if(saved!=null)saved.run();return;}model.saveFailed();cancel.setDisable(false);intake.setDisable(false);error.setText(failure==null?message:"Overview could not be saved. Refresh and reopen the editor.");error.setVisible(true);error.setManaged(true);validate.run();}));});
        HBox footer=new HBox(10,cancel,save);footer.setAlignment(Pos.CENTER_RIGHT);footer.getStyleClass().add("app-dialog-actions");footer.setMinHeight(Region.USE_PREF_SIZE);
        VBox content=new VBox(12,scroll,new Label("Intake By"),intake,error,footer);content.setPadding(new Insets(18));VBox shell=AppDialogs.createSecondaryWindowShell(stage,"Edit Overview",close,content);Scene scene=new Scene(shell,760,620);scene.getStylesheets().add(Objects.requireNonNull(CaseOverviewEditorDialog.class.getResource("/css/app.css")).toExternalForm());scene.setOnKeyPressed(e->{if(e.getCode()==KeyCode.ESCAPE){e.consume();close.run();}});stage.setScene(scene);stage.setOnCloseRequest(e->{if(model.saving()||model.changed()&&!AppDialogs.showConfirmation(stage,"Discard Changes?","Discard unsaved Overview changes?","Your staged date ordering and Intake By selection will not be saved.","Discard Changes",AppDialogs.DialogActionKind.DANGER))e.consume();});WindowSizingUtil.sizeModalStage(stage,owner,760,620,560,420);stage.showAndWait();
    }

    private static HBox dateRow(EffectiveCaseDateTypeDto type,boolean shown,boolean historical,CaseOverviewEditModel model,Runnable render,Runnable validate){CheckBox selected=new CheckBox();selected.setSelected(shown);Label name=new Label(type.name()+(historical?" (Inactive / unavailable)":""));name.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(name,Priority.ALWAYS);Region color=new Region();color.getStyleClass().add("case-overview-date-color");color.setStyle("-fx-background-color: "+type.color()+";");HBox row=new HBox(9,selected,color,name);if(shown){Button up=ActionButtonFactory.semantic("Move Up",e->{model.moveUp(type.id());render.run();validate.run();},ControlStyles.Purpose.GHOST,ControlStyles.Size.SMALL);Button down=ActionButtonFactory.semantic("Move Down",e->{model.moveDown(type.id());render.run();validate.run();},ControlStyles.Purpose.GHOST,ControlStyles.Size.SMALL);up.setDisable(!model.canMoveUp(type.id()));down.setDisable(!model.canMoveDown(type.id()));row.getChildren().addAll(up,down);}selected.setOnAction(e->{if(selected.isSelected())model.select(type.id());else model.remove(type.id());render.run();validate.run();});row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("case-overview-editor-row");if(shown)row.getStyleClass().add("case-overview-editor-row-selected");return row;}
}
