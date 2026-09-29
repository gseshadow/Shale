package com.shale.core.service;

import java.time.Instant;
import java.util.UUID;
import com.shale.core.dto.ApplicationInstanceView;
import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;

/** Narrow authenticated lifecycle boundary. Tenant/user values come from trusted principal context. */
public interface ApplicationInstanceServicePort {
	ApplicationInstanceView enroll(int shaleClientId, int userId, UUID machineId, ClientType clientType,
			SemanticVersion applicationVersion);
	ApplicationInstanceView end(int shaleClientId, int userId, long applicationInstanceId);
	default ApplicationInstanceView heartbeat(int shaleClientId, int userId, long applicationInstanceId,
			SemanticVersion applicationVersion, Instant lastHumanActivityAt) {
		throw new UnsupportedOperationException("Application-instance heartbeat is not implemented by this adapter.");
	}
}
