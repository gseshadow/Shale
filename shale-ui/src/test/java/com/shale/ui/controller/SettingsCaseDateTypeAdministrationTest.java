package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import com.shale.core.dto.EffectiveCaseDateTypeDto;

final class SettingsCaseDateTypeAdministrationTest {
    private static final String SOURCE = read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String FXML = read("src/main/resources/fxml/settings.fxml");
    private static final String CARDS_CSS = read("src/main/resources/css/foundation/cards.css");
    private static String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    private static String method(String signature){int start=SOURCE.indexOf(signature);assertTrue(start>=0);int brace=SOURCE.indexOf('{',start),depth=0;for(int i=brace;i<SOURCE.length();i++){char c=SOURCE.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return SOURCE.substring(start,i+1);}throw new AssertionError("unterminated method");}
    @Test void settingsNavigationContainsCaseDateTypesManager(){
        assertTrue(FXML.contains("caseDateTypeAdministrationSection"));
        assertTrue(FXML.contains("Case Date Types"));
        assertTrue(SOURCE.contains("loadCaseDateTypesAsync(null);"));
        assertTrue(SOURCE.contains("caseService.listCaseDateTypesForAdministration(tenantId,actorUserId)"));
    }
    @Test void mutationsUseServiceBoundaryAndSemanticControls(){
        assertTrue(SOURCE.contains("caseService.createCaseDateType"));
        assertTrue(SOURCE.contains("caseService.updateCaseDateType"));
        assertTrue(SOURCE.contains("caseService.setCaseDateTypeActive"));
        assertTrue(SOURCE.contains("caseService.resetCaseDateTypeOverride"));
        assertTrue(SOURCE.contains("configureCaseDateTypeActionRow()"));
        assertTrue(SOURCE.contains("AppDialogs.showConfirmation"));
        assertFalse(SOURCE.contains("new CaseDateDao"));
        assertTrue(SOURCE.contains("protectedType()?\"Protected system type\":\"Custom type\""));
        assertTrue(SOURCE.contains("updateCaseDateTypeActionState(null)"));
        assertTrue(SOURCE.contains("editCaseDateTypeButton.setDisable(!editable)"));
        assertTrue(SOURCE.contains("toggleCaseDateTypeButton.setDisable(!toggle)"));
        assertTrue(SOURCE.contains("removeCaseDateTypeButton.setDisable(!remove)"));
        assertTrue(SOURCE.contains("row.active()?\"Deactivate\":\"Activate\""));
        assertTrue(SOURCE.contains("selected.id(),selected.rowVer()"));
        assertTrue(SOURCE.contains("publishCaseDateTypeChanged"));
    }
    @Test void selectionUsesSharedPseudoClassAndAuthoritativeIdentity(){
        assertTrue(SOURCE.contains("card.setUserData(row)"));
        assertTrue(SOURCE.contains("card.pseudoClassStateChanged(SELECTED_CARD,selectedCaseDateTypeRow!=null&&selectedCaseDateTypeRow.id()==row.id())"));
        assertTrue(SOURCE.contains("updateSelectionStyles(caseDateTypeCardsContainer,row.id())"));
        assertTrue(SOURCE.contains("value instanceof CaseDateTypeViewRow row ? row.id()"));
        assertTrue(SOURCE.contains("card.setFocusTraversable(true)"));
        assertTrue(SOURCE.contains("e.getCode()==KeyCode.ENTER||e.getCode()==KeyCode.SPACE"));
        assertFalse(SOURCE.contains("selectCaseDateTypeRow(row);loadCaseDateTypesAsync"));
        assertTrue(CARDS_CSS.contains(".shale-entity-card-selectable:selected"));
        assertTrue(CARDS_CSS.contains("-fx-border-color: -shale-color-primary-accent"));
        String card = method("private VBox buildCaseDateTypeCard");
        assertFalse(card.contains("cardButton("), "Case Date Types uses one coherent bottom action surface");
        assertFalse(card.contains("loadCaseDateTypesAsync("), "selection must not reload cards");
    }
    @Test void refreshPreservesOnlyMatchingIdAndRejectsStaleResults(){
        assertTrue(SOURCE.contains("if(generation!=caseDateTypeLoadGeneration)return"));
        assertTrue(SOURCE.contains("preserveCaseDateTypeSelection(rows,selectedId)"));
        assertTrue(SOURCE.contains("findFirst().orElse(null)"));
        assertTrue(SOURCE.contains("applyCaseDateTypeRows(generation,rows,successMessage)"));
    }
    @Test void overlayRowsBuildLikeOtherSettingsManagers(){
        var global = new EffectiveCaseDateTypeDto(1,null,"trial","Trial",null,"TRIAL","#111111",true,10,true,false,EffectiveCaseDateTypeDto.Origin.GLOBAL,new byte[]{1});
        var override = new EffectiveCaseDateTypeDto(2,7,"trial","Trial Tenant",null,"TRIAL","#222222",true,10,true,false,EffectiveCaseDateTypeDto.Origin.TENANT_OVERRIDE,new byte[]{2});
        var custom = new EffectiveCaseDateTypeDto(3,7,null,"Custom",null,"OTHER","#333333",false,20,true,false,EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,new byte[]{3});
        var rows = SettingsController.buildCaseDateTypeRows(List.of(global, override, custom), 7);
        assertEquals(List.of("Custom", "Trial Tenant"), rows.stream().map(SettingsController.CaseDateTypeViewRow::name).sorted().toList());
        assertTrue(rows.stream().anyMatch(r -> r.scopeLabel().equals("Tenant override")));
        assertTrue(rows.stream().anyMatch(r -> r.scopeLabel().equals("Tenant custom")));
        assertTrue(rows.stream().filter(r -> r.scopeLabel().equals("Tenant override")).allMatch(SettingsController.CaseDateTypeViewRow::protectedType));
        assertTrue(rows.stream().filter(SettingsController.CaseDateTypeViewRow::custom).noneMatch(SettingsController.CaseDateTypeViewRow::protectedType));
        assertEquals(3, SettingsController.preserveCaseDateTypeSelection(rows,3).id());
        assertNull(SettingsController.preserveCaseDateTypeSelection(rows,999));
        var sameName = new EffectiveCaseDateTypeDto(4,7,null,"Custom",null,"OTHER","#444444",false,30,true,false,EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,new byte[]{4});
        var duplicateNames = SettingsController.buildCaseDateTypeRows(List.of(custom,sameName),7);
        assertEquals(4, SettingsController.preserveCaseDateTypeSelection(duplicateNames,4).id(), "selection must use authoritative id, not display name or index");
        var protectedRow = rows.stream().filter(SettingsController.CaseDateTypeViewRow::protectedType).findFirst().orElseThrow();
        var customRow = rows.stream().filter(SettingsController.CaseDateTypeViewRow::custom).findFirst().orElseThrow();
        assertFalse(protectedRow.canEdit()); assertFalse(protectedRow.canToggleActive()); assertFalse(protectedRow.canRemove()); assertFalse(protectedRow.canReset());
        assertTrue(customRow.canEdit()); assertTrue(customRow.canToggleActive()); assertTrue(customRow.canRemove()); assertFalse(customRow.canReset());
        assertTrue(SOURCE.contains("explainProtectedCaseDateType();return"));
        assertTrue(SOURCE.contains("Built into Shale. This Case Date Type cannot be customized"));
    }
}
