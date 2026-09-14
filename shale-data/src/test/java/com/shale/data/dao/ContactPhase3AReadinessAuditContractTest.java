package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Text-only contract: these tests never connect to or execute against a database. */
class ContactPhase3AReadinessAuditContractTest {
    private static final Path AUDIT = Path.of("../docs/sql/verification/2026-08-28_contacts_phase3a_legacy_retirement_readiness.sql");
    private static final Path WRAPPER = Path.of("../docs/sql/verification/2026-08-28_contacts_phase3a_sqlcmd_wrapper.sql");
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

    @Test void sqlcmdWrapperEstablishesContextAndIncludesAuditOnOneConnection() throws Exception {
        String w=Files.readString(WRAPPER);
        int tenant=w.indexOf("@key=N'ShaleClientId'");
        int principal=w.indexOf("@key=N'PrincipalUserId'");
        int verify=w.indexOf("SELECT DB_NAME() AS DatabaseName");
        int include=w.indexOf(":r docs/sql/verification/2026-08-28_contacts_phase3a_legacy_retirement_readiness.sql");
        assertTrue(tenant>=0 && principal>tenant && verify>principal && include>verify);
        assertTrue(w.contains(":On Error exit"));
        assertEquals(7,Pattern.compile("@read_only=1").matcher(w).results().count());
        for(String variable:List.of("ExpectedDatabaseName","TenantId","AdministratorUserId","OperatorAcknowledgement","ApplicationBoundaryAcknowledgement","MismatchIdCap")) assertTrue(w.contains("$("+variable+")"),variable);
        String u=stripCommentsAndStrings(w).toUpperCase(Locale.ROOT);
        for(String mutation:List.of("INSERT ","UPDATE ","DELETE ","MERGE ","ALTER ","DROP ","TRUNCATE ","CREATE ")) assertFalse(u.contains(mutation),mutation);
        assertFalse(w.contains("DisplayNumber")); assertFalse(w.contains("EmailAddress")); assertFalse(w.contains("LegacyAddressText"));
    }

    @Test void rlsMatcherAcceptsAzureFormattingButRejectsAnyContractDrift() throws Exception {
        String audit=sql();
        assertTrue(audit.contains("N'sec.fn_filterbytenantshaleclientid'"));
        assertTrue(audit.contains("N'(',N''),N')',N''"));
        assertTrue(audit.contains("IIF(x.n=1 AND x.bad=0,1,0)"));
        assertTrue(audit.contains("sp.is_enabled=1"));
        assertTrue(audit.contains("spr.predicate_type_desc=N'FILTER'"));
        assertTrue(audit.contains("spr.operation_desc IS NULL"));

        PredicateMetadata azure=new PredicateMetadata(true,"FILTER",null,
            "([sec].[fn_FilterByTenant]([ShaleClientId]))");
        assertTrue(hasExactlyOneStrictTenantFilter(List.of(azure)));
        assertFalse(hasExactlyOneStrictTenantFilter(List.of(new PredicateMetadata(true,"FILTER",null,
            "([sec].[fn_FilterByTenantOrGlobal]([ShaleClientId]))"))));
        assertFalse(hasExactlyOneStrictTenantFilter(List.of(new PredicateMetadata(true,"FILTER",null,
            "([dbo].[fn_FilterByTenant]([ShaleClientId]))"))));
        assertFalse(hasExactlyOneStrictTenantFilter(List.of(new PredicateMetadata(true,"FILTER",null,
            "([sec].[fn_FilterByTenant]([TenantId]))"))));
        assertFalse(hasExactlyOneStrictTenantFilter(List.of(new PredicateMetadata(false,"FILTER",null,
            "([sec].[fn_FilterByTenant]([ShaleClientId]))"))));
        assertFalse(hasExactlyOneStrictTenantFilter(List.of(new PredicateMetadata(true,"BLOCK","INSERT",
            "([sec].[fn_FilterByTenant]([ShaleClientId]))"))));
        assertFalse(hasExactlyOneStrictTenantFilter(List.of()));
        assertFalse(hasExactlyOneStrictTenantFilter(List.of(azure,azure)));
    }

    @Test void phoneNormalizedIntegrityAcceptsDigitsWithOptionalSingleLeadingPlus() throws Exception {
        String audit=sql();
        assertTrue(audit.contains("NormalizedNumber=N''"));
        assertTrue(audit.contains("LEFT(NormalizedNumber,1)=N'+'"));
        assertTrue(audit.contains("DATALENGTH(NormalizedNumber)<=2"));
        assertTrue(audit.contains("SUBSTRING(NormalizedNumber,2,4000) COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9]%'"));
        assertTrue(audit.contains("LEFT(NormalizedNumber,1)<>N'+' AND NormalizedNumber COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9]%'"));
        assertTrue(isValidNormalizedPhone(null)); // Phase 2C-A intentionally permits NULL.
        for(String valid:List.of("0","5051234567","+1","+15051234567"))
            assertTrue(isValidNormalizedPhone(valid),valid);
        for(String invalid:List.of("","+","++1","1+2"," 5051234567","505 123 4567",
            "505-123-4567","(505)1234567","505.123.4567","phone","１２３"))
            assertFalse(isValidNormalizedPhone(invalid),invalid);
    }

    private record PredicateMetadata(boolean policyEnabled,String predicateType,String operation,String definition) {}

    private static boolean hasExactlyOneStrictTenantFilter(List<PredicateMetadata> predicates) {
        return predicates.size()==1 && predicates.stream().allMatch(p -> p.policyEnabled()
            && p.predicateType().equals("FILTER") && p.operation()==null
            && normalizePredicate(p.definition()).equals("sec.fn_filterbytenantshaleclientid"));
    }

    private static String normalizePredicate(String value) {
        return Objects.requireNonNull(value).replaceAll("[\\[\\]\\s()]","").toLowerCase(Locale.ROOT);
    }

    private static boolean isValidNormalizedPhone(String value) {
        return value==null || value.matches("\\+?[0-9]+");
    }

    private static String stripCommentsAndStrings(String source) {
        String noComments=Pattern.compile("(?s)/\\*.*?\\*/|--[^\\r\\n]*").matcher(source).replaceAll(" ");
        return Pattern.compile("N?'(?:''|[^'])*'").matcher(noComments).replaceAll("''");
    }
}
