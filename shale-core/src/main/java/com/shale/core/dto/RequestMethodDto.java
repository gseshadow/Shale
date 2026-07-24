package com.shale.core.dto;

public record RequestMethodDto(int id, Integer shaleClientId, String systemKey, String name, String color, int sortOrder, boolean active, boolean deleted, byte[] rowVer) {
    public RequestMethodDto(int id, Integer shaleClientId, String systemKey, String name, int sortOrder, boolean active, boolean deleted) { this(id, shaleClientId, systemKey, name, null, sortOrder, active, deleted, null); }
}
