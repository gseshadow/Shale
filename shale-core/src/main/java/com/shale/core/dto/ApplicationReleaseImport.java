package com.shale.core.dto;

import java.time.LocalDate;
import java.util.List;

import com.shale.core.model.ReleaseItemType;
import com.shale.core.model.SemanticVersion;

/** Validated, repository-authored content for one production release. */
public record ApplicationReleaseImport(SemanticVersion version, String title, LocalDate releaseDate,
		String summary, List<Item> items, byte[] expectedRowVersion) {
	public ApplicationReleaseImport {
		items = List.copyOf(items);
		expectedRowVersion = expectedRowVersion == null ? null : expectedRowVersion.clone();
	}
	@Override public byte[] expectedRowVersion() { return expectedRowVersion == null ? null : expectedRowVersion.clone(); }
	public record Item(int sortOrder, String group, ReleaseItemType type, String body) { }
}
