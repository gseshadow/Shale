package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import com.shale.core.dto.EffectiveCaseDateTypeDto;

final class SettingsCaseDateTypeAdministrationTest {
    private static final String SOURCE = read("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final String FXML = read("src/main/resources/fxml/settings.fxml");
    private static String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new ExceptionInInitializerError(e);}}
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
        assertTrue(SOURCE.contains("configureRequestActionRow(caseDateTypeActionRow"));
        assertTrue(SOURCE.contains("AppDialogs.showConfirmation"));
        assertFalse(SOURCE.contains("new CaseDateDao"));
    }
    @Test void overlayRowsBuildLikeOtherSettingsManagers(){
        var global = new EffectiveCaseDateTypeDto(1,null,"trial","Trial",null,"TRIAL","#111111",true,10,true,false,EffectiveCaseDateTypeDto.Origin.GLOBAL,new byte[]{1});
        var override = new EffectiveCaseDateTypeDto(2,7,"trial","Trial Tenant",null,"TRIAL","#222222",true,10,true,false,EffectiveCaseDateTypeDto.Origin.TENANT_OVERRIDE,new byte[]{2});
        var custom = new EffectiveCaseDateTypeDto(3,7,null,"Custom",null,"OTHER","#333333",false,20,true,false,EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,new byte[]{3});
        var rows = SettingsController.buildCaseDateTypeRows(List.of(global, override, custom), 7);
        assertEquals(List.of("Custom", "Trial Tenant"), rows.stream().map(SettingsController.CaseDateTypeViewRow::name).sorted().toList());
        assertTrue(rows.stream().anyMatch(r -> r.scopeLabel().equals("Tenant override")));
        assertTrue(rows.stream().anyMatch(r -> r.scopeLabel().equals("Tenant custom")));
    }
}
