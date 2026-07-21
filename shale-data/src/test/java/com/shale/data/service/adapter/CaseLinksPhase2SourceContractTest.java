package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.LinkTypeDto;
import com.shale.data.dao.CaseDao;

class CaseLinksPhase2SourceContractTest {
	@Test
	void effectiveLinkTypeOverlayPrefersTenantAndSorts() {
		List<LinkTypeDto> rows = List.of(
				new LinkTypeDto(1, null, "Zoo", "#111", true, false, "shared", new byte[]{1}),
				new LinkTypeDto(2, 7, "Alpha", "#222", true, false, "shared", new byte[]{2}),
				new LinkTypeDto(3, 8, "Other", "#333", true, false, null, new byte[]{3}),
				new LinkTypeDto(4, 7, "Custom", "#444", true, false, null, new byte[]{4}),
				new LinkTypeDto(5, null, "Inactive", "#555", false, false, "inactive", new byte[]{5}),
				new LinkTypeDto(6, null, "Deleted", "#666", true, true, "deleted", new byte[]{6}));
		List<LinkTypeDto> effective = CaseServiceAdapter.resolveEffectiveLinkTypes(rows, 7, false);
		assertEquals(List.of("Alpha", "Custom"), effective.stream().map(LinkTypeDto::name).toList());
		assertEquals(2, effective.get(0).id());
	}

	@Test
	void urlValidationAllowsHttpAndHttpsAndPreservesMeaningfulParts() {
		assertEquals("https://Example.com/Some/Path?A=B#Frag", CaseServiceAdapter.validateUrl("  https://Example.com/Some/Path?A=B#Frag  "));
		assertEquals("http://example.com", CaseServiceAdapter.validateUrl("http://example.com"));
	}

	@Test
	void urlValidationRejectsUnsafeOrInvalidUrls() {
		for (String value : List.of("", "relative/path", "https:///missing", "file:///tmp/a", "javascript:alert(1)", "data:text/plain,a", "https://user:pass@example.com", "https://exa\nmple.com")) {
			assertThrows(IllegalArgumentException.class, () -> CaseServiceAdapter.validateUrl(value), value);
		}
		assertThrows(IllegalArgumentException.class, () -> CaseServiceAdapter.validateUrl("https://example.com/" + "a".repeat(2048)));
	}

	@Test
	void sqlConflictTranslationTargetsUniqueViolationsOnly() {
		SQLException duplicateSystemKey = new SQLException(
				"Violation of UNIQUE KEY UX_LinkTypes_ShaleClientId_SystemKey_NonNull", "23000", 2601);
		RuntimeException translated = CaseDao.translateSql("Failed to update link type", duplicateSystemKey);
		assertTrue(translated instanceof IllegalArgumentException);
		assertSame(duplicateSystemKey, translated.getCause());

		SQLException connectionFailure = new SQLException("connection failed", "08001", 0);
		RuntimeException preserved = CaseDao.translateSql("Failed to update link type", connectionFailure);
		assertFalse(preserved instanceof IllegalArgumentException);
		assertSame(connectionFailure, preserved.getCause());
	}

	@Test
	void caseDaoContainsRequiredTenantSafeSqlAndTransactions() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
		String reorderMethod = methodSource(source, "public List<CaseLinkDto> reorderCaseLinks");
		String updateLinkTypeMethod = methodSource(source, "public LinkTypeDto updateLinkType");
		assertAll(
				() -> assertTrue(source.contains("cl.ShaleClientId = ?")),
				() -> assertTrue(source.contains("el.ShaleClientId = cl.ShaleClientId")),
				() -> assertTrue(source.contains("lt.ShaleClientId IS NULL OR lt.ShaleClientId = cl.ShaleClientId")),
				() -> assertTrue(source.contains("cl.IsDeleted = 0")),
				() -> assertTrue(source.contains("el.IsDeleted = 0")),
				() -> assertTrue(source.contains("con.setAutoCommit(false)")),
				() -> assertTrue(source.contains("ORDER BY SortOrder, Id")),
				() -> assertTrue(source.contains("TOP (1)")),
				() -> assertTrue(source.contains("AND RowVer = ?")),
				() -> assertTrue(source.contains("if (ps.executeUpdate() != 1)")),
				() -> assertFalse(reorderMethod.contains("listCaseLinks(caseId, shaleClientId)")),
				() -> assertFalse(updateLinkTypeMethod.contains("return updateLinkType(")),
				() -> assertFalse(updateLinkTypeMethod.contains("return createLinkType(")),
				() -> assertFalse(source.contains("javafx")));
	}

	private static String methodSource(String source, String signature) {
		int start = source.indexOf(signature);
		assertTrue(start >= 0, signature);
		int nextPublic = source.indexOf("\n\tpublic ", start + signature.length());
		return nextPublic < 0 ? source.substring(start) : source.substring(start, nextPublic);
	}
}
