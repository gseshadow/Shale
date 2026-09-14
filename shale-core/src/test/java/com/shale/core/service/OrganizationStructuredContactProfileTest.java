package com.shale.core.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class OrganizationStructuredContactProfileTest {
	@Test void legacyCreateAdapterProducesOwnedRowsWithoutManufacturingBlanks() {
		var fields=new OrganizationServicePort.OrganizationFields("Org"," 555 "," ","a@example.test",null,null,null,"Town",null,null,null,null);
		var mutation=OrganizationServicePort.structuredCreateFromLegacy(fields);
		assertTrue(mutation.phones().owned());assertEquals(1,mutation.phones().rows().size());
		assertTrue(mutation.emails().owned());assertEquals(1,mutation.emails().rows().size());
		assertEquals(1,mutation.addresses().rows().size());assertTrue(mutation.websites().rows().isEmpty());
	}
 @Test void completeHistoryIsImmutableOrderedAndRowVersionsAreDefensive(){
  byte[] token={1,2}; var history=phone(9,0,true,false,token); var active=phone(3,2,false,true,new byte[]{3});
  var profile=profile(List.of(history,active)); token[0]=8;
  assertEquals(List.of(3L,9L),profile.phones().stream().map(OrganizationServicePort.OrganizationPhoneNumber::id).toList());
  assertEquals(List.of(3L),profile.activePhones().stream().map(OrganizationServicePort.OrganizationPhoneNumber::id).toList());
  assertEquals(3,profile.primaryPhone().orElseThrow().id()); assertThrows(UnsupportedOperationException.class,()->profile.phones().add(active));
  assertArrayEquals(new byte[]{1,2},profile.phones().get(1).rowVer());var exposed=profile.phones().get(1).rowVer();exposed[0]=7;assertArrayEquals(new byte[]{1,2},profile.phones().get(1).rowVer());
 }
 @Test void deletedPrimaryNeverAppearsInPrimaryProjectionAndFaxRemainsDistinct(){
  var deletedFax=new OrganizationServicePort.OrganizationPhoneNumber(1,7,4,OrganizationServicePort.OrganizationPhoneKind.FAX,"FAX","555",null,null,true,0,true,lifecycle(),new byte[]{1});
  var voice=phone(2,0,false,true,new byte[]{2});var p=profile(List.of(deletedFax,voice));
  assertTrue(p.primaryFax().isEmpty());assertEquals(2,p.primaryPhone().orElseThrow().id());
 }
 @Test void orderedNonPrimaryFaxCanOwnLegacyProjectionAlongsidePrimaryVoice(){
  var fax=new OrganizationServicePort.OrganizationPhoneNumber(4,7,4,OrganizationServicePort.OrganizationPhoneKind.FAX,"FAX","555-0199",null,null,false,1,false,lifecycle(),new byte[]{4});
  var voice=phone(2,0,false,true,new byte[]{2});var c=new OrganizationServicePort.StructuredContactCompatibility(OrganizationServicePort.CompatibilityState.MATCHING,OrganizationServicePort.CompatibilityState.MATCHING,OrganizationServicePort.CompatibilityState.BOTH_ABSENT,OrganizationServicePort.CompatibilityState.BOTH_ABSENT,OrganizationServicePort.CompatibilityState.BOTH_ABSENT);
  var p=new OrganizationServicePort.OrganizationStructuredContactProfile(4,7,List.of(fax,voice),List.of(),List.of(),List.of(),Optional.of(voice),Optional.of(fax),Optional.empty(),Optional.empty(),Optional.empty(),c);
  assertEquals(2,p.primaryPhone().orElseThrow().id());assertEquals(4,p.primaryFax().orElseThrow().id());assertFalse(p.primaryFax().orElseThrow().primary());
 }
 @Test void exactParticipationAndOpeningTokensAreExplicitAndDefensive(){byte[] rv={1};var row=new OrganizationServicePort.StagedOrganizationWebsite(9L,rv,OrganizationServicePort.OrganizationWebsiteKind.MAIN,"example.test",true,0,false);var exact=OrganizationServicePort.OwnedContactCollection.exact(List.of(row));rv[0]=7;assertTrue(exact.owned());assertArrayEquals(new byte[]{1},exact.rows().getFirst().expectedRowVer());assertFalse(OrganizationServicePort.OwnedContactCollection.omitted().owned());assertThrows(UnsupportedOperationException.class,()->exact.rows().clear());}
 private static OrganizationServicePort.OrganizationPhoneNumber phone(long id,int order,boolean deleted,boolean primary,byte[] rv){return new OrganizationServicePort.OrganizationPhoneNumber(id,7,4,OrganizationServicePort.OrganizationPhoneKind.WORK,"WORK","555",null,null,primary,order,deleted,lifecycle(),rv);}
 private static OrganizationServicePort.ContactLifecycle lifecycle(){return new OrganizationServicePort.ContactLifecycle(Instant.EPOCH,null,null,null,null,null);}
 private static OrganizationServicePort.OrganizationStructuredContactProfile profile(List<OrganizationServicePort.OrganizationPhoneNumber> phones){var c=new OrganizationServicePort.StructuredContactCompatibility(OrganizationServicePort.CompatibilityState.MATCHING,OrganizationServicePort.CompatibilityState.BOTH_ABSENT,OrganizationServicePort.CompatibilityState.BOTH_ABSENT,OrganizationServicePort.CompatibilityState.BOTH_ABSENT,OrganizationServicePort.CompatibilityState.BOTH_ABSENT);return new OrganizationServicePort.OrganizationStructuredContactProfile(4,7,phones,List.of(),List.of(),List.of(),Optional.ofNullable(phones.stream().filter(x->!x.deleted()&&x.primary()&&!x.fax()).findFirst().orElse(null)),Optional.empty(),Optional.empty(),Optional.empty(),Optional.empty(),c);}
}
