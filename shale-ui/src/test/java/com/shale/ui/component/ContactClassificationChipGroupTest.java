package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.shale.core.service.ContactServicePort.ClassificationPresentation;
import com.shale.core.service.ContactServicePort.DefinitionCategory;
import com.shale.ui.testutil.JavaFxTestSupport;
import javafx.scene.control.Label;

class ContactClassificationChipGroupTest {
    @Test void rendersOrderedCategoriesColorsFallbackAndBothSizesWithoutOverflowHiding() {
        JavaFxTestSupport.runAndWait(()->{
            var values=List.of(
                    new ClassificationPresentation(DefinitionCategory.CONTACT_TYPE,1,"Expert","#123456",0),
                    new ClassificationPresentation(DefinitionCategory.SPECIALTY,2,"Radiology","bad",0),
                    new ClassificationPresentation(DefinitionCategory.CREDENTIAL,3,"M.D.","#FFFFFF",0));
            var compact=new ContactClassificationChipGroup(values,ContactClassificationChipGroup.Size.COMPACT);
            var standard=new ContactClassificationChipGroup(values,ContactClassificationChipGroup.Size.STANDARD);
            assertEquals(List.of("Expert","Radiology","M.D."),compact.getChildren().stream().map(n->((Label)n).getText()).toList());
            assertTrue(compact.getStyleClass().contains("contact-classification-chip-group-compact"));
            assertTrue(standard.getStyleClass().contains("contact-classification-chip-group-standard"));
            assertTrue(compact.getChildren().get(0).getStyle().contains("#123456"));
            assertTrue(compact.getChildren().get(1).getStyle().contains("#6C757D"));
            assertEquals(3,compact.getChildren().stream().filter(javafx.scene.Node::isManaged).count());
            var empty=new ContactClassificationChipGroup(List.of(),ContactClassificationChipGroup.Size.COMPACT);
            assertTrue(empty.getChildren().isEmpty()); assertFalse(empty.isManaged());
        });
    }
}
