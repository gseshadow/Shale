package com.shale.core.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContactCompleteProfileCommandTest {
 @Test void completeCommandRetainsPersonalDetailsAndOptimisticTokens(){
  byte[] rowVer={1,2,3};
  var phone=new ContactServicePort.IntendedPhoneNumber(91L,rowVer,"WORK","+44 20 1234",null,true,false,0);
  var command=new ContactServicePort.UpdateContactProfileCommand(7,8,9,"Explicit display",
    new ContactServicePort.StructuredName("Dr","Ada",null,"Lovelace",null,null),LocalDate.of(1815,12,10),
    "Existing condition",true,Instant.EPOCH,List.of(),List.of(),List.of(),List.of(phone),List.of(),List.of());
  rowVer[0]=9;
  assertEquals("Explicit display",command.displayName());
  assertEquals(LocalDate.of(1815,12,10),command.dateOfBirth());
  assertEquals("Existing condition",command.condition());assertTrue(command.deceased());
  assertArrayEquals(new byte[]{1,2,3},command.phoneNumbers().get(0).expectedRowVer());
 }
}
