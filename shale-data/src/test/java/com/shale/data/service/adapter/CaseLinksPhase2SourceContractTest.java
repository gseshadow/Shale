package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.LinkTypeDto;

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
	void caseDaoContainsRequiredTenantSafeSqlAndTransactions() throws IOException {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/CaseDao.java"));
		assertAll(
				() -> assertTrue(source.contains("cl.ShaleClientId=?")),
				() -> assertTrue(source.contains("el.ShaleClientId=cl.ShaleClientId")),
				() -> assertTrue(source.contains("(lt.ShaleClientId IS NULL OR lt.ShaleClientId=cl.ShaleClientId)")),
				() -> assertTrue(source.contains("cl.IsDeleted=0 AND el.IsDeleted=0")),
				() -> assertTrue(source.contains("con.setAutoCommit(false)")),
				() -> assertTrue(source.contains("ORDER BY SortOrder, Id")),
				() -> assertFalse(source.contains("javafx")));
	}
}
