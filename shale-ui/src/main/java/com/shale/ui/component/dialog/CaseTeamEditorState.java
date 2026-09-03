package com.shale.ui.component.dialog;

import java.util.*;
import com.shale.core.dto.*;
import com.shale.data.dao.CaseDao;

/** Pure staged state for the Case Team dialog. No method in this class persists data. */
public final class CaseTeamEditorState {
    public static final String RESPONSIBLE_ATTORNEY = "responsible_attorney";

    public static final class Member {
        private final long membershipId;
        private final CaseDao.UserRow user;
        private final byte[] rowVer;
        private final LinkedHashMap<Integer, CaseTeamMemberRoleDto> persistedRoles = new LinkedHashMap<>();
        private final LinkedHashSet<Integer> roleIds = new LinkedHashSet<>();
        Member(long id, CaseDao.UserRow user, byte[] rowVer) { this.membershipId=id; this.user=user; this.rowVer=copy(rowVer); }
        public long membershipId(){return membershipId;} public CaseDao.UserRow user(){return user;}
        public byte[] rowVer(){return copy(rowVer);} public Set<Integer> roleIds(){return Collections.unmodifiableSet(roleIds);}
        public Collection<CaseTeamMemberRoleDto> persistedRoles(){return Collections.unmodifiableCollection(persistedRoles.values());}
    }

    private final List<CaseDao.UserRow> users;
    private final Map<Integer,CaseTeamRoleDefinitionDto> definitions;
    private final LinkedHashMap<Integer,Member> members = new LinkedHashMap<>();
    private final String baseline;

    public CaseTeamEditorState(List<CaseDao.UserRow> users, List<CaseTeamMembershipDto> baseline,
            List<CaseTeamRoleDefinitionDto> definitions) {
        this.users=(users==null?List.<CaseDao.UserRow>of():users).stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(CaseDao.UserRow::displayName,String.CASE_INSENSITIVE_ORDER)).toList();
        LinkedHashMap<Integer,CaseTeamRoleDefinitionDto> defs=new LinkedHashMap<>();
        (definitions==null?List.<CaseTeamRoleDefinitionDto>of():definitions).stream().filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(CaseTeamRoleDefinitionDto::sortOrder).thenComparingInt(CaseTeamRoleDefinitionDto::id))
                .forEach(d->defs.put(d.id(),d)); this.definitions=Map.copyOf(defs);
        Map<Integer,CaseDao.UserRow> byId=new HashMap<>(); this.users.forEach(u->byId.put(u.id(),u));
        for(CaseTeamMembershipDto dto:baseline==null?List.<CaseTeamMembershipDto>of():baseline){
            CaseDao.UserRow user=byId.getOrDefault(dto.userId(),new CaseDao.UserRow(dto.userId(),dto.displayName(),null));
            Member member=new Member(dto.membershipId(),user,dto.rowVer());
            for(CaseTeamMemberRoleDto role:dto.roles()) if(!role.assignmentDeleted()) { member.roleIds.add(role.roleDefinitionId()); member.persistedRoles.put(role.roleDefinitionId(),role); }
            members.put(user.id(),member);
        }
        this.baseline=fingerprint();
    }
    public List<Member> members(){return List.copyOf(members.values());}
    public List<CaseDao.UserRow> search(String text){String q=text==null?"":text.strip().toLowerCase(Locale.ROOT);return users.stream().filter(u->!members.containsKey(u.id())).filter(u->q.isEmpty()||u.displayName().toLowerCase(Locale.ROOT).contains(q)).limit(50).toList();}
    public Member addMember(CaseDao.UserRow user){if(user==null||members.containsKey(user.id()))return members.get(user==null?-1:user.id());Member m=new Member(0,user,null);members.put(user.id(),m);return m;}
    public void removeMember(int userId){members.remove(userId);}
    public boolean addRole(int userId,int roleId,boolean confirmMove){Member target=members.get(userId);CaseTeamRoleDefinitionDto d=definitions.get(roleId);if(target==null||d==null||!d.active()||d.deleted()||target.roleIds.contains(roleId))return false;
        if(RESPONSIBLE_ATTORNEY.equals(d.systemKey())) { Member current=responsibleAttorney(); if(current!=null&&current!=target){if(!confirmMove)return false;current.roleIds.remove(roleId);} }
        return target.roleIds.add(roleId);
    }
    public void removeRole(int userId,int roleId){Member m=members.get(userId);if(m!=null)m.roleIds.remove(roleId);}
    public List<CaseTeamRoleDefinitionDto> availableRoles(int userId){Member m=members.get(userId);if(m==null)return List.of();return definitions.values().stream().filter(d->d.active()&&!d.deleted()&&!m.roleIds.contains(d.id())).sorted(Comparator.comparingInt(CaseTeamRoleDefinitionDto::sortOrder).thenComparingInt(CaseTeamRoleDefinitionDto::id)).toList();}
    public CaseTeamRoleDefinitionDto definition(int id){return definitions.get(id);} public boolean dirty(){return !baseline.equals(fingerprint());}
    public Member responsibleAttorney(){for(Member m:members.values())for(int id:m.roleIds){CaseTeamRoleDefinitionDto d=definitions.get(id);if(d!=null&&RESPONSIBLE_ATTORNEY.equals(d.systemKey()))return m;}return null;}
    private String fingerprint(){StringBuilder b=new StringBuilder();members.values().stream().sorted(Comparator.comparingInt(m->m.user.id())).forEach(m->{b.append(m.user.id()).append(':');m.roleIds.stream().sorted().forEach(id->b.append(id).append(','));b.append(';');});return b.toString();}
    private static byte[] copy(byte[] v){return v==null?null:v.clone();}
}
