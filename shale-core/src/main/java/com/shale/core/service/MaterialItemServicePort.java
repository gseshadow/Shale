package com.shale.core.service;

import com.shale.core.dto.*;
import java.time.*;
import java.util.*;

public interface MaterialItemServicePort {
    List<MaterialItemSummaryDto> listMaterialItems(long caseId, int shaleClientId);
    Optional<MaterialItemDetailDto> getMaterialItem(long caseId, long materialItemId, int shaleClientId, int actorUserId);
    MaterialItemDetailDto createMaterialItem(CreateMaterialItemCommand command);
    MaterialItemDetailDto updateMaterialItem(UpdateMaterialItemCommand command);
    MaterialItemDetailDto changeMaterialItemLocation(ChangeMaterialItemLocationCommand command);
    MaterialItemDetailDto linkMaterialItemToRequest(LinkMaterialItemToRequestCommand command);
    MaterialItemDetailDto unlinkMaterialItemFromRequest(UnlinkMaterialItemFromRequestCommand command);
    MaterialItemDetailDto releaseOrReturnMaterialItem(ReleaseOrReturnMaterialItemCommand command);
    void softDeleteMaterialItem(SoftDeleteMaterialItemCommand command);

    record CreateMaterialItemCommand(int shaleClientId, int actorUserId, long caseId, Long materialRequestId, int materialTypeId, String format, String name, String description, Integer sourceContactId, Integer sourceOrganizationId, String sourceText, int receivedByUserId, LocalDateTime receivedAt, LocalDate relevantStartDate, LocalDate relevantEndDate, String completeness, Integer quantityCount, Integer pageCount, Integer fileCount, String storageLocation, Integer externalLinkId, String physicalCondition, String custodyStatus) {}
    record UpdateMaterialItemCommand(int shaleClientId, int actorUserId, long caseId, long materialItemId, int materialTypeId, String format, String name, String description, Integer sourceContactId, Integer sourceOrganizationId, String sourceText, LocalDate relevantStartDate, LocalDate relevantEndDate, String completeness, Integer quantityCount, Integer pageCount, Integer fileCount, String physicalCondition, byte[] expectedRowVer) {}
    record ChangeMaterialItemLocationCommand(int shaleClientId, int actorUserId, long caseId, long materialItemId, String storageLocation, Integer externalLinkId, byte[] expectedRowVer) {}
    record LinkMaterialItemToRequestCommand(int shaleClientId, int actorUserId, long caseId, long materialItemId, long materialRequestId, byte[] expectedRowVer) {}
    record UnlinkMaterialItemFromRequestCommand(int shaleClientId, int actorUserId, long caseId, long materialItemId, byte[] expectedRowVer) {}
    record ReleaseOrReturnMaterialItemCommand(int shaleClientId, int actorUserId, long caseId, long materialItemId, String custodyStatus, LocalDateTime returnedOrReleasedAt, Integer returnedOrReleasedToContactId, Integer returnedOrReleasedToOrganizationId, String returnedOrReleasedToText, String returnReleaseMethod, String returnReleaseNotes, byte[] expectedRowVer) {}
    record SoftDeleteMaterialItemCommand(int shaleClientId, int actorUserId, long caseId, long materialItemId, byte[] expectedRowVer) {}
}
