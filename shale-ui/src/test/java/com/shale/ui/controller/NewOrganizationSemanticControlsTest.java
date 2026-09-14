package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.service.OrganizationServicePort;
import com.shale.ui.controller.support.OrganizationTypeAssignmentStage;
import com.shale.ui.testutil.JavaFxTestSupport;

import javafx.scene.control.TextField;

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
            assertTrue(java.util.stream.Stream.of(mutation.phones().rows(),mutation.emails().rows(),mutation.addresses().rows(),mutation.websites().rows())
                    .flatMap(List::stream).noneMatch(row -> hasIdentityOrRowVersion(row)),
                    "a new structured aggregate must not contain persisted child identities or concurrency tokens");
            assertFalse(editor.isDirty());
            assertEquals("Name is required.",editor.validationError());
            ((TextField)field(editor,"name")).setText("Example Organization");
            assertTrue(editor.isDirty(),"entering a name must make the initially clean editor dirty");
            assertEquals("Exactly one eligible primary Organization Type is required.",editor.validationError());
            editor.assignmentStage().add(type);
            assertTrue(editor.assignmentStage().assigned().getFirst().primary());
        });
    }

    private static Object field(Object target,String name){return assertDoesNotThrow(()->{var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);});}
    private static boolean hasIdentityOrRowVersion(Object row){try{var id=row.getClass().getMethod("id").invoke(row);var rowVer=row.getClass().getMethod("expectedRowVer").invoke(row);return id!=null||rowVer!=null;}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
}
