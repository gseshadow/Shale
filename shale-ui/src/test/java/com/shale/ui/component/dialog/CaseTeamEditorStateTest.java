package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.shale.core.dto.*;
import com.shale.data.dao.CaseDao;

class CaseTeamEditorStateTest {
    private static CaseDao.UserRow user(int id,String name){return new CaseDao.UserRow(id,name,null);}
    private static CaseTeamRoleDefinitionDto def(int id,String key,String name,int order,boolean active,boolean deleted){return new CaseTeamRoleDefinitionDto(id,null,key,null,name,null,"#336699",order,active,deleted,key!=null,false,null,null,null,null,null,null,null);}
    private static CaseTeamMemberRoleDto assigned(long id,CaseTeamRoleDefinitionDto d){return new CaseTeamMemberRoleDto(id,d.id(),d.systemKey(),d.name(),d.active(),d.deleted(),false,null,null,new byte[]{1});}
    private static CaseTeamMembershipDto member(long id,CaseDao.UserRow u,CaseTeamMemberRoleDto...roles){return new CaseTeamMembershipDto(id,90,u.id(),u.displayName(),null,false,new byte[]{1},List.of(roles));}

    @Test void eligibleUsersAppearSortedBeforeTypingAndFilterAsTextChanges(){var zoe=user(3,"Zoe Young");var amy=user(1,"Amy Jaramillo");var brian=user(2,"Brian Downing");var s=new CaseTeamEditorState(List.of(zoe,brian,amy),List.of(member(7,amy)),List.of());assertEquals(List.of(brian,zoe),s.search(""),"empty search must browse all eligible users in display-name order");assertEquals(List.of(brian),s.search("down"),"typing must filter the visible candidates");assertTrue(s.search("missing").isEmpty(),"unmatched filters must support the visible empty state");}
    @Test void searchSelectionStagesRolelessMemberAndExcludesItUntilRemoved(){var amy=user(1,"Amy Jaramillo");var brian=user(2,"Brian Downing");var s=new CaseTeamEditorState(List.of(amy,brian),List.of(member(7,amy)),List.of());assertEquals(List.of(brian),s.search("Brian"));var staged=s.addMember(brian);assertTrue(staged.roleIds().isEmpty(),"new membership must be roleless");assertTrue(s.search("").isEmpty(),"staged members cannot be offered twice");s.removeMember(brian.id());assertEquals(List.of(brian),s.search(""),"removed staged member must immediately return to results");}
    @Test void multipleRolesAreOrderedUniqueAndFinalRemovalPreservesMembership(){var u=user(1,"Amy");var a=def(10,null,"Case Manager",20,true,false);var b=def(11,null,"Assistant",10,true,false);var s=new CaseTeamEditorState(List.of(u),List.of(member(7,u)),List.of(a,b));assertEquals(List.of(b,a),s.availableRoles(1));assertTrue(s.addRole(1,a.id(),true));assertFalse(s.addRole(1,a.id(),true),"duplicate role must be rejected");assertTrue(s.addRole(1,b.id(),true));s.removeRole(1,a.id());s.removeRole(1,b.id());assertEquals(1,s.members().size());assertTrue(s.members().get(0).roleIds().isEmpty());}
    @Test void inactiveHistoricalRoleRemainsVisibleButCannotBeSelected(){var u=user(1,"Amy");var inactive=def(12,null,"Former Role",1,false,false);var role=assigned(4,inactive);var s=new CaseTeamEditorState(List.of(u),List.of(member(7,u,role)),List.of(inactive));assertEquals(Set.of(12),s.members().get(0).roleIds());assertTrue(s.availableRoles(1).isEmpty());s.removeRole(1,12);assertEquals(1,s.members().size());}
    @Test void responsibleAttorneyMoveRequiresConfirmationAndNeverRemovesEitherMember(){var a=user(1,"Current");var b=user(2,"Proposed");var ra=def(4,"responsible_attorney","Responsible Attorney",1,true,false);var s=new CaseTeamEditorState(List.of(a,b),List.of(member(7,a,assigned(8,ra)),member(9,b)),List.of(ra));assertFalse(s.addRole(2,4,false),"cancel must leave staged state unchanged");assertEquals(1,s.responsibleAttorney().user().id());assertTrue(s.addRole(2,4,true));assertEquals(2,s.responsibleAttorney().user().id());assertEquals(2,s.members().size());assertFalse(s.members().get(0).roleIds().contains(4));}
    @Test void dirtyStateComparesImmutableBaseline(){var u=user(1,"Amy");var s=new CaseTeamEditorState(List.of(u),List.of(),List.of());assertFalse(s.dirty());s.addMember(u);assertTrue(s.dirty());s.removeMember(1);assertFalse(s.dirty());}
}
