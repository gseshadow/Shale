package com.shale.core.dto;

public record RequestMethodDto(int id, Integer shaleClientId, String systemKey, String name, int sortOrder, boolean active, boolean deleted) {}
