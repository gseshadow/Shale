package com.shale.core.service;

import com.shale.core.dto.*;
import java.util.List;
import java.util.Optional;

public interface MaterialRequestServicePort {
    List<MaterialTypeDto> listEffectiveMaterialTypes(int shaleClientId);
    List<MaterialRequestSummaryDto> listMaterialRequests(long caseId, int shaleClientId);
    Optional<MaterialRequestDetailDto> getMaterialRequest(long caseId, long materialRequestId, int shaleClientId, int actorUserId);
    List<MaterialRequestFollowUpDto> listFollowUps(long caseId, long materialRequestId, int shaleClientId, int actorUserId);
}
