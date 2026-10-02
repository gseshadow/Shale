package com.shale.server.live;

import java.util.UUID;
import com.shale.core.model.ReleaseChannel;

/** Best-effort transport for PHI-free hints. Database/API state remains authoritative. */
public interface InvalidationPublisher {
	void sessionInvalidated(int shaleClientId, UUID sessionId);
	void applicationPolicyChanged(ReleaseChannel channel);

	static InvalidationPublisher disabled() {
		return new InvalidationPublisher() {
			@Override public void sessionInvalidated(int tenant, UUID sessionId) { }
			@Override public void applicationPolicyChanged(ReleaseChannel channel) { }
		};
	}
}
