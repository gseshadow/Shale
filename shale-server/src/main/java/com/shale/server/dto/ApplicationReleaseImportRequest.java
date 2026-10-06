package com.shale.server.dto;

import java.util.List;
import java.util.Map;

public record ApplicationReleaseImportRequest(String version, String title, String releaseDate, String summary,
		Map<String,List<String>> groups, Boolean allowUpdate, String expectedRowVersion) { }
