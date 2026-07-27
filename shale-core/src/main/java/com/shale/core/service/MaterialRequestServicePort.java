package com.shale.core.service;

import com.shale.core.dto.*;
import java.time.*;
import java.util.List;
import java.util.Optional;

public interface MaterialRequestServicePort {
    List<MaterialTypeDto> listEffectiveMaterialTypes(int shaleClientId);
    List<RequestMethodDto> listEffectiveRequestMethods(int shaleClientId);
    List<RequestStatusDto> listEffectiveRequestStatuses(int shaleClientId);
    default List<MaterialTypeDto> listMaterialTypesForAdministration(int shaleClientId, int actorUserId) { throw new UnsupportedOperationException("listMaterialTypesForAdministration"); }
    default List<RequestMethodDto> listRequestMethodsForAdministration(int shaleClientId, int actorUserId) { throw new UnsupportedOperationException("listRequestMethodsForAdministration"); }
    default List<RequestStatusDto> listRequestStatusesForAdministration(int shaleClientId, int actorUserId) { throw new UnsupportedOperationException("listRequestStatusesForAdministration"); }
    default MaterialTypeDto createMaterialType(MaterialTypeCommand command) { throw new UnsupportedOperationException("createMaterialType"); }
    default MaterialTypeDto updateMaterialType(MaterialTypeCommand command) { throw new UnsupportedOperationException("updateMaterialType"); }
    default MaterialTypeDto setMaterialTypeActive(SetLookupActiveCommand command) { throw new UnsupportedOperationException("setMaterialTypeActive"); }
    default void resetMaterialTypeOverride(ResetLookupOverrideCommand command) { throw new UnsupportedOperationException("resetMaterialTypeOverride"); }
    default RequestMethodDto createRequestMethod(RequestMethodCommand command) { throw new UnsupportedOperationException("createRequestMethod"); }
    default RequestMethodDto updateRequestMethod(RequestMethodCommand command) { throw new UnsupportedOperationException("updateRequestMethod"); }
    default RequestMethodDto setRequestMethodActive(SetLookupActiveCommand command) { throw new UnsupportedOperationException("setRequestMethodActive"); }
    default void resetRequestMethodOverride(ResetLookupOverrideCommand command) { throw new UnsupportedOperationException("resetRequestMethodOverride"); }
    default RequestStatusDto createRequestStatus(RequestStatusCommand command) { throw new UnsupportedOperationException("createRequestStatus"); }
    default RequestStatusDto updateRequestStatus(RequestStatusCommand command) { throw new UnsupportedOperationException("updateRequestStatus"); }
    default RequestStatusDto setRequestStatusActive(SetLookupActiveCommand command) { throw new UnsupportedOperationException("setRequestStatusActive"); }
    default void resetRequestStatusOverride(ResetLookupOverrideCommand command) { throw new UnsupportedOperationException("resetRequestStatusOverride"); }
    List<MaterialRequestSummaryDto> listMaterialRequests(long caseId, int shaleClientId);
    Optional<MaterialRequestDetailDto> getMaterialRequest(long caseId, long materialRequestId, int shaleClientId, int actorUserId);
    List<MaterialRequestFollowUpDto> listFollowUps(long caseId, long materialRequestId, int shaleClientId, int actorUserId);
    MaterialRequestDetailDto createMaterialRequest(CreateMaterialRequestCommand command);
    MaterialRequestDetailDto updateMaterialRequest(UpdateMaterialRequestCommand command);

    record MaterialTypeCommand(Integer id, int shaleClientId, int actorUserId, String name, String description, String color, boolean active, String systemKey, Integer sortOrder, byte[] expectedRowVer) {}
    record RequestMethodCommand(Integer id, int shaleClientId, int actorUserId, String name, String color, boolean active, String systemKey, Integer sortOrder, byte[] expectedRowVer) {}
    record RequestStatusCommand(Integer id, int shaleClientId, int actorUserId, String name, String color, boolean active, String systemKey, Integer sortOrder, byte[] expectedRowVer) {}
    record SetLookupActiveCommand(int shaleClientId, int actorUserId, int id, boolean active, byte[] expectedRowVer) {}
    record ResetLookupOverrideCommand(int shaleClientId, int actorUserId, int id) {}

    record CreateMaterialRequestCommand(int shaleClientId, int actorUserId, long caseId, int materialTypeId,
                                        String title, String description, Integer requestedFromContactId,
                                        Integer requestedFromOrganizationId, String requestedFromText,
                                        String requestMethod, String status, int requestedByUserId,
                                        Integer assignedToUserId, LocalDateTime requestedAt,
                                        LocalDate expectedResponseDate, LocalDateTime nextFollowUpAt, Integer followUpIntervalDays) {}

    record UpdateMaterialRequestCommand(int shaleClientId, int actorUserId, long caseId, long materialRequestId, int materialTypeId,
                                        String title, String description, Integer requestedFromContactId,
                                        Integer requestedFromOrganizationId, String requestedFromText,
                                        String requestMethod, String status, int requestedByUserId,
                                        Integer assignedToUserId, LocalDateTime requestedAt, LocalDate relevantStartDate,
                                        LocalDate relevantEndDate, LocalDate expectedResponseDate, LocalDateTime nextFollowUpAt, Integer followUpIntervalDays,
                                        LocalDateTime firstReceivedAt, LocalDateTime fullyReceivedAt, LocalDateTime closedAt,
                                        Integer closedByUserId, String closureReason, String notes, byte[] rowVer) {}
}
