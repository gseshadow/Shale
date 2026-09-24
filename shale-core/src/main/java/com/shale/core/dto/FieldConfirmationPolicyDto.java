package com.shale.core.dto;

import java.util.Arrays;

/** Immutable current policy version for a tenant-effective Case Date Type identity. */
public record FieldConfirmationPolicyDto(long id, int shaleClientId, String caseDateTypePolicyKey,
        long policyRevision, boolean requiresConfirmation, Integer requiredFirmWideRoleDefinitionId,
        byte[] rowVer) {
    public FieldConfirmationPolicyDto { rowVer = rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length); }
    @Override public byte[] rowVer() { return rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length); }
}
