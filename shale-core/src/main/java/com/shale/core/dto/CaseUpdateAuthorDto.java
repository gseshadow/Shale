package com.shale.core.dto;

/** Author identity/presentation only; never loads note history for a picker. */
public record CaseUpdateAuthorDto(int id, String displayName, String color) { }
