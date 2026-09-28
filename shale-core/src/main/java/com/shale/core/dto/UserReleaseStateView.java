package com.shale.core.dto;

import java.time.Instant;
import com.shale.core.model.*;

public record UserReleaseStateView(long id, int shaleClientId, int userId, ClientType clientType,
		ReleaseChannel releaseChannel, long releaseId, SemanticVersion version, Instant acknowledgedAt,
		byte[] rowVersion) {
	public UserReleaseStateView { rowVersion = rowVersion == null ? null : rowVersion.clone(); }
	@Override public byte[] rowVersion() { return rowVersion == null ? null : rowVersion.clone(); }
}
