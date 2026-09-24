package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.CaseTeamRoleDefinitionDto;
class CaseTeamRoleManagementContractTest {
 private static CaseTeamRoleDefinitionDto role(Integer tenant,String key,Integer legacy,boolean active,boolean deleted,boolean protectedRole,boolean override){return new CaseTeamRoleDefinitionDto(9,tenant,key,legacy,"Counsel","desc","#112233",40,active,deleted,protectedRole,override,Instant.EPOCH,1,null,null,null,null,new byte[]{1,2});}
 @Test void authoritativeMetadataControlsActions(){var global=CaseTeamRoleAdminPane.actions(role(null,"attorney",7,true,false,true,false));assertTrue(global.edit());assertFalse(global.toggle());assertFalse(global.remove());assertFalse(global.restore());var custom=CaseTeamRoleAdminPane.actions(role(3,null,null,true,false,false,false));assertTrue(custom.toggle());assertTrue(custom.remove());var removed=CaseTeamRoleAdminPane.actions(role(3,null,null,false,true,false,false));assertTrue(removed.restore());var protectedRemoved=CaseTeamRoleAdminPane.actions(role(3,"attorney",7,false,true,true,true));assertFalse(protectedRemoved.restore());}
 @Test void editCommandPreservesIdentityContextPresentationAndRowVersion(){var d=role(3,"attorney",7,true,false,true,true);var c=CaseTeamRoleAdminPane.command(d,new CaseTeamRoleAdminPane.Input("Renamed","new","#ABCDEF",17,false),3,22);assertEquals(9,c.id());assertEquals(3,c.tenantId());assertEquals(22,c.actorUserId());assertEquals("Renamed",c.name());assertEquals("new",c.description());assertEquals("#ABCDEF",c.color());assertEquals(17,c.sortOrder());assertFalse(c.active());assertArrayEquals(new byte[]{1,2},c.rowVer());assertEquals("attorney",d.systemKey());assertEquals(7,d.legacyRoleId());}
}
