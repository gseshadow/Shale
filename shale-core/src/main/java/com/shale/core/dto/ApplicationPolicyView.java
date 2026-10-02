package com.shale.core.dto;
import java.time.Instant;
import com.shale.core.model.*;
public record ApplicationPolicyView(long id, ReleaseChannel channel, long revisionNumber,
		ReleaseReference latest, ReleaseReference minimumRecommended, ReleaseReference minimumAllowed,
		Instant requiredUpdateDeadline, ApplicationAccessMode accessMode, Instant publishedAt, Instant serverTime, byte[] rowVersion) {
	public ApplicationPolicyView { rowVersion = rowVersion == null ? null : rowVersion.clone(); }
	@Override public byte[] rowVersion() { return rowVersion == null ? null : rowVersion.clone(); }
	public record ReleaseReference(long releaseId, SemanticVersion version) {}
}
