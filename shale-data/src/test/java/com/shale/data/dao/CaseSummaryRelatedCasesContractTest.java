package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CaseSummaryRelatedCasesContractTest {
	private static String method() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
		int start=source.indexOf("private List<RelatedCaseRow> listActiveRelated("),open=source.indexOf('{',start),depth=0;
		assertTrue(start>=0&&open>=0,"missing related-Cases query method");
		for(int i=open;i<source.length();i++){char ch=source.charAt(i);if(ch=='{')depth++;else if(ch=='}'&&--depth==0)return source.substring(start,i+1);}
		throw new AssertionError("unbalanced related-Cases query method");
	}

	@Test void relationshipAndBothEntitiesAreTenantAndActiveConstrained() throws Exception {
		String sql = method();
		assertTrue(sql.contains("verifyTenant(con, tenantId)"));
		assertTrue(sql.contains("c.ShaleClientId=?"));
		assertTrue(sql.contains("entity.ShaleClientId=c.ShaleClientId"));
		assertTrue(sql.contains("ISNULL(entity.IsDeleted,0)=0"));
		assertTrue(sql.contains("ISNULL(c.IsDeleted,0)=0"));
	}

	@Test void oneRowPerRelationshipUsesAuthoritativeIdsAndDeterministicOrder() throws Exception {
		String sql = method();
		assertTrue(sql.contains("cp.Id RelationshipId,cp.PartyRoleId"));
		assertTrue(sql.contains("FROM dbo.CaseParties cp"));
		assertTrue(sql.contains("c.Name ASC,c.Id ASC,cp.Id ASC"));
		assertTrue(sql.contains("mapGridSummary(rs)"));
		assertFalse(sql.contains("DISTINCT"));
	}

	@Test void sharedStatusAssignmentsAndSemanticDatesAreSetBased() throws Exception {
		String sql = method();
		assertTrue(sql.contains("statusApplySql()"));
		assertTrue(sql.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY"));
		assertTrue(sql.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT"));
		assertTrue(sql.contains("CaseDateTypeSemanticRoleMappings"));
		assertTrue(sql.contains("LEFT JOIN dbo.PracticeAreas"));
		assertTrue(sql.contains("OUTER APPLY"));
		assertFalse(sql.matches("(?s).*RoleId\\s*=\\s*(4|11).*"));
		assertFalse(sql.contains("c.CallerDate"));
		assertFalse(sql.contains("c.StatuteOfLimitations"));
		assertFalse(sql.contains("c.TortNoticeDeadline"));
		assertTrue(sql.lines().filter(line -> line.contains("executeQuery()")).count() == 1,
				"Only one set query is allowed after tenant verification");
	}
}
