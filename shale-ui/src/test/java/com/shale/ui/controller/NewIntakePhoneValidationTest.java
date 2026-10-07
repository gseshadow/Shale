package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import javafx.css.PseudoClass;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.shale.ui.testutil.JavaFxTestSupport;

/** Prevents stale summaries and feedback while keeping validation at blur/Save boundaries. */
class NewIntakePhoneValidationTest {
    private static final PseudoClass INVALID=PseudoClass.getPseudoClass("invalid");
    private static final class PhoneField extends TextField {
        PhoneField(String value){super(value);}
        void blur(){setFocused(true);setFocused(false);}
    }
    @BeforeAll static void toolkit(){JavaFxTestSupport.ensureToolkitStarted();}
    private static void set(NewIntakeController controller,String name,Object value)throws Exception {
        Field field=NewIntakeController.class.getDeclaredField(name);field.setAccessible(true);field.set(controller,value);
    }
    private static Object get(NewIntakeController controller,String name)throws Exception {
        Field field=NewIntakeController.class.getDeclaredField(name);field.setAccessible(true);return field.get(controller);
    }
    private static Object invoke(NewIntakeController controller,String name,Class<?>[] types,Object... args)throws Exception {
        Method method=NewIntakeController.class.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(controller,args);
    }
    private static NewIntakeController form()throws Exception {
        NewIntakeController controller=new NewIntakeController();
        for(String role:List.of("client","caller")){
            set(controller,role+"PhoneField",new PhoneField("0"));
            set(controller,role+"PhoneExtensionField",new TextField());
            set(controller,role+"EmailField",new TextField());
            set(controller,role+"PhoneUnavailableCheckBox",new CheckBox());
            set(controller,role+"PhoneUnavailableReasonBox",new ComboBox<>());
            set(controller,role+"PhoneFeedbackLabel",new Label());
        }
        set(controller,"callerIsClientCheckBox",new CheckBox());set(controller,"validationLabel",new Label());
        for(String field:List.of("caseNameField","timeOfIntakeField","clientFirstNameField","clientLastNameField","callerFirstNameField","callerLastNameField"))set(controller,field,new TextField());
        for(String field:List.of("clientDateOfBirthPicker","dateMedicalNegligencePicker","dateMedicalNegligenceDiscoveredPicker","dateOfInjuryPicker","statuteOfLimitationsPicker","tortClaimsNoticePicker"))set(controller,field,new DatePicker());
        invoke(controller,"configureContactValidation",new Class<?>[0]);
        return controller;
    }
    @Test void correctingEachRoleClearsOnlyItsErrorOnBlurAndTypingDoesNotClear() {
        JavaFxTestSupport.runAndWait(()->{
            var controller=form();var summary=(Label)get(controller,"validationLabel");
            var client=(PhoneField)get(controller,"clientPhoneField");var caller=(PhoneField)get(controller,"callerPhoneField");
            client.blur();assertTrue(summary.getText().contains("Client Phone:"));assertTrue(summary.getText().contains("Caller Phone:"));
            invoke(controller,"showValidation",new Class<?>[]{String.class},summary.getText()+"\nCase Name is required.");
            client.setText("555-0123");assertTrue(summary.getText().contains("Client Phone:"),"typing alone must not clear errors");
            assertTrue(client.getPseudoClassStates().contains(INVALID));
            client.blur();assertFalse(summary.getText().contains("Client Phone:"));assertFalse(client.getPseudoClassStates().contains(INVALID));
            assertTrue(summary.getText().contains("Caller Phone:"));assertTrue(summary.getText().contains("Case Name is required."));
            assertTrue(((Label)get(controller,"clientPhoneFeedbackLabel")).getText().contains("area code required to call"));
            caller.setText("3035550123");caller.blur();assertFalse(summary.getText().contains("Caller Phone:"));
            assertFalse(caller.getPseudoClassStates().contains(INVALID));assertEquals("Case Name is required.",summary.getText());
        });
    }
    @Test void correctingLastErrorHidesSummaryOnBlur() {
        JavaFxTestSupport.runAndWait(()->{
            var controller=form();((CheckBox)get(controller,"callerIsClientCheckBox")).setSelected(true);
            var phone=(PhoneField)get(controller,"clientPhoneField");var summary=(Label)get(controller,"validationLabel");
            phone.blur();assertTrue(summary.isVisible());phone.setText("3035550123");phone.blur();
            assertFalse(summary.isVisible());assertFalse(summary.isManaged());assertEquals("",summary.getText());
            assertFalse(((Label)get(controller,"callerPhoneFeedbackLabel")).isVisible());
        });
    }
    @Test void saveValidationReplacesCorrectedPhoneErrorsAndRetainsOtherFailures() {
        JavaFxTestSupport.runAndWait(()->{
            var controller=form();var client=(PhoneField)get(controller,"clientPhoneField");var caller=(PhoneField)get(controller,"callerPhoneField");
            client.blur();client.setText("3035550123");caller.setText("+44 20 7946 0018");
            @SuppressWarnings("unchecked") List<String> errors=(List<String>)invoke(controller,"validateRequiredFields",new Class<?>[0]);
            assertFalse(errors.stream().anyMatch(message->message.contains("Phone:")));
            assertTrue(errors.contains("Case Name is required."));assertTrue(errors.contains("Status is required."));
            assertFalse(client.getPseudoClassStates().contains(INVALID));assertFalse(caller.getPseudoClassStates().contains(INVALID));
            assertFalse(((Label)get(controller,"validationLabel")).getText().contains("Phone:"));
        });
    }
    @Test void reasonExtensionAndRetainedInputCannotBypassValidation() {
        JavaFxTestSupport.runAndWait(()->{
            var controller=form();((CheckBox)get(controller,"callerIsClientCheckBox")).setSelected(true);
            var phone=(PhoneField)get(controller,"clientPhoneField");var unavailable=(CheckBox)get(controller,"clientPhoneUnavailableCheckBox");
            unavailable.setSelected(true);phone.blur();
            var reason=(ComboBox<?>)get(controller,"clientPhoneUnavailableReasonBox");assertTrue(reason.getPseudoClassStates().contains(INVALID));assertTrue(phone.getPseudoClassStates().contains(INVALID));
            @SuppressWarnings("unchecked") var typedReason=(ComboBox<com.shale.core.validation.PhoneUnavailableReason>)reason;
            typedReason.setValue(com.shale.core.validation.PhoneUnavailableReason.UNKNOWN);phone.setText("");phone.blur();
            assertFalse(reason.getPseudoClassStates().contains(INVALID));assertFalse(phone.getPseudoClassStates().contains(INVALID));
            var extension=(TextField)get(controller,"clientPhoneExtensionField");extension.setText("001");phone.blur();assertTrue(extension.getPseudoClassStates().contains(INVALID));
            assertTrue(((Label)get(controller,"validationLabel")).getText().contains("before adding an extension"));
        });
    }
    @Test void productionFxmlLoadsPhoneGroupsInBothThemes() {
        JavaFxTestSupport.runAndWait(()->{
            var loader=new javafx.fxml.FXMLLoader(getClass().getResource("/fxml/new-intake.fxml"));
            javafx.scene.Parent root=loader.load();
            var stage=new javafx.stage.Stage();var scene=new javafx.scene.Scene(root,1180,1000);
            var themes=new com.shale.ui.theme.ThemeManager();themes.register(scene);stage.setScene(scene);
            try {
                stage.show();
                for(var theme:com.shale.ui.theme.Theme.values()){
                    themes.setActiveTheme(theme);root.applyCss();root.layout();
                    for(String role:List.of("client","caller")){
                        var phone=(TextField)loader.getNamespace().get(role+"PhoneField");
                        var extension=(TextField)loader.getNamespace().get(role+"PhoneExtensionField");
                        assertSame(phone.getParent(),extension.getParent(),"production phone controls stay in one group");
                        assertSame(phone.getParent(),((Label)loader.getNamespace().get(role+"PhoneFeedbackLabel")).getParent());
                        assertTrue(extension.getStyleClass().contains("shale-form-control"));
                        String directory=System.getProperty("shale.phone.previewDir");
                        if(directory!=null){
                            var section=(javafx.scene.Node)loader.getNamespace().get(role+"Section");
                            var image=section.snapshot(null,null);var reader=image.getPixelReader();
                            var output=new java.awt.image.BufferedImage((int)image.getWidth(),(int)image.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
                            for(int y=0;y<output.getHeight();y++)for(int x=0;x<output.getWidth();x++)output.setRGB(x,y,reader.getArgb(x,y));
                            javax.imageio.ImageIO.write(output,"png",java.nio.file.Path.of(directory,role+"-"+theme.name()+".png").toFile());
                        }
                    }
                }
            } finally { stage.close(); }
        });
    }

}
