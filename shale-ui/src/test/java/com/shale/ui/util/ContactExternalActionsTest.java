package com.shale.ui.util;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
class ContactExternalActionsTest {
 @Test void buildsEncodedUrisAndDelegates(){
  assertEquals("tel:+442079460018;ext=9",ContactExternalActions.telephone("+44 20 7946 0018;ext=9").toASCIIString());
  assertEquals("mailto:person@example.com",ContactExternalActions.email("person@example.com").toASCIIString());
  assertTrue(ContactExternalActions.maps("10 Main St, Montréal").toASCIIString().contains("query=10+Main+St%2C+Montr%C3%A9al"));
  var opened=new ArrayList<java.net.URI>();new ContactExternalActions(opened::add).open(ContactExternalActions.email("a@b.co"));assertEquals(1,opened.size());
 }
 @Test void missingHandlerIsNonPlatformSpecificFailure(){assertThrows(IllegalStateException.class,()->new ContactExternalActions(u->{throw new UnsupportedOperationException();}).open(ContactExternalActions.email("a@b.co")));}
 @Test void organizationTargetsUseDialablePhoneAndSafeWebSchemes(){
  assertEquals("tel:+13035550123;ext=42",ContactExternalActions.telephone("+13035550123","42").toString());
  assertEquals("https://example.com/path",ContactExternalActions.website("example.com/path").toString());
  assertThrows(IllegalArgumentException.class,()->ContactExternalActions.website("javascript:alert(1)"));
  assertThrows(IllegalArgumentException.class,()->ContactExternalActions.telephone("not a number",null));
 }
}
