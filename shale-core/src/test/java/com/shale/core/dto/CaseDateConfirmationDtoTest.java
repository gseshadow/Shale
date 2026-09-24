package com.shale.core.dto;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class CaseDateConfirmationDtoTest {
    private static CaseDateDto date(){return new CaseDateDto(1,7,2,3,"x","X",null,"DEADLINE",null,false,null,
            LocalDateTime.of(2026,1,1,0,0),null,true,null,LocalDateTime.of(2025,1,1,0,0),4,"A",null,null,null,new byte[]{1});}
    @Test void representsAllThreeStatesWithoutInventingAWorkflow(){
        assertAll("explicit workflow states",
                ()->assertEquals(CaseDateConfirmationDto.Status.NOT_REQUIRED,new CaseDateConfirmationDto(date(),1,CaseDateConfirmationDto.Status.NOT_REQUIRED,null,null,null,null,null,null,null,null).status()),
                ()->assertEquals(CaseDateConfirmationDto.Status.PENDING,new CaseDateConfirmationDto(date(),2,CaseDateConfirmationDto.Status.PENDING,10L,new byte[]{1},11L,3L,20,null,null,null).status()),
                ()->assertEquals(CaseDateConfirmationDto.Status.CONFIRMED,new CaseDateConfirmationDto(date(),2,CaseDateConfirmationDto.Status.CONFIRMED,10L,new byte[]{1},11L,3L,20,5,"User",LocalDateTime.now()).status()));
    }
    @Test void rejectsContradictoryState(){
        assertThrows(IllegalArgumentException.class,()->new CaseDateConfirmationDto(date(),1,CaseDateConfirmationDto.Status.NOT_REQUIRED,10L,null,null,null,null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->new CaseDateConfirmationDto(date(),1,CaseDateConfirmationDto.Status.CONFIRMED,10L,new byte[]{1},11L,1L,20,null,null,null));
    }
}
