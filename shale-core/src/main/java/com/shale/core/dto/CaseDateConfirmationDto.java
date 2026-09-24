package com.shale.core.dto;

import java.time.LocalDateTime;
import java.util.Objects;

/** Read model for a Case Date and the workflow attached to its current business-value revision. */
public record CaseDateConfirmationDto(
        CaseDateDto caseDate,
        long businessValueRevision,
        Status status,
        Long confirmationRequirementId,
        Long fieldConfirmationPolicyId,
        Long policyRevisionSnapshot,
        Integer requiredFirmWideRoleDefinitionId,
        Integer confirmedByUserId,
        String confirmedByDisplayName,
        LocalDateTime confirmedAt) {
    public enum Status { NOT_REQUIRED, PENDING, CONFIRMED }

    public CaseDateConfirmationDto {
        Objects.requireNonNull(caseDate, "caseDate");
        Objects.requireNonNull(status, "status");
        if (businessValueRevision <= 0) throw new IllegalArgumentException("businessValueRevision must be positive");
        if (status == Status.NOT_REQUIRED && confirmationRequirementId != null)
            throw new IllegalArgumentException("NOT_REQUIRED cannot identify a confirmation requirement");
        if (status != Status.NOT_REQUIRED && (confirmationRequirementId == null || fieldConfirmationPolicyId == null
                || policyRevisionSnapshot == null || requiredFirmWideRoleDefinitionId == null))
            throw new IllegalArgumentException("Workflow states require their policy and role snapshot");
        if (status == Status.CONFIRMED && (confirmedByUserId == null || confirmedAt == null))
            throw new IllegalArgumentException("CONFIRMED requires confirmation facts");
        if (status != Status.CONFIRMED && (confirmedByUserId != null || confirmedAt != null))
            throw new IllegalArgumentException("Only CONFIRMED may expose confirmation facts");
    }
}
