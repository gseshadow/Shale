package com.shale.core.dto;

public record MaterialTypeDto(int id, Integer shaleClientId, String systemKey, String name, String description, String color, int sortOrder, boolean active, boolean deleted, byte[] rowVer) {
    public MaterialTypeDto(int id, Integer shaleClientId, String systemKey, String name, String description, String color, int sortOrder) { this(id, shaleClientId, systemKey, name, description, color, sortOrder, true, false, null); }
}
