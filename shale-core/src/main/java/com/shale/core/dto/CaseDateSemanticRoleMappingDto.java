package com.shale.core.dto;

import java.util.Arrays;

/** Administration projection for one protected Case Date meaning. */
public record CaseDateSemanticRoleMappingDto(String roleKey, String roleName, int effectiveTypeId,
        String effectiveTypeName, boolean tenantOverride, Long tenantMappingId, byte[] tenantMappingRowVer) {
    public CaseDateSemanticRoleMappingDto { tenantMappingRowVer = copy(tenantMappingRowVer); }
    @Override public byte[] tenantMappingRowVer() { return copy(tenantMappingRowVer); }
    private static byte[] copy(byte[] value) { return value == null ? null : Arrays.copyOf(value, value.length); }
}
