package com.shale.ui.controller;
import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import org.junit.jupiter.api.Test;
final class UserManagementFirmWideRolesContractTest{
 @Test void editorAssignsSeveralIndependentFirmWideRolesButDoesNotDefineThem()throws Exception{String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserManagementPane.java"));assertAll(
  ()->assertFalse(s.contains("Define Role")),()->assertFalse(s.contains("CreateFirmWideRoleCommand")),
  ()->assertTrue(s.contains("Tenant-defined roles")),()->assertTrue(s.contains("reconcileCustomRoles")),
  ()->assertTrue(s.contains("FirmWideRoleAssignmentLifecycleCommand")),()->assertFalse(s.contains("CaseTeamRole")),()->assertFalse(s.contains("CaseUsers")));
 }
 @Test void initialGridHydratesAssignmentsThroughTheBulkBoundary()throws Exception{String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/UserManagementPane.java"));String load=method(s,"loadManagedUsersAsync");assertAll(
  ()->assertTrue(load.contains("listTenantUserFirmWideRoleAssignments(tenantId,actorUserId,includeInactive)"),"The grid must bulk-load only assignment history needed by the active/inactive projection."),
  ()->assertFalse(load.contains("listUserFirmWideRoleAssignments("),"The grid must not restore the per-user assignment N+1 path."));}
 private static String method(String s,String name){int start=s.indexOf(" "+name+"(");int brace=s.indexOf('{',start),depth=0;for(int i=brace;i<s.length();i++){char c=s.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return s.substring(start,i+1);}throw new AssertionError(name);}
}
