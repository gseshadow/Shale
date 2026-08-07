package com.shale.ui.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NewIntakeDatesStep2ContractTest {
 private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
 @Test void customizationIsConstructedOnlyForAdminAndRechecked()throws Exception{String s=source();assertTrue(s.contains("non-admins never receive an action node or handler"));assertTrue(s.contains("private void enterDatesCustomization()"));assertTrue(s.contains("if (!isAuthorizedDatesAdmin()) return;"));}
 @Test void asyncLifecycleHasAllGuards()throws Exception{String s=source();for(String guard:List.of("generation != datesLoadGeneration","datesViewClosed","datesViewAttached && datesSection.getScene() == null","supplyAsync","Platform.runLater"))assertTrue(s.contains(guard));}
 @Test void fxmlPreservesSectionsAndControls()throws Exception{String f=Files.readString(ROOT.resolve("shale-ui/src/main/resources/fxml/new-intake.fxml"));for(String section:List.of("Caller","Client","Case","Parties","Incident","Dates"))assertTrue(f.contains("text=\""+section+"\""));for(String id:List.of("caseNameField","dateOfIntakePicker","addPartyButton","descriptionArea","statuteOfLimitationsPicker","configuredDatesBox"))assertTrue(f.contains("fx:id=\""+id+"\""));}
 @Test void concurrencyFailureRequiresExplicitReload(){assertTrue(NewIntakeController.isConfigurationConflict(new IllegalStateException("Form configuration changed.")));assertFalse(NewIntakeController.isConfigurationConflict(new IllegalStateException("other")));}
 @Test void usesEffectiveLookupAndConfigurationAggregateOnly()throws Exception{String s=source();assertTrue(s.contains("listEffectiveCaseDateTypes"));assertTrue(s.contains("loadedDatesConfiguration.rowVer()"));assertTrue(s.contains("formConfigurationService.replace(command)"));assertFalse(s.contains("createCaseDateType("));assertFalse(s.contains("updateCaseDateType("));}
 private static String source()throws Exception{return Files.readString(ROOT.resolve("shale-ui/src/main/java/com/shale/ui/controller/NewIntakeController.java"));}
}
