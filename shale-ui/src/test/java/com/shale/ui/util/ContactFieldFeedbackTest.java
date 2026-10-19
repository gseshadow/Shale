package com.shale.ui.util;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.validation.FieldValidationException;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.css.PseudoClass;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Protects free entry, blur formatting, and selective error clearing in shared desktop phone editors. */
class ContactFieldFeedbackTest {
    private static class Input extends TextField {
        Input(String text) { super(text); }
        void blur() { setFocused(true); setFocused(false); }
    }
    @BeforeAll static void toolkit() { JavaFxTestSupport.ensureToolkitStarted(); }

    @Test void pastedTextFormatsOnBlurAndExtractsExtensionWithoutAFilter() {
        JavaFxTestSupport.runAndWait(() -> {
            Input number=new Input(""); Input extension=new Input("");
            ContactFieldFeedback.phone(number,extension,false,null);
            String pasted="Call: (505) 903 3568 x001";
            number.replaceText(0,0,pasted);
            assertEquals(pasted,number.getText(),"Typing/paste must remain unrestricted and unformatted");
            assertNull(number.getTextFormatter());
            number.blur();assertEquals("(505) 903-3568",number.getText());assertEquals("001",extension.getText());
            number.setText("1-505-903-3568");number.blur();assertEquals("+1 (505) 903-3568",number.getText());
            number.setText("(903)-3568");number.blur();assertEquals("903-3568",number.getText());
        });
    }
    @Test void correctedBlurClearsOnlyItsErrorAndInvalidDraftStaysIntact() {
        JavaFxTestSupport.runAndWait(() -> {
            Input number=new Input("0");Input extension=new Input("");Label summary=new Label();
            Label feedback=ContactFieldFeedback.phone(number,extension,true,null);
            VBox body=new VBox(number,extension,feedback,summary);ContactFieldFeedback.trackSummary(body,summary);
            FieldValidationException error=new FieldValidationException("phone","invalid_phone","Enter a usable phone.");
            summary.setText(error.getMessage()+"\nName is required.");ContactFieldFeedback.focus(body,error);
            number.setText("phone 5059033568");assertTrue(summary.getText().contains(error.getMessage()));
            assertTrue(number.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")));
            number.blur();assertEquals("Name is required.",summary.getText());
            assertFalse(number.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")));
            extension.setText("bad");extension.blur();assertEquals("bad",extension.getText());
            assertTrue(extension.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")));
            extension.setText("0001");extension.blur();assertEquals("0001",extension.getText());
            assertFalse(extension.getPseudoClassStates().contains(PseudoClass.getPseudoClass("invalid")));
        });
    }
    @Test void unchangedValidAndInvalidLegacyValuesAreNotRewritten() {
        JavaFxTestSupport.runAndWait(() -> {
            for(String original:new String[]{" 0 ","3035550123"}) {
                Input number=new Input(original);Input extension=new Input("");
                ContactFieldFeedback.phone(number,extension,false,()->number.getText().equals(original));
                number.blur();assertEquals(original,number.getText());assertEquals("",extension.getText());
            }
        });
    }
    @Test void stagedRowCorrectionClearsOnlyItsCollectionSummary() {
        JavaFxTestSupport.runAndWait(() -> {
            VBox stage=new VBox();Label summary=new Label("Invalid phone.\nName is required.");
            boolean[] valid={false};Runnable validator=()->{if(!valid[0])throw new IllegalArgumentException("Invalid phone.");};
            assertThrows(IllegalArgumentException.class,()->ContactFieldFeedback.validateStage(stage,validator));
            ContactFieldFeedback.trackStageSummary(stage,summary,validator);
            valid[0]=true;stage.getChildren().add(new Label("903-3568"));
            assertEquals("Name is required.",summary.getText());
        });
    }

}
