package com.shale.core.dto;

import java.time.Instant;

/** Tenant-effective Case Team role definition; identity is the durable row id/system key, never the display name. */
public record CaseTeamRoleDefinitionDto(int id, Integer shaleClientId, String systemKey, Integer legacyRoleId,
        String name, String description, String color, int sortOrder, boolean active, boolean deleted,
        boolean protectedSystemRole, boolean tenantOverride, Instant createdAt, Integer createdByUserId,
        Instant updatedAt, Integer updatedByUserId, Instant deletedAt, Integer deletedByUserId, byte[] rowVer) {
    public CaseTeamRoleDefinitionDto { rowVer = rowVer == null ? null : rowVer.clone(); }
    @Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
    public boolean systemProvided() { return systemKey != null; }
    public boolean custom() { return systemKey == null; }
}
