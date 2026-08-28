package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Text-only contract: these tests never connect to or execute against a database. */
class ContactPhase3AReadinessAuditContractTest {
    private static final Path AUDIT = Path.of("../docs/sql/verification/2026-08-28_contacts_phase3a_legacy_retirement_readiness.sql");
    private static final List<String> LEGACY = List.of("PhoneCell","PhoneHome","PhoneWork","EmailPersonal","EmailWork","EmailOther","AddressHome","AddressWork","AddressOther","IsExpert");
    private static String sql() throws Exception { return Files.readString(AUDIT); }

    @Test void isReadOnlyAndExplicitlyGated() throws Exception {
        String s=sql(), u=stripCommentsAndStrings(s).toUpperCase(Locale.ROOT);
        for(String verb:List.of("INSERT INTO","UPDATE ","DELETE ","MERGE ","ALTER ","DROP ","TRUNCATE ","CREATE "))
            assertFalse(u.contains(verb),verb);
        assertTrue(s.contains("@OperatorAcknowledgedReadOnlyAudit bit=0"));
        assertTrue(s.contains("@ApplicationDependencyBoundaryPassed bit=0"));
        assertFalse(s.substring(s.indexOf("SET NOCOUNT ON")).toUpperCase(Locale.ROOT).contains("SP_SET_SESSION_CONTEXT"));
        assertTrue(s.contains("IF @Ready<>1 THROW"));
    }

    @Test void environmentSchemaAndSessionFailClosed() throws Exception {
        String s=sql();
        assertAll(()->assertTrue(s.contains("DB_NAME()=@ExpectedDatabase")),
          ()->assertTrue(s.contains("@ExpectedShaleClientId>0")),
          ()->assertTrue(s.contains("SESSION_CONTEXT(N'ShaleClientId')")),
          ()->assertTrue(s.contains("SESSION_CONTEXT(N'PrincipalUserId')")),
          ()->assertTrue(s.contains("@LegacyPresent IN(0,10)")),
          ()->assertTrue(s.contains("partial retirement fails closed")),
          ()->assertTrue(s.contains("sys.security_predicates")),
          ()->assertTrue(s.contains("sec.fn_filterbytenant")));
    }

    @Test void coversInventoryStructuredTablesAndParity() throws Exception {
        String s=sql();
        LEGACY.forEach(c->assertTrue(s.contains("N'"+c+"'"),c));
        for(String t:List.of("ContactPhoneNumbers","ContactEmailAddresses","ContactAddresses","ContactContactTypes","ContactTypes","ContactSpecialties","ContactCredentials","CredentialDefinitions")) assertTrue(s.contains(t),t);
        for(String category:List.of("PHONE_PRESERVATION_MISSING","PHONE_LIVE_MISSING","EMAIL_PRESERVATION_MISSING","EMAIL_LIVE_MISSING","ADDRESS_PRESERVATION_MISSING","ADDRESS_LIVE_MISSING","EXPERT_LEGACY_TRUE_ASSIGNMENT_MISSING","EXPERT_ASSIGNMENT_PRESENT_LEGACY_FALSE")) assertTrue(s.contains(category),category);
        assertFalse(s.contains("N'DisplayName'"));
    }

    @Test void integrityDependenciesAndZeroFindingPassAreContracted() throws Exception {
        String s=sql();
        for(String token:List.of("TENANT_OR_ORPHAN","DUPLICATE_ACTIVE_PRIMARY","BLANK_DISPLAY","BLANK_PRESENTATION","INVALID_NORMALIZED","INVALID_SORT_ORDER","DUPLICATE_ACTIVE_ASSIGNMENT","EXPERT_INVALID_DEFINITION","ACTIVE_LEGACY_WITHOUT_USABLE_STRUCTURED_POINT")) assertTrue(s.contains(token),token);
        for(String token:List.of("sys.sql_expression_dependencies","sys.sql_modules","sys.computed_columns","sys.check_constraints","sys.default_constraints","sys.indexes","sys.foreign_key_columns","sys.security_predicates","SQL_EXPRESSION","MODULE_TEXT","COMPUTED_COLUMN","CHECK_CONSTRAINT","DEFAULT_CONSTRAINT","INDEX_KEY_INCLUDE_OR_FILTER","FOREIGN_KEY","SECURITY_PREDICATE")) assertTrue(s.contains(token),token);
        assertTrue(s.contains("NOT EXISTS(SELECT 1 FROM @Findings WHERE Blocking=1)"));
        assertTrue(s.contains("NOT EXISTS(SELECT 1 FROM @Dependencies WHERE Allowed=0)"));
        assertTrue(s.contains("PASS_READY_FOR_PHASE_3B")); assertTrue(s.contains("FAIL_NOT_READY"));
    }

    @Test void resultProjectionsArePhiSafe() throws Exception {
        String s=sql();
        String resultTail=s.substring(s.indexOf("/* Deterministic result contract"));
        for(String phi:List.of("DisplayNumber","NormalizedNumber","EmailAddress","NormalizedEmail","LegacyAddressText","DisplayName","RowVer")) assertFalse(resultTail.contains(phi),phi);
        assertTrue(resultTail.contains("CategoryCode,ContactId"));
    }

    private static String stripCommentsAndStrings(String source) {
        String noComments=Pattern.compile("(?s)/\\*.*?\\*/|--[^\\r\\n]*").matcher(source).replaceAll(" ");
        return Pattern.compile("N?'(?:''|[^'])*'").matcher(noComments).replaceAll("''");
    }
}
