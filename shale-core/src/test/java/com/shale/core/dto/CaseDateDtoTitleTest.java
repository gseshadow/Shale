package com.shale.core.dto;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CaseDateDtoTitleTest {
    @Test void displayTitleUsesOccurrenceAndFallsBackWithoutChangingIdentity() {
        CaseDateDto titled=date("  Trial day  ");
        CaseDateDto blank=date("   ");
        assertEquals("  Trial day  ", titled.displayTitle());
        assertEquals("Trial", blank.displayTitle());
        assertEquals(titled.id(), blank.id());
    }
    private static CaseDateDto date(String title) {
        return new CaseDateDto(42,7,9,3,"trial","Trial",null,"TRIAL",null,true,title,null,null,true,null,null,1,null,null,null,null,new byte[]{1});
    }
}
