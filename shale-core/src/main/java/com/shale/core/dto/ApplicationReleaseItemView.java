package com.shale.core.dto;
import com.shale.core.model.ReleaseItemType;
public record ApplicationReleaseItemView(long id, long releaseId, int sortOrder, ReleaseItemType itemType,
		String title, String body, String resourceUrl, boolean active) {}
