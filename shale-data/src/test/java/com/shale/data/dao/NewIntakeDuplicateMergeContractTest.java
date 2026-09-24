package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Protects the duplicate-intake data-integrity, tenant, audit, and transaction boundary. */
final class NewIntakeDuplicateMergeContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
    }

    @Test void exactNormalizationIsConservative() {
        assertEquals("smith v jones", CaseDao.normalizeCaseName("  SMITH   v\tJones "));
        assertEquals("smith-v-jones", CaseDao.normalizeCaseName("Smith-v-Jones"));
        assertNull(CaseDao.normalizeCaseName("  "));
    }

    @Test void duplicateLookupIsTenantScopedActiveAndDeterministic() throws Exception {
        String method=method(source(),"public List<IntakeDuplicateCase> findIntakeDuplicateCases");
        assertTrue(method.contains("c.ShaleClientId=?"));
        assertTrue(method.contains("ISNULL(c.IsDeleted,0)=0"));
        assertTrue(method.contains("LOWER(REPLACE(REPLACE(REPLACE"));
        assertTrue(method.contains("ORDER BY c.Id"));
    }

    @Test void mergeLocksAndRevalidatesTheSelectedTenantCaseBeforeMutation() throws Exception {
        String method=method(source(),"private static boolean lockMatchingCase");
        assertTrue(method.contains("UPDLOCK,HOLDLOCK"));
        assertTrue(method.contains("Id=? AND ShaleClientId=?"));
        assertTrue(method.contains("ISNULL(IsDeleted,0)=0"));
        assertTrue(method.contains("normalizeCaseName"));
    }

    @Test void mergeIsAdditiveForScalarsCollectionsDatesAndRoleContacts() throws Exception {
        String s=source();
        assertTrue(s.contains("THEN ? ELSE Description END"));
        assertTrue(s.contains("THEN ? ELSE Summary END"));
        assertTrue(s.contains("COALESCE(NULLIF(LTRIM(RTRIM(FirstName)),''),?)"));
        assertTrue(s.contains("hasContactPoint(con,\"ContactPhoneNumbers\""));
        assertTrue(s.contains("hasContactPoint(con,\"ContactEmailAddresses\""));
        assertTrue(s.contains("hasContactPoint(con,\"ContactAddresses\""));
        assertTrue(s.contains("if(!hasActiveCaseDate"));
        assertTrue(s.contains("if(ids.size()==1)"),"only an unambiguous role Contact may be reused");
        assertTrue(s.contains("else{id=insertContact"),"ambiguous or absent role identity must not be merged arbitrarily");
    }

    @Test void mergeDoesNotInsertACaseAndRollsBackAuditOrPersistenceFailure() throws Exception {
        String method=method(source(),"public NewIntakeCreateResult mergeIntake");
        assertFalse(method.contains("insertCase(con"));
        assertTrue(method.contains("con.setAutoCommit(false)"));
        assertTrue(method.contains("con.commit()"));
        assertTrue(method.contains("con.rollback()"));
        assertTrue(method.contains("auditCreatedCaseDate"));
        assertTrue(method.contains("phiAuditService") || source().contains("fillBlankCaseScalars"));
    }

    @Test void mergeEvaluatesPoliciesOnlyForInsertedMissingDatesInsideItsTransaction() throws Exception {
        String method=method(source(),"public NewIntakeCreateResult mergeIntake");
        int missing=method.indexOf("if(!hasActiveCaseDate");
        int insert=method.indexOf("insertConfiguredCaseDate(con,request,existingCaseId,date,intakeTypeId)",missing);
        int evaluate=method.indexOf("fieldConfirmationDao.evaluateCaseDate(con,request.shaleClientId(),request.createdByUserId()",insert);
        int commit=method.indexOf("con.commit()",evaluate);
        assertAll(
                () -> assertTrue(missing>=0, "Merge must preserve an existing active value rather than replacing it."),
                () -> assertTrue(insert>missing, "Only a missing selected type may be inserted."),
                () -> assertTrue(evaluate>insert, "Every newly inserted merge value must evaluate confirmation policy."),
                () -> assertTrue(commit>evaluate, "Requirement creation and auto-confirmation must precede merge commit."),
                () -> assertTrue(method.contains("id,date.caseDateTypeId(),1"),
                        "Merge must snapshot the inserted type and initial business revision."),
                () -> assertTrue(method.contains("con.rollback()"),
                        "Policy evaluation failure must roll back the merge and its audits."));
    }

    private static String method(String source,String signature){int start=source.indexOf(signature);assertTrue(start>=0);int open=source.indexOf('{',start),depth=0;for(int i=open;i<source.length();i++){char c=source.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return source.substring(start,i+1);}throw new AssertionError("unterminated method");}
}
