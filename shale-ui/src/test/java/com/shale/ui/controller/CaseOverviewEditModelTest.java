package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.dto.EffectiveCaseDateTypeDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaseOverviewEditModelTest {
 @Test void editingIsStagedOrderedUniqueAndCanBeExplicitlyEmpty(){var m=new CaseOverviewEditModel(List.of(t(1,true),t(2,true),t(3,true)),List.of(t(1,true),t(2,true)),7);assertFalse(m.changed());m.select(3);m.select(3);assertEquals(List.of(1,2,3),m.selectedIds());assertTrue(m.moveUp(3));assertEquals(List.of(1,3,2),m.selectedIds());assertFalse(m.canMoveUp(1));assertFalse(m.canMoveDown(2));m.remove(1);m.remove(3);m.remove(2);assertEquals(List.of(),m.selectedIds());assertTrue(m.layoutChanged());}
 @Test void intakeClearAndFailedSaveRemainStagedAndDoubleSaveIsPrevented(){var m=new CaseOverviewEditModel(List.of(t(1,true)),List.of(t(1,true)),9);m.setIntakeUserId(null);assertTrue(m.intakeChanged());assertTrue(m.beginSave());assertFalse(m.beginSave());m.saveFailed();assertNull(m.intakeUserId());assertTrue(m.beginSave());}
 @Test void historicalConfiguredTypeIsPreservedUntilExplicitRemoval(){var historical=t(8,false);var m=new CaseOverviewEditModel(List.of(t(1,true)),List.of(historical,t(1,true)),null);assertEquals(List.of(8,1),m.selectedIds());assertTrue(m.moveDown(8));m.remove(8);assertEquals(List.of(1),m.selectedIds());}
 private static EffectiveCaseDateTypeDto t(int id,boolean active){return new EffectiveCaseDateTypeDto(id,7,"k"+id,"Type "+id,null,"OTHER","#123456",false,id,active,!active,EffectiveCaseDateTypeDto.Origin.TENANT_CREATED,new byte[]{1});}
}
