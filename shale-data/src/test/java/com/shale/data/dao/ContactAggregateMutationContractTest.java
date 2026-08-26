package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
final class ContactAggregateMutationContractTest {
 private static String source() throws Exception{return Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactMutationDao.java"));}
 @Test void ownsOneTransactionAndPrevalidatesBeforeStructuredMutation() throws Exception{String s=source();int method=s.indexOf("void aggregate(");String a=s.substring(method,s.indexOf("private static void validateName",method));assertTrue(a.contains("tx(c.shaleClientId(),c.actorUserId(),false"));assertTrue(a.indexOf("prevalidateIntent")<a.indexOf("updateStructuredContact"));assertTrue(a.contains("complete active assignment set"));assertTrue(a.contains("expectedContactUpdatedAt"));}
 @Test void coversRestorationRemovalOrderingExpertAndAuditInTransaction() throws Exception{String s=source();assertTrue(s.contains("removedAssignmentId"));assertTrue(s.contains("IsDeleted=?"));assertTrue(s.contains("DisplayOrder=?"));assertTrue(s.contains("recomputeExpert(con"));assertTrue(s.contains("EntityType.CONTACT,c.contactId(),EntityActionAuditEvent.Action.UPDATED"));assertTrue(s.contains("EntityType.CONTACT_CREDENTIAL,c.contactId(),EntityActionAuditEvent.Action.REORDERED"));}
 @Test void neverMutatesCaseRoleTables() throws Exception{String aggregate=source().substring(source().indexOf("void aggregate("),source().indexOf("private <T>T tx"));assertFalse(aggregate.contains("CaseParties"));assertFalse(aggregate.contains("PartyRoles"));assertFalse(aggregate.contains("CaseContacts"));}
}
