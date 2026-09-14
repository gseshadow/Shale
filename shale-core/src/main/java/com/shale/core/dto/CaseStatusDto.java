package com.shale.core.dto;

public record CaseStatusDto(
		int id,
		String name,
		boolean closed,
		Integer sortOrder,
		String color,
		String lifecycleKey,
		String systemKey,
		Integer shaleClientId,
		boolean active,
		boolean deleted) {
}
