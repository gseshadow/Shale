package com.shale.ui.util;

import com.shale.data.validation.ContactValues;
import com.shale.core.validation.FieldValidationException;
import java.util.function.BooleanSupplier;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/** Advisory blur feedback. Save and persistence still own their respective validation boundaries. */
public final class ContactFieldFeedback {
    private static final String ERROR = "contactValidationError";
    private ContactFieldFeedback() {}

    /** Remove only this field's resolved validation message from its owning form summary. */
    public static void trackSummary(Parent parent, Label summary) {
        if (summary == null) return;
        for (Node node : parent.getChildrenUnmodifiable()) {
            if (node instanceof TextField field && field.getProperties().containsKey("validationField")) {
                field.getProperties().addListener((javafx.collections.MapChangeListener<Object,Object>) change -> {
                    if (ERROR.equals(change.getKey()) && change.wasRemoved()) {
                        String previous = Objects.toString(change.getValueRemoved(), "");
                        String current = Objects.toString(change.getValueAdded(), "");
                        if (!previous.isBlank()) {
                            summary.setText(summary.getText().lines().map(line -> line.equals(previous) ? current : line)
                                    .filter(line -> !line.isBlank()).collect(java.util.stream.Collectors.joining("\n")));
                            if (summary.getText().isBlank()) { summary.setVisible(false); summary.setManaged(false); }
                        }
                    }
                });
            }
            if (node instanceof Parent child) trackSummary(child, summary);
        }
    }

    /** Complete editors keep their collection validation summary synchronized after a row edit. */
    public static void trackStageSummary(Parent stage, Label summary, Runnable validator) {
        stage.getChildrenUnmodifiable().addListener((javafx.collections.ListChangeListener<Node>) change -> {
            String previous=Objects.toString(stage.getProperties().get("stageValidationError"), "");
            if(previous.isBlank() || !summary.getText().lines().anyMatch(previous::equals))return;
            try {
                validateStage(stage,validator);
                summary.setText(summary.getText().lines().filter(line -> !line.equals(previous))
                        .collect(java.util.stream.Collectors.joining("\n")));
                if(summary.getText().isBlank()){summary.setVisible(false);summary.setManaged(false);}
            } catch(IllegalArgumentException invalid) {
                summary.setText(summary.getText().lines().map(line -> line.equals(previous)?invalid.getMessage():line)
                        .collect(java.util.stream.Collectors.joining("\n")));
            }
        });
    }

    public static void validateStage(Parent stage, Runnable validator) {
        try {validator.run();stage.getProperties().remove("stageValidationError");}
        catch(IllegalArgumentException invalid){stage.getProperties().put("stageValidationError",invalid.getMessage());throw invalid;}
    }

    private static void resolved(TextField field) {
        ControlStyles.setInvalid(field, false);
        field.getProperties().put(ERROR, "");
    }

    private static void failed(TextField field, String message, boolean legacy) {
        ControlStyles.setInvalid(field, !legacy);
        field.getProperties().put(ERROR, legacy ? "" : message);
    }
    public static Label phone(TextField number,TextField extension,boolean required,BooleanSupplier retained){
        Label feedback=new Label();feedback.setWrapText(true);number.getProperties().put("validationField","phone");
        if(extension!=null)extension.getProperties().put("validationField","phone.extension");
        Runnable validate=()->{
            boolean legacy=retained!=null&&retained.getAsBoolean();
            try {
                var value=ContactValues.INSTANCE.phone(number.getText(),extension==null?null:extension.getText(),required,"phone");
                if (!legacy && value != null) {
                    number.setText(value.displayInput() + (extension == null && value.extension() != null ? " ext. " + value.extension() : ""));
                    if (extension != null) extension.setText(Objects.toString(value.extension(), ""));
                }
                feedback.setText(value==null?"":value.preview());
                resolved(number); if(extension!=null)resolved(extension);
            } catch(FieldValidationException error) {
                feedback.setText((legacy?"Saved value needs review; unchanged values can be retained. ":"")+error.getMessage());
                boolean ext=error.errors().getFirst().field().endsWith(".extension");
                if(ext&&extension!=null){resolved(number);failed(extension,error.getMessage(),legacy);}
                else {failed(number,error.getMessage(),legacy);if(extension!=null)resolved(extension);}
            }
        };
        number.focusedProperty().addListener((o,a,focused)->{if(!focused)validate.run();});
        if(extension!=null)extension.focusedProperty().addListener((o,a,focused)->{if(!focused)validate.run();});
        if(retained!=null)validate.run();return feedback;
    }
    public static Label email(TextField field,boolean required,BooleanSupplier retained){
        Label feedback=new Label();feedback.setWrapText(true);field.getProperties().put("validationField","email");
        Runnable validate=()->{boolean legacy=retained!=null&&retained.getAsBoolean();try{ContactValues.INSTANCE.email(field.getText(),required,"email");feedback.setText("");resolved(field);}catch(FieldValidationException error){feedback.setText((legacy?"Saved value needs review; unchanged values can be retained. ":"")+error.getMessage());failed(field,error.getMessage(),legacy);}};
        field.focusedProperty().addListener((o,a,focused)->{if(!focused)validate.run();});if(retained!=null)validate.run();return feedback;
    }
    public static boolean focus(Parent parent,FieldValidationException error){
        for(Node node:parent.getChildrenUnmodifiable()){
            if(error.errors().getFirst().field().equals(node.getProperties().get("validationField"))){if(node instanceof javafx.scene.control.Control control)ControlStyles.setInvalid(control,true);node.getProperties().put(ERROR,error.errors().getFirst().message());node.requestFocus();return true;}
            if(node instanceof Parent child&&focus(child,error))return true;
        }return false;
    }
}
