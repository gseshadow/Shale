package com.shale.ui.whatsnew;

import java.util.List;
import java.util.Objects;
import com.shale.core.model.ReleaseItemType;
import com.shale.core.model.SemanticVersion;

/** Immutable, UI-specific model for one aggregated What's New experience. */
public record WhatsNewPresentation(SemanticVersion runningVersion, SemanticVersion targetVersion,
		long targetReleaseId, List<ReleaseSection> releases) {
	public WhatsNewPresentation {
		Objects.requireNonNull(runningVersion, "runningVersion");
		Objects.requireNonNull(targetVersion, "targetVersion");
		if (targetReleaseId <= 0) throw new IllegalArgumentException("targetReleaseId must be positive");
		releases = List.copyOf(Objects.requireNonNull(releases, "releases"));
		if (releases.isEmpty()) throw new IllegalArgumentException("releases must not be empty");
	}

	public record ReleaseSection(long releaseId, SemanticVersion version, String summary, List<Item> items) {
		public ReleaseSection {
			if (releaseId <= 0) throw new IllegalArgumentException("releaseId must be positive");
			Objects.requireNonNull(version, "version");
			summary = normalize(summary);
			items = List.copyOf(Objects.requireNonNull(items, "items"));
		}
	}

	public record Item(long id, ReleaseItemType type, String title, String body, String resourceUrl) {
		public Item {
			if (id <= 0) throw new IllegalArgumentException("id must be positive");
			Objects.requireNonNull(type, "type");
			title = normalize(title);
			body = normalize(body);
			resourceUrl = normalize(resourceUrl);
		}
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
