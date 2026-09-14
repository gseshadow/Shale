package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.dto.CaseDateDto;
import com.shale.ui.component.TimeDurationInput;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.junit.jupiter.api.Test;

class CaseDateOccurrenceDialogTitleTest {
    @Test void productionEditorRendersVisibleAssociatedTitleAndPreservesExistingFields() {
        JavaFxTestSupport.runAndWait(() -> {
            TextField title=CaseDateOccurrenceDialog.createTitleField(date("Scheduling conference"));
            ComboBox<String> type=new ComboBox<>(); DatePicker start=new DatePicker(); TimeDurationInput timing=new TimeDurationInput();
            DatePicker end=new DatePicker(); CheckBox allDay=new CheckBox(); TextArea notes=new TextArea();
            GridPane grid=CaseDateOccurrenceDialog.createEditorGrid(type,title,start,timing,end,allDay,notes);
            Scene scene=new Scene(grid); grid.applyCss(); grid.layout();
            Label label=(Label)scene.lookup("#case-date-occurrence-title-label");
            assertAll(() -> assertNotNull(label), () -> assertEquals("Title",label.getText()),
                    () -> assertTrue(label.isVisible()), () -> assertSame(title,label.getLabelFor()),
                    () -> assertEquals("Scheduling conference",title.getText()),
                    () -> assertTrue(grid.getChildren().containsAll(java.util.List.of(type,start,timing,end,allDay,notes))));
        });
    }

    @Test void titleNormalizationTrimsAndConvertsBlankToNull() {
        assertEquals("Hearing day one",CaseDateOccurrenceDialog.normalizeTitle("  Hearing day one  "));
        assertNull(CaseDateOccurrenceDialog.normalizeTitle("   \t"));
        assertNull(CaseDateOccurrenceDialog.normalizeTitle(null));
    }

    private static CaseDateDto date(String title){ return new CaseDateDto(42,7,9,3,"hearing","Hearing",null,"HEARING",null,true,title,null,null,true,"notes",null,1,null,null,null,null,new byte[]{1}); }
}
