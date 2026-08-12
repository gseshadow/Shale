package com.shale.core.dto;

import java.util.Arrays;

public record EffectiveCaseDateTypeDto(
        int id,
        Integer shaleClientId,
        String systemKey,
        String name,
        String description,
        String calendarCategory,
        String color,
        boolean supportsTime,
        int sortOrder,
        boolean active,
        boolean deleted,
        Origin origin,
        byte[] rowVer) {
    public enum Origin { GLOBAL, TENANT_CREATED, TENANT_OVERRIDE }

    public EffectiveCaseDateTypeDto {
        rowVer = rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length);
    }

    @Override public byte[] rowVer() { return rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length); }
}
