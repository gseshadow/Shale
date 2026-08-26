package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ContactAggregateMutationContractTest {
    private static final String AGGREGATE_SIGNATURE = "void aggregate(UpdateContactProfileCommand c)";
    private static final String CONTACT_UPDATE_SIGNATURE =
            "private static void updateStructuredContact(Connection con,UpdateContactProfileCommand c)";

    private static String source() throws Exception {
        return normalizeLineEndings(Files.readString(
                Path.of("src/main/java/com/shale/data/dao/ContactMutationDao.java")));
    }

    @Test
    void ownsOneTransactionAndPrevalidatesBeforeStructuredMutation() throws Exception {
        String aggregate = extractMethod(source(), AGGREGATE_SIGNATURE);

        assertEquals(1, occurrences(aggregate, "tx(c.shaleClientId(),c.actorUserId(),false"),
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
        assertTrue(aggregate.contains("recomputeExpert(con"), "Expert recomputation must remain in aggregate transaction");
        assertTrue(aggregate.contains("EntityType.CONTACT_CREDENTIAL"),
                "Credential assignment reorder audit must remain in aggregate transaction");
        assertTrue(aggregate.contains("EntityActionAuditEvent.Action.REORDERED"),
                "Credential ordering audit action must remain in aggregate transaction");
        assertTrue(aggregate.contains("EntityType.CONTACT,c.contactId(),EntityActionAuditEvent.Action.UPDATED"),
                "CONTACT/UPDATED audit must remain in aggregate transaction");
    }

    @Test
    void structuredContactUpdateUsesExactContactConcurrencyGuard() throws Exception {
        String update = extractMethod(source(), CONTACT_UPDATE_SIGNATURE);

        for (String field : new String[] {
                "Name=?", "Prefix=?", "FirstName=?", "MiddleName=?", "LastName=?", "PreferredName=?", "Suffix=?"
        }) {
            assertTrue(update.contains(field), "structured Contact update must include " + field);
        }
        assertFalse(update.contains("UpdatedByUserId"),
                "dbo.Contacts does not own UpdatedByUserId");
        assertFalse(update.contains("c.actorUserId()"),
                "the removed UpdatedByUserId placeholder must not retain an actor binding");
        assertEquals(10, occurrences(update, "=?"),
                "non-null concurrency SQL must have seven fields, two scope IDs, and one timestamp parameter");
        assertEquals(7, occurrences(update, "setString(p,i++"),
                "the seven Contact fields must be bound first and in SQL order");
        assertTrue(update.contains("p.setInt(i++,c.contactId());\n            p.setInt(i++,c.shaleClientId())"),
                "Contact and tenant bindings must immediately follow the seven field bindings");
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
