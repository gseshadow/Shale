package com.shale.data.service.adapter;

import com.shale.core.dto.*;
import com.shale.core.service.MaterialRequestServicePort;
import com.shale.data.dao.MaterialRequestDao;
import java.util.*;

public final class MaterialRequestServiceAdapter implements MaterialRequestServicePort {
    public interface SensitiveReadAuditSink { void auditRead(String fieldName, String screenName, String objectType, Long objectId); }
    private final MaterialRequestDao dao;
    private final SensitiveReadAuditSink readAuditSink;
    public MaterialRequestServiceAdapter(MaterialRequestDao dao){this(dao, null);}
    public MaterialRequestServiceAdapter(MaterialRequestDao dao, SensitiveReadAuditSink readAuditSink){this.dao=Objects.requireNonNull(dao,"dao"); this.readAuditSink=readAuditSink;}
    public List<MaterialTypeDto> listEffectiveMaterialTypes(int shaleClientId){return dao.listEffectiveMaterialTypes(shaleClientId);}
    public List<RequestMethodDto> listEffectiveRequestMethods(int shaleClientId){return dao.listEffectiveRequestMethods(shaleClientId);}
    public List<RequestStatusDto> listEffectiveRequestStatuses(int shaleClientId){return dao.listEffectiveRequestStatuses(shaleClientId);}
    public List<MaterialRequestSummaryDto> listMaterialRequests(long caseId,int shaleClientId){return dao.listMaterialRequests(caseId,shaleClientId);}    
    public Optional<MaterialRequestDetailDto> getMaterialRequest(long caseId,long materialRequestId,int shaleClientId,int actorUserId){ if(readAuditSink!=null) readAuditSink.auditRead("MaterialRequest.Detail", "CASE_MATERIALS_REQUEST_DETAIL", "MaterialRequest", materialRequestId); return Optional.ofNullable(dao.findMaterialRequest(caseId,materialRequestId,shaleClientId));}
    public List<MaterialRequestFollowUpDto> listFollowUps(long caseId,long materialRequestId,int shaleClientId,int actorUserId){ if(readAuditSink!=null) readAuditSink.auditRead("MaterialRequest.FollowUps", "CASE_MATERIALS_FOLLOW_UP_HISTORY", "MaterialRequest", materialRequestId); return dao.listFollowUps(caseId,materialRequestId,shaleClientId);}
    public MaterialRequestDetailDto createMaterialRequest(CreateMaterialRequestCommand command){return dao.create(command);}
    public MaterialRequestDetailDto updateMaterialRequest(UpdateMaterialRequestCommand command){return dao.update(command);}
}
