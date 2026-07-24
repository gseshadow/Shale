package com.shale.core.service;

import com.shale.core.dto.*;
import java.time.*;
import java.util.List;
import java.util.Optional;

public interface MaterialRequestServicePort {
    List<MaterialTypeDto> listEffectiveMaterialTypes(int shaleClientId);
    List<RequestMethodDto> listEffectiveRequestMethods(int shaleClientId);
    List<RequestStatusDto> listEffectiveRequestStatuses(int shaleClientId);
    List<MaterialRequestSummaryDto> listMaterialRequests(long caseId, int shaleClientId);
    Optional<MaterialRequestDetailDto> getMaterialRequest(long caseId, long materialRequestId, int shaleClientId, int actorUserId);
    List<MaterialRequestFollowUpDto> listFollowUps(long caseId, long materialRequestId, int shaleClientId, int actorUserId);
    MaterialRequestDetailDto createMaterialRequest(CreateMaterialRequestCommand command);

    record CreateMaterialRequestCommand(int shaleClientId, int actorUserId, long caseId, int materialTypeId,
                                        String title, String description, Integer requestedFromContactId,
                                        Integer requestedFromOrganizationId, String requestedFromText,
                                        String requestMethod, String status, int requestedByUserId,
                                        Integer assignedToUserId, LocalDateTime requestedAt,
                                        LocalDate expectedResponseDate, LocalDateTime nextFollowUpAt) {}
}
