package com.shale.data.service.adapter;

import java.util.Objects;
import com.shale.core.dto.*;
import com.shale.core.service.ApplicationReleaseImportServicePort;
import com.shale.data.dao.ApplicationReleaseImportDao;

public final class ApplicationReleaseImportServiceAdapter implements ApplicationReleaseImportServicePort {
	private final ApplicationReleaseImportDao dao;
	public ApplicationReleaseImportServiceAdapter(ApplicationReleaseImportDao dao) { this.dao = Objects.requireNonNull(dao); }
	@Override public ApplicationReleaseImportResult importProductionRelease(ApplicationReleaseImport release,
			boolean allowUpdate, String operatorId) { return dao.importProductionRelease(release, allowUpdate, operatorId); }
}
