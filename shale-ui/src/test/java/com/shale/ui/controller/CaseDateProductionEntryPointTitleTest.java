package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.dto.CaseDateDto;
import com.shale.ui.component.dialog.CaseDateOccurrenceDialog;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CaseDateProductionEntryPointTitleTest {
    @Test void createAndUpdateEntryPointsPropagateNormalizedTitleAndExistingFields() {
        LocalDateTime start=LocalDateTime.of(2026,8,18,9,30), end=start.plusHours(1);
        var input=new CaseDateOccurrenceDialog.Input(12,"Scheduling conference",start,end,false,"keep notes");
        var create=CaseController.createCaseDateCommand(7,8,9,input);
        var existing=date(); var update=CaseController.updateCaseDateCommand(7,8,9,existing,input);
        assertAll(
            () -> assertEquals("Scheduling conference",create.title()), () -> assertEquals(12,create.caseDateTypeId()),
            () -> assertEquals(start,create.startsAt()), () -> assertEquals(end,create.endsAt()),
            () -> assertFalse(create.allDay()), () -> assertEquals("keep notes",create.notes()),
            () -> assertEquals("Scheduling conference",update.title()), () -> assertEquals(existing.id(),update.caseDateId()),
            () -> assertArrayEquals(existing.rowVer(),update.expectedRowVer()), () -> assertEquals("keep notes",update.notes()));
    }

    @Test void createAndUpdateEntryPointsConvertBlankTitleToNull() {
        var input=new CaseDateOccurrenceDialog.Input(12,"   ",LocalDateTime.of(2026,8,18,0,0),null,true,"notes");
        assertNull(CaseController.createCaseDateCommand(7,8,9,input).title());
        assertNull(CaseController.updateCaseDateCommand(7,8,9,date(),input).title());
    }

    private static CaseDateDto date(){ return new CaseDateDto(42,7,9,3,"hearing","Hearing",null,"HEARING",null,true,"Old",null,null,true,"notes",null,1,null,null,null,null,new byte[]{1,2}); }
}
