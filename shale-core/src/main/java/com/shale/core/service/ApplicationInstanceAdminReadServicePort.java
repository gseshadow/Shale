package com.shale.core.service;

import java.time.Instant;
import java.util.List;
import com.shale.core.dto.ApplicationInstanceAdminPage;
import com.shale.core.dto.ApplicationVersionDistributionView;
import com.shale.core.model.ClientType;
import com.shale.core.model.SemanticVersion;

/** Read-only, tenant-administrator boundary for recent application-instance telemetry. */
public interface ApplicationInstanceAdminReadServicePort {
	record Filter(ClientType clientType, SemanticVersion applicationVersion, Integer userId,
			boolean activeOnly, Instant startedSince) { }
	ApplicationInstanceAdminPage listRecent(int shaleClientId, int actorUserId, Filter filter, int page, int size);
	List<ApplicationVersionDistributionView> getVersionDistribution(int shaleClientId, int actorUserId,
			Instant startedSince);
}
