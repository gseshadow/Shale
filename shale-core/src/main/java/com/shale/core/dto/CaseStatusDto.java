package com.shale.core.dto;

public record CaseStatusDto(
		int id,
		String name,
		String description,
		boolean active,
		Integer sortOrder,
		String color,
		String lifecycleKey,
		String systemKey,
		Integer shaleClientId) {
}
