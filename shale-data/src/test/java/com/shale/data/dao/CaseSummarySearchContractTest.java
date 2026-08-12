package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class CaseSummarySearchContractTest {
	private static String source() throws Exception {
		return Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseSummaryDao.java"));
	}

	@Test void wildcardCharactersAreLiteral() {
		assertEquals("a[%]b[_]c[[]d", CaseSummaryDao.escapeLike("a%b_c[d"));
	}

	@Test void searchUsesTheAuthoritativeTenantActiveAndOneRowBoundary() throws Exception {
		String all = source();
		String method = all.substring(all.indexOf("public List<SearchCaseRow> searchActiveByName"),
				all.indexOf("/** Main active Cases grid"));
		assertTrue(method.contains("verifyTenant(con, requestedTenantId)"));
		assertTrue(method.contains("c.ShaleClientId=? AND ISNULL(c.IsDeleted,0)=0"));
		assertTrue(method.contains("LOWER(COALESCE(c.Name,'')) LIKE ?"));
		assertTrue(method.contains("ps.setString(4"));
		assertTrue(method.contains("ORDER BY c.Name ASC,c.Id ASC"));
		assertTrue(method.contains("RoleSemantics.ROLE_RESPONSIBLE_ATTORNEY"));
		assertTrue(method.contains("RoleSemantics.ROLE_LEGAL_ASSISTANT"));
		assertFalse(method.matches("(?s).*RoleId\\s*=\\s*(4|11).*"));
		assertFalse(method.contains("c.CallerDate"));
		assertFalse(method.contains("c.StatuteOfLimitations"));
		assertFalse(method.contains("c.DateOfInjury"));
		assertFalse(method.contains("c.TortNoticeDeadline"));
		assertFalse(method.contains("CaseParties"));
		assertFalse(method.contains("CaseUpdates"));
	}
}
