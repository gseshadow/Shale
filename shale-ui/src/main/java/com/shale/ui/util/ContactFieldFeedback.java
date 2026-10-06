package com.shale.ui.util;

import com.shale.data.validation.ContactValues;
import com.shale.core.validation.FieldValidationException;
import java.util.function.BooleanSupplier;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/** Advisory blur feedback. Save and persistence still own their respective validation boundaries. */
public final class ContactFieldFeedback {
    private ContactFieldFeedback() {}
    public static Label phone(TextField number,TextField extension,boolean required,BooleanSupplier retained){
        Label feedback=new Label();feedback.setWrapText(true);number.getProperties().put("validationField","phone");
        if(extension!=null)extension.getProperties().put("validationField","phone.extension");
        Runnable validate=()->{
            boolean legacy=retained!=null&&retained.getAsBoolean();
            try{var value=ContactValues.INSTANCE.phone(number.getText(),extension==null?null:extension.getText(),required,"phone");feedback.setText(value==null?"":value.preview());ControlStyles.setInvalid(number,false);if(extension!=null)ControlStyles.setInvalid(extension,false);}
            catch(FieldValidationException error){feedback.setText((legacy?"Saved value needs review; unchanged values can be retained. ":"")+error.getMessage());boolean ext=error.errors().getFirst().field().endsWith(".extension");ControlStyles.setInvalid(number,!legacy&&!ext);if(extension!=null)ControlStyles.setInvalid(extension,!legacy&&ext);}
        };
        number.focusedProperty().addListener((o,a,focused)->{if(!focused)validate.run();});
        if(extension!=null)extension.focusedProperty().addListener((o,a,focused)->{if(!focused)validate.run();});
        if(retained!=null)validate.run();return feedback;
    }
    public static Label email(TextField field,boolean required,BooleanSupplier retained){
        Label feedback=new Label();feedback.setWrapText(true);field.getProperties().put("validationField","email");
        Runnable validate=()->{boolean legacy=retained!=null&&retained.getAsBoolean();try{ContactValues.INSTANCE.email(field.getText(),required,"email");feedback.setText("");ControlStyles.setInvalid(field,false);}catch(FieldValidationException error){feedback.setText((legacy?"Saved value needs review; unchanged values can be retained. ":"")+error.getMessage());ControlStyles.setInvalid(field,!legacy);}};
        field.focusedProperty().addListener((o,a,focused)->{if(!focused)validate.run();});if(retained!=null)validate.run();return feedback;
    }
    public static boolean focus(Parent parent,FieldValidationException error){
        for(Node node:parent.getChildrenUnmodifiable()){
            if(error.errors().getFirst().field().equals(node.getProperties().get("validationField"))){if(node instanceof javafx.scene.control.Control control)ControlStyles.setInvalid(control,true);node.requestFocus();return true;}
            if(node instanceof Parent child&&focus(child,error))return true;
        }return false;
    }
}
