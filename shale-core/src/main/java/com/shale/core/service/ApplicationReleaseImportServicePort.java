package com.shale.core.service;

import com.shale.core.dto.ApplicationReleaseImport;
import com.shale.core.dto.ApplicationReleaseImportResult;

public interface ApplicationReleaseImportServicePort {
	ApplicationReleaseImportResult importProductionRelease(ApplicationReleaseImport release, boolean allowUpdate,
			String operatorId);
}
