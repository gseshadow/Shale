package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.service.OrganizationServicePort;
import com.shale.ui.controller.support.OrganizationTypeAssignmentStage;
import com.shale.ui.testutil.JavaFxTestSupport;

final class NewOrganizationSemanticControlsTest {
    @Test void createAndEditShareOneStructuredEditorAndLegacyControlsAreAbsent() throws Exception {
        String create=Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewOrganizationController.java"));
        String edit=Files.readString(Path.of("src/main/java/com/shale/ui/controller/EditOrganizationDialog.java"));
        String fxml=Files.readString(Path.of("src/main/resources/fxml/new-organization.fxml"));
        assertTrue(create.contains("OrganizationAggregateEditor.forCreate"));
        assertTrue(edit.contains("OrganizationAggregateEditor.forEdit"));
        for(String legacy:List.of("phoneField","faxField","emailField","websiteField","address1Field","address2Field","cityField","stateField","postalCodeField","countryField"))assertFalse(fxml.contains(legacy),"legacy scalar control must be removed: "+legacy);
        assertTrue(fxml.contains("contact-editor-surface")&&fxml.contains("contact-editor-section-scroll"));
    }

    @Test void createStageStartsEmptyAndOwnsAllFourStructuredCollections() {
        JavaFxTestSupport.runAndWait(()->{
            var type=new OrganizationServicePort.OrganizationTypeDefinition(1,null,"provider","Provider",null,"#123456",0,true,false,OrganizationServicePort.OrganizationTypeOrigin.GLOBAL,null);
            var editor=OrganizationAggregateEditor.forCreate(OrganizationTypeAssignmentStage.forCreate(List.of(type)));
            var mutation=editor.contactMutation();
            assertTrue(mutation.phones().owned()&&mutation.emails().owned()&&mutation.addresses().owned()&&mutation.websites().owned());
            assertTrue(mutation.phones().rows().isEmpty()&&mutation.emails().rows().isEmpty()&&mutation.addresses().rows().isEmpty()&&mutation.websites().rows().isEmpty());
            assertFalse(editor.isDirty());
            assertEquals("Exactly one eligible primary Organization Type is required.",editor.validationError());
            editor.assignmentStage().add(type);
            assertTrue(editor.assignmentStage().assigned().getFirst().primary());
        });
    }
}
