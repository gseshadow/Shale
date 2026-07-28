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
    public List<MaterialTypeDto> listMaterialTypesForAdministration(int shaleClientId,int actorUserId){return dao.listMaterialTypesForAdministration(shaleClientId,actorUserId);}
    public List<RequestMethodDto> listRequestMethodsForAdministration(int shaleClientId,int actorUserId){return dao.listRequestMethodsForAdministration(shaleClientId,actorUserId);}
    public List<RequestStatusDto> listRequestStatusesForAdministration(int shaleClientId,int actorUserId){return dao.listRequestStatusesForAdministration(shaleClientId,actorUserId);}
    public MaterialTypeDto createMaterialType(MaterialTypeCommand c){validate(c.name(),120);return dao.createMaterialType(c);}
    public MaterialTypeDto updateMaterialType(MaterialTypeCommand c){if(c.id()==null)throw new IllegalArgumentException("Material type id is required.");rv(c.expectedRowVer());validate(c.name(),120);return dao.updateMaterialType(c);}
    public MaterialTypeDto setMaterialTypeActive(SetLookupActiveCommand c){rv(c.expectedRowVer());return dao.setMaterialTypeActive(c);}
    public void resetMaterialTypeOverride(ResetLookupOverrideCommand c){dao.resetMaterialTypeOverride(c);}
    public RequestMethodDto createRequestMethod(RequestMethodCommand c){validate(c.name(),120);return dao.createRequestMethod(c);}
    public RequestMethodDto updateRequestMethod(RequestMethodCommand c){if(c.id()==null)throw new IllegalArgumentException("Request method id is required.");rv(c.expectedRowVer());validate(c.name(),120);return dao.updateRequestMethod(c);}
    public RequestMethodDto setRequestMethodActive(SetLookupActiveCommand c){rv(c.expectedRowVer());return dao.setRequestMethodActive(c);}
    public void resetRequestMethodOverride(ResetLookupOverrideCommand c){dao.resetRequestMethodOverride(c);}
    public RequestStatusDto createRequestStatus(RequestStatusCommand c){validate(c.name(),120);return dao.createRequestStatus(c);}
    public RequestStatusDto updateRequestStatus(RequestStatusCommand c){if(c.id()==null)throw new IllegalArgumentException("Request status id is required.");rv(c.expectedRowVer());validate(c.name(),120);return dao.updateRequestStatus(c);}
    public RequestStatusDto setRequestStatusActive(SetLookupActiveCommand c){rv(c.expectedRowVer());return dao.setRequestStatusActive(c);}
    public void resetRequestStatusOverride(ResetLookupOverrideCommand c){dao.resetRequestStatusOverride(c);}
    public List<MaterialRequestSummaryDto> listMaterialRequests(long caseId,int shaleClientId){auditList(caseId);return dao.listMaterialRequests(caseId,shaleClientId);}
    public List<MaterialRequestSummaryDto> listMaterialRequests(long caseId,int shaleClientId,boolean includeDeleted){auditList(caseId);return dao.listMaterialRequests(caseId,shaleClientId,includeDeleted);}
    public Optional<MaterialRequestDetailDto> getMaterialRequest(long caseId,long materialRequestId,int shaleClientId,int actorUserId){ if(readAuditSink!=null) readAuditSink.auditRead("MaterialRequest.Detail", "CASE_MATERIALS_REQUEST_DETAIL", "MaterialRequest", materialRequestId); return Optional.ofNullable(dao.findMaterialRequest(caseId,materialRequestId,shaleClientId));}
    public List<MaterialRequestFollowUpDto> listFollowUps(long caseId,long materialRequestId,int shaleClientId,int actorUserId){ if(readAuditSink!=null) readAuditSink.auditRead("MaterialRequest.FollowUps", "CASE_MATERIALS_FOLLOW_UP_HISTORY", "MaterialRequest", materialRequestId); return dao.listFollowUps(caseId,materialRequestId,shaleClientId);}
    public MaterialRequestDetailDto createMaterialRequest(CreateMaterialRequestCommand command){validateRequestedFrom(command.requestedFromContactId(),command.requestedFromOrganizationId());validateRequestedRange(command.requestedRangeStartDate(),command.requestedRangeEndDate());return dao.create(command);}
    public MaterialRequestDetailDto updateMaterialRequest(UpdateMaterialRequestCommand command){validateRequestedFrom(command.requestedFromContactId(),command.requestedFromOrganizationId());validateRequestedRange(command.requestedRangeStartDate(),command.requestedRangeEndDate());return dao.update(command);}
    public void deleteMaterialRequest(DeleteMaterialRequestCommand command){rv(command.rowVer());dao.softDelete(command);}
    private void auditList(long caseId){if(readAuditSink!=null)readAuditSink.auditRead("MaterialRequest.Description","CASE_MATERIALS_REQUEST_LIST","Case",caseId);}
    private static void validate(String name,int max){if(name==null||name.trim().isEmpty())throw new IllegalArgumentException("Name is required.");if(name.trim().length()>max)throw new IllegalArgumentException("Name is too long.");}
    private static void validateRequestedFrom(Integer contactId,Integer organizationId){if(contactId==null&&organizationId==null)throw new IllegalArgumentException("Requested From is required.");if(contactId!=null&&organizationId!=null)throw new IllegalArgumentException("Choose either a Requested From contact or organization, not both.");if(contactId!=null&&contactId<=0||organizationId!=null&&organizationId<=0)throw new IllegalArgumentException("Requested From selection is invalid.");}
    private static void validateRequestedRange(java.time.LocalDate start,java.time.LocalDate end){if(start!=null&&end!=null&&start.isAfter(end))throw new IllegalArgumentException("Requested Date Start cannot be after Requested Date End.");}
    private static void rv(byte[] v){if(v==null||v.length==0)throw new IllegalArgumentException("Request version is required.");}
}
