package com.shale.data.service.adapter;

import com.shale.core.dto.*;
import com.shale.core.service.MaterialItemServicePort;
import com.shale.data.dao.MaterialItemDao;
import java.util.*;

public final class MaterialItemServiceAdapter implements MaterialItemServicePort {
    public interface SensitiveReadAuditSink { void auditRead(String fieldName, String screenName, String objectType, Long objectId); }
    private final MaterialItemDao dao;
    private final SensitiveReadAuditSink readAuditSink;
    public MaterialItemServiceAdapter(MaterialItemDao dao){this(dao,null);}
    public MaterialItemServiceAdapter(MaterialItemDao dao, SensitiveReadAuditSink readAuditSink){this.dao=Objects.requireNonNull(dao,"dao");this.readAuditSink=readAuditSink;}
    public List<MaterialItemSummaryDto> listMaterialItems(long caseId,int shaleClientId){return dao.listMaterialItems(caseId,shaleClientId);}    
    public Optional<MaterialItemDetailDto> getMaterialItem(long caseId,long materialItemId,int shaleClientId,int actorUserId){MaterialItemDetailDto item=dao.findMaterialItem(caseId,materialItemId,shaleClientId); if(item!=null&&readAuditSink!=null) readAuditSink.auditRead("MaterialItem.Detail","CASE_MATERIALS_ITEM_DETAIL","MaterialItem",materialItemId); return Optional.ofNullable(item);}    
    public MaterialItemDetailDto createMaterialItem(CreateMaterialItemCommand command){return dao.create(Objects.requireNonNull(command));}
    public MaterialItemDetailDto updateMaterialItem(UpdateMaterialItemCommand command){return dao.update(Objects.requireNonNull(command));}
    public MaterialItemDetailDto changeMaterialItemLocation(ChangeMaterialItemLocationCommand command){return dao.changeLocation(Objects.requireNonNull(command));}
    public MaterialItemDetailDto linkMaterialItemToRequest(LinkMaterialItemToRequestCommand command){return dao.linkRequest(Objects.requireNonNull(command));}
    public MaterialItemDetailDto unlinkMaterialItemFromRequest(UnlinkMaterialItemFromRequestCommand command){return dao.unlinkRequest(Objects.requireNonNull(command));}
    public MaterialItemDetailDto releaseOrReturnMaterialItem(ReleaseOrReturnMaterialItemCommand command){return dao.releaseOrReturn(Objects.requireNonNull(command));}
    public void softDeleteMaterialItem(SoftDeleteMaterialItemCommand command){dao.softDelete(Objects.requireNonNull(command));}
}
