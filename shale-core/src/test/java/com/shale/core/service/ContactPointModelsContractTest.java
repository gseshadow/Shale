package com.shale.core.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class ContactPointModelsContractTest {
 @Test void contactPointRowVersionsAreDefensivelyCopied(){
  byte[] source={1,2};
  var phone=new ContactServicePort.ContactPhoneNumber(1,"MOBILE","+44 20 1234", "+44201234",null,true,0,false,Instant.EPOCH,null,source);
  source[0]=9; assertArrayEquals(new byte[]{1,2},phone.rowVer());
  byte[] exposed=phone.rowVer();exposed[1]=9;assertArrayEquals(new byte[]{1,2},phone.rowVer());
 }
 @Test void intendedExistingIdentityAndConcurrencyAreExplicit(){
  byte[] rv={3,4};var email=new ContactServicePort.IntendedEmailAddress(7L,rv,"WORK","a@example.test",false,false,0);
  rv[0]=8;assertEquals(7,email.id());assertArrayEquals(new byte[]{3,4},email.expectedRowVer());
 }
}
