package com.shale.core.service;

import java.util.Optional;
import com.shale.core.dto.UserReleaseStateView;
import com.shale.core.model.*;

/** Current-actor release announcement state; not update eligibility or telemetry. */
public interface UserReleaseStateServicePort {
	Optional<UserReleaseStateView> findCurrent(int tenantId, int currentUserId, ClientType clientType, ReleaseChannel channel);
	UserReleaseStateView acknowledge(int tenantId, int currentUserId, ClientType clientType, ReleaseChannel channel,
			long targetReleaseId, byte[] expectedRowVersion);
}
