package com.shale.core.dto;
import java.time.Instant;
import com.shale.core.model.*;
public record ApplicationReleaseView(long id, SemanticVersion version, ReleaseChannel channel,
		PublicationStatus publicationStatus, Instant publishedAt, String summary, byte[] rowVersion) {
	public ApplicationReleaseView { rowVersion = rowVersion == null ? null : rowVersion.clone(); }
	@Override public byte[] rowVersion() { return rowVersion == null ? null : rowVersion.clone(); }
}
