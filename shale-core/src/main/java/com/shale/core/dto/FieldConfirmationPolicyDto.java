package com.shale.core.dto;

import java.util.Arrays;

/** Immutable current policy version for a stable configured-field identity. */
public record FieldConfirmationPolicyDto(long id, int shaleClientId, String formKey, String fieldKey,
        long policyRevision, boolean requiresConfirmation, Integer requiredFirmWideRoleDefinitionId,
        byte[] rowVer) {
    public FieldConfirmationPolicyDto { rowVer = rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length); }
    @Override public byte[] rowVer() { return rowVer == null ? null : Arrays.copyOf(rowVer, rowVer.length); }
}
