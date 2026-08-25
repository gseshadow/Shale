package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ContactAdministrationReadContractTest {
	@Test void methodExtractorBalancesBracesAndNormalizesLineEndings() {
		String source = "prefix\r\npublic void target() {\r if (true) { call(); }\n}\rsuffix";
		assertEquals("public void target() {\n if (true) { call(); }\n}",
				extractMethod(source, "public void target()"));
	}

    @Test void administrationReadIsBoundedTenantScopedAuthorizedAndParameterized() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactDao.java"));
        String method = extractMethod(source,
                "public List<AdministrationDefinitionRow> listDefinitionsForAdministration(");
        assertTrue(method.contains("d.ShaleClientId IS NULL OR d.ShaleClientId=?"));
        assertTrue(method.contains("verifyTenantMatchesSession(con, shaleClientId)"));
        assertTrue(method.contains("actor.setInt(1, actorUserId)"));
        assertTrue(method.contains("actor.setInt(2, shaleClientId)"));
        assertTrue(method.contains("ISNULL(is_admin,0)=1"));
        assertTrue(method.contains("ISNULL(is_deleted,0)=0"));
        assertTrue(method.contains("ISNULL(IsRemoved,0)=0"));
        assertTrue(method.contains("con.prepareStatement(sql)"));
        assertTrue(method.contains("ps.setInt(1, shaleClientId)"));
        assertTrue(method.contains("ORDER BY d.SortOrder,d.Name,d.Id"));
        assertTrue(method.contains("rs.getBytes(\"RowVer\")"));
        assertTrue(method.contains("executeQuery()"));
        assertFalse(method.contains("executeUpdate()"));
        assertFalse(Pattern.compile("\\b(?:INSERT|UPDATE|DELETE|MERGE)\\b").matcher(method).find());
        assertTrue(method.contains("String table = switch (category)"));
        assertTrue(method.contains("case CONTACT_TYPE -> \"ContactTypes\""));
        assertTrue(method.contains("case SPECIALTY -> \"Specialties\""));
        assertTrue(method.contains("case CREDENTIAL -> \"CredentialDefinitions\""));
        assertTrue(method.contains("String abbreviation = category == DefinitionCategory.CREDENTIAL"));
        assertFalse(method.contains("CaseParties"));
        assertFalse(method.contains("PartyRoles"));
        assertFalse(method.contains("CaseContacts"));
    }

    static String extractMethod(String source, String exactSignature) {
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        int signature = normalized.indexOf(exactSignature);
        assertTrue(signature >= 0, () -> "Method signature was not found: " + exactSignature);
        int openingBrace = normalized.indexOf('{', signature + exactSignature.length());
        assertTrue(openingBrace >= 0, () -> "Opening brace was not found for: " + exactSignature);
        int depth = 0;
        for (int index = openingBrace; index < normalized.length(); index++) {
            char character = normalized.charAt(index);
            if (character == '{') depth++;
            if (character == '}' && --depth == 0) return normalized.substring(signature, index + 1);
        }
        fail("Matching closing brace was not found for: " + exactSignature);
        return "";
    }
}
