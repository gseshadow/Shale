package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ContactAggregateMutationContractTest {
    private static final String AGGREGATE_SIGNATURE = "void aggregate(UpdateContactProfileCommand c)";
    private static final String AGGREGATE_MUTATION_SIGNATURE =
            "private void mutateAggregate(Connection con,UpdateContactProfileCommand c,boolean creating)";
    private static final String CONTACT_UPDATE_SIGNATURE =
            "private void updateStructuredContact(Connection con,UpdateContactProfileCommand c)";

    private static String source() throws Exception {
        return normalizeLineEndings(Files.readString(
                Path.of("src/main/java/com/shale/data/dao/ContactMutationDao.java")));
    }

    @Test
    void ownsOneTransactionAndPrevalidatesBeforeStructuredMutation() throws Exception {
    	String mutationSource = source();
        String transactionEntry = extractMethod(mutationSource, AGGREGATE_SIGNATURE);
        String aggregate = extractMethod(mutationSource, AGGREGATE_MUTATION_SIGNATURE);

	        assertEquals(1, occurrences(transactionEntry, "tx(c.shaleClientId(),c.actorUserId(),false"),
                "aggregate must own exactly one transaction boundary");
        int typeValidation = aggregate.indexOf(
                "prevalidateIntent(con,c,DefinitionCategory.CONTACT_TYPE,c.contactTypes())");
        int specialtyValidation = aggregate.indexOf(
                "prevalidateIntent(con,c,DefinitionCategory.SPECIALTY,c.specialties())");
        int credentialValidation = aggregate.indexOf(
                "prevalidateIntent(con,c,DefinitionCategory.CREDENTIAL,c.credentials())");
        int structuredUpdate = aggregate.indexOf("updateStructuredContact(con,c)");
        assertOrderedBefore(typeValidation, structuredUpdate, "Contact Type prevalidation", "structured mutation");
        assertOrderedBefore(specialtyValidation, structuredUpdate, "Specialty prevalidation", "structured mutation");
        assertOrderedBefore(credentialValidation, structuredUpdate, "Credential prevalidation", "structured mutation");
        assertOrderedBefore(aggregate.indexOf("complete active assignment set"), structuredUpdate,
                "complete active Credential-set validation", "structured mutation");

        int typeMutation = aggregate.indexOf(
                "applyIntent(con,c,DefinitionCategory.CONTACT_TYPE,c.contactTypes())");
        int specialtyMutation = aggregate.indexOf(
                "applyIntent(con,c,DefinitionCategory.SPECIALTY,c.specialties())");
        int credentialMutation = aggregate.indexOf(
                "applyIntent(con,c,DefinitionCategory.CREDENTIAL,c.credentials())");
        assertOrderedBefore(structuredUpdate, typeMutation, "structured mutation", "Contact Type mutation");
        assertOrderedBefore(structuredUpdate, specialtyMutation, "structured mutation", "Specialty mutation");
        assertOrderedBefore(structuredUpdate, credentialMutation, "structured mutation", "Credential mutation");

        assertTrue(aggregate.contains("DisplayOrder=?"), "credential ordering must remain in aggregate transaction");
        assertTrue(aggregate.contains("RowVer=?"), "credential ordering must retain its RowVer guard");
        assertFalse(mutationSource.contains("recomputeExpert("),
                "Phase 3B must remove Expert scalar recomputation");

        assertFalse(mutationSource.contains("setExpert("),
                "Phase 3B must remove Expert scalar synchronization");

        assertFalse(mutationSource.contains("IsExpert"),
                "Phase 3B Contact mutations must not reference Contacts.IsExpert");
        assertTrue(aggregate.contains("EntityType.CONTACT_CREDENTIAL"),
                "Credential assignment reorder audit must remain in aggregate transaction");
        assertTrue(aggregate.contains("EntityActionAuditEvent.Action.REORDERED"),
                "Credential ordering audit action must remain in aggregate transaction");
        assertTrue(aggregate.contains("EntityType.CONTACT,c.contactId(),creating?EntityActionAuditEvent.Action.CREATED:EntityActionAuditEvent.Action.UPDATED"),
                "CONTACT/UPDATED audit must remain in aggregate transaction");
    }

    @Test
    void structuredContactUpdateUsesExactContactConcurrencyGuard() throws Exception {
        String update = extractMethod(source(), CONTACT_UPDATE_SIGNATURE);
        int updateSqlStart = update.indexOf("String sql=");
        String updateSql = update.substring(updateSqlStart, update.indexOf(';', updateSqlStart));
        String mutation = update.substring(updateSqlStart, update.indexOf("stale(p.executeUpdate())"));

        for (String field : new String[] {
                "Name=?", "Prefix=?", "FirstName=?", "MiddleName=?", "LastName=?", "PreferredName=?", "Suffix=?",
                "DateOfBirth=?", "Condition=?", "Notes=?", "IsDeceased=?"
        }) {
            assertTrue(updateSql.contains(field), "structured Contact update must include " + field);
        }
        assertTrue(updateSql.contains("Name=?,Prefix=?,FirstName=?,MiddleName=?,LastName=?,PreferredName=?,Suffix=?,"
                        + "DateOfBirth=?,Condition=?,Notes=?,IsDeceased=?"),
                "all eleven structured Contact fields must retain their authoritative SQL order");
        assertFalse(updateSql.contains("UpdatedByUserId"),
                "dbo.Contacts does not own UpdatedByUserId");
        assertFalse(mutation.contains("c.actorUserId()"),
                "the removed UpdatedByUserId placeholder must not retain an actor binding");
        assertEquals(14, occurrences(updateSql, "=?"),
                "non-null concurrency SQL must have eleven fields, two scope IDs, and one timestamp parameter");
        assertEquals(8, occurrences(update, "setString(p,i++"),
                "the seven name fields and Condition must use the shared nullable-string binding");

        assertEquals(1,
                occurrences(update, "p.setString(i++,normalizeNotes(c.notes()))"),
                "Notes must be normalized and bound exactly once");
        assertTrue(update.contains("setString(p,i++,c.condition());p.setString(i++,normalizeNotes(c.notes()));"
                        + "p.setBoolean(i++,c.deceased());"),
                "Condition, normalized Notes, and IsDeceased must follow DateOfBirth in SQL order");
        assertTrue(update.contains("p.setInt(i++,c.contactId());\n            p.setInt(i++,c.shaleClientId())"),
                "Contact and tenant bindings must immediately follow all eleven profile-field bindings");
        assertTrue(update.contains("WHERE Id=? AND ShaleClientId=?"),
                "Contact update must scope by authoritative Contact and tenant IDs");
        assertTrue(update.contains("ISNULL(IsDeleted,0)=0"), "Contact update must exclude deleted Contacts");
        assertTrue(update.contains("c.expectedContactUpdatedAt()==null"),
                "Contact update must branch on the exact loaded concurrency timestamp");
        assertTrue(update.contains("UpdatedAt IS NULL"),
                "legacy null concurrency must use an explicit IS NULL guard");
        assertTrue(update.contains("UpdatedAt=?"),
                "non-null Contact concurrency must be part of the UPDATE predicate");
        assertTrue(update.contains("p.setTimestamp(i,Timestamp.from(c.expectedContactUpdatedAt()))"),
                "non-null Contact concurrency must be bound as a prepared-statement parameter");
        assertTrue(update.contains("stale(p.executeUpdate())"), "a stale Contact update must be rejected");
        assertFalse(update.contains("WHERE Id=? AND ShaleClientId=? AND ISNULL(IsDeleted,0)=0\");"),
                "Contact update must not fall back to an unguarded last-write-wins predicate");
    }

    @Test
    void createOwnsOneTransactionForContactChildrenClassificationsAndAudit() throws Exception {
        String mutation=source();
        String create=extractMethod(mutation,"int createAggregate(CreateContactProfileCommand draft)");
        String shared=extractMethod(mutation,AGGREGATE_MUTATION_SIGNATURE);
        assertEquals(1,occurrences(create,"tx(draft.shaleClientId(),draft.actorUserId(),false"),
                "creation must own one tenant/actor transaction");
        assertTrue(create.contains("INSERT dbo.Contacts"),"the Contact must be inserted inside the aggregate transaction");
        assertTrue(create.contains("CreatedAt,UpdatedAt"),
                "creation must initialize both authoritative Contact timestamp columns");
        assertFalse(create.contains("CreatedByUserId"),
                "dbo.Contacts has no CreatedByUserId column");
        assertFalse(create.contains("UpdatedByUserId"),
                "dbo.Contacts has no UpdatedByUserId column");
        assertEquals(2,occurrences(create,"p.setTimestamp(i++,now)"),
                "CreatedAt and UpdatedAt must use the same transaction timestamp");
        assertTrue(create.contains("p.setInt(i++,draft.shaleClientId())"),
                "creation must preserve the authorized tenant owner");
        String insertSql=create.substring(create.indexOf("String sql="),create.indexOf(';',create.indexOf("String sql=")));
        int columnsStart=insertSql.indexOf('(')+1;
        int columnsEnd=insertSql.indexOf(')',columnsStart);
        Set<String> insertedColumns=Arrays.stream(insertSql.substring(columnsStart,columnsEnd).split(","))
                .map(String::trim).collect(Collectors.toSet());
        assertEquals(Set.of("ShaleClientId","Name","Prefix","FirstName","MiddleName","LastName",
                        "PreferredName","Suffix","DateOfBirth","Condition","Notes","IsDeceased","IsClient",
                        "IsDeleted","CreatedAt","UpdatedAt"),insertedColumns,
                "the create aggregate must use only authoritative production dbo.Contacts columns");
        assertEquals(14,occurrences(insertSql,"?"),
                "the Contact insert must retain exactly fourteen placeholders for its fourteen bound values");
        assertTrue(create.contains("p.setBoolean(i++,draft.deceased());p.setTimestamp(i++,now);p.setTimestamp(i++,now);try"),
                "the final bindings must align IsDeceased, CreatedAt, and UpdatedAt without stale actor parameters");
        assertTrue(create.contains("phi.auditCreate(con,draft.actorUserId(),\"Contacts\",\"Condition\""),
                "sensitive actor attribution must remain in the established PHI audit");
        assertTrue(create.contains("mutateAggregate(con,c,true)"),"children and classifications must share creation's transaction");
        assertTrue(shared.contains("applyIntent(con,c,DefinitionCategory.CONTACT_TYPE"));
        assertTrue(shared.contains("applyPhones(con,c,phones)"));
        assertTrue(shared.contains("Action.CREATED:EntityActionAuditEvent.Action.UPDATED"),
                "create and edit must emit their distinct authoritative audit actions");
    }

    @Test
    void balancedMethodExtractorSupportsNestedBracesAndEveryLineEnding() {
        String lf = "prefix\nvoid sample() {\n if (ready) {\n  run();\n }\n}\nsuffix";
        String expected = "void sample() {\n if (ready) {\n  run();\n }\n}";
        assertEquals(expected, extractMethod(lf, "void sample()"));
        assertEquals(expected, extractMethod(lf.replace("\n", "\r\n"), "void sample()"));
        assertEquals(expected, extractMethod(lf.replace("\n", "\r"), "void sample()"));

        AssertionError missing = assertThrows(AssertionError.class,
                () -> extractMethod("void other() {}", "void sample()"));
        assertTrue(missing.getMessage().contains("Missing method signature: void sample()"));
        AssertionError malformed = assertThrows(AssertionError.class,
                () -> extractMethod("void sample() { if (ready) { run(); }", "void sample()"));
        assertTrue(malformed.getMessage().contains("Missing matching brace for: void sample()"));
    }

    @Test
    void coversRestorationRemovalAndNeverMutatesCaseRoleTables() throws Exception {
        String source = source();
        String aggregate = extractMethod(source, AGGREGATE_SIGNATURE);
        assertTrue(source.contains("removedAssignmentId"));
        assertTrue(source.contains("IsDeleted=?"));
        assertFalse(aggregate.contains("CaseParties"));
        assertFalse(aggregate.contains("PartyRoles"));
        assertFalse(aggregate.contains("CaseContacts"));
    }

    private static String extractMethod(String rawSource, String signature) {
        String normalized = normalizeLineEndings(rawSource);
        int signatureStart = normalized.indexOf(signature);
        if (signatureStart < 0) {
            throw new AssertionError("Missing method signature: " + signature);
        }
        int openingBrace = normalized.indexOf('{', signatureStart + signature.length());
        if (openingBrace < 0) {
            throw new AssertionError("Missing opening brace for: " + signature);
        }
        int depth = 0;
        for (int index = openingBrace; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return normalized.substring(signatureStart, index + 1);
            }
        }
        throw new AssertionError("Missing matching brace for: " + signature);
    }

    private static String normalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static void assertOrderedBefore(int first, int second, String firstLabel, String secondLabel) {
        assertTrue(first >= 0, "Missing " + firstLabel);
        assertTrue(second >= 0, "Missing " + secondLabel);
        assertTrue(first < second, firstLabel + " must occur before " + secondLabel);
    }

    private static int occurrences(String value, String token) {
        int count = 0;
        for (int index = 0; (index = value.indexOf(token, index)) >= 0; index += token.length()) {
            count++;
        }
        return count;
    }
}
