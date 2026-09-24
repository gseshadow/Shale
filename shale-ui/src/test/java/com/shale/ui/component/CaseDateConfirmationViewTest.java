package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.dto.CaseDateConfirmationDto;
import com.shale.core.dto.CaseDateDto;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

final class CaseDateConfirmationViewTest {
    @Test void pendingNamesRequiredBuiltInOrCustomRole() {
        assertEquals("Confirmation needed — Attorney", CaseDateConfirmationView.message(pending(), "Attorney"));
        assertEquals("Confirmation needed — Senior Reviewer", CaseDateConfirmationView.message(pending(), "Senior Reviewer"));
    }

    @Test void confirmedNamesActorAndTimeWhileHistoricalUnenrolledHasNoMarker() {
        var confirmed = new CaseDateConfirmationDto(date(), 2, CaseDateConfirmationDto.Status.CONFIRMED, 3L, new byte[]{4}, 5L, 1L, 8, 19, "Alex Smith", LocalDateTime.of(2026,9,24,14,30));
        assertEquals("Confirmed by Alex Smith on Sep 24, 2026 2:30 PM", CaseDateConfirmationView.message(confirmed, "Attorney"));
        var historical = new CaseDateConfirmationDto(date(), 1, CaseDateConfirmationDto.Status.NOT_REQUIRED, null, null, null, null, null, null, null, null);
        assertEquals("", CaseDateConfirmationView.message(historical, null));
    }

    private static CaseDateConfirmationDto pending(){return new CaseDateConfirmationDto(date(),2,CaseDateConfirmationDto.Status.PENDING,3L,new byte[]{4},5L,1L,8,null,null,null);}
    private static CaseDateDto date(){return new CaseDateDto(2,7,10,12,"STATUTE_OF_LIMITATIONS","SOL",null,"DEADLINE","#123456",false,null,LocalDateTime.of(2026,10,1,0,0),null,true,null,null,1,null,null,null,null,new byte[]{9});}
}
