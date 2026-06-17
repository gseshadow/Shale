package com.shale.core.dto;

public record PracticeAreaDto(
		int id,
		String name,
		String color,
		boolean active,
		boolean deleted,
		String systemKey,
		Integer shaleClientId) {
}
