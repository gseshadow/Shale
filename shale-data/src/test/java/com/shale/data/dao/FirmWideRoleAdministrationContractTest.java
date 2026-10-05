package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;import org.junit.jupiter.api.Test;
final class FirmWideRoleAdministrationContractTest{
 private static String source(String path)throws Exception{return Files.readString(Path.of(path)).replace("\r\n","\n");}
 @Test void mutationsAreAdminTenantScopedConcurrentTransactionalAndAudited()throws Exception{String s=source("src/main/java/com/shale/data/dao/UserDao.java");assertAll(
  ()->assertTrue(s.contains("requireRoleAdmin(c,tenant,actor)")),()->assertTrue(s.contains("Requested actor does not match session context.")),
  ()->assertTrue(s.contains("AND RowVer=?")),()->assertTrue(s.contains("c.setAutoCommit(false)")),()->assertTrue(s.contains("c.rollback()")),
  ()->assertTrue(s.contains("EntityType.FIRM_WIDE_ROLE")),()->assertTrue(s.contains("EntityType.USER_FIRM_WIDE_ROLE")));
 }
 @Test void builtInsAndCaseTeamAuthorityStaySeparateAndHistoryIsPreserved()throws Exception{String s=source("src/main/java/com/shale/data/dao/UserDao.java");String eligibility=s.substring(s.indexOf("currentActorHasFirmWideRole"),s.indexOf("listFirmWideRolesForAdministration"));assertAll(
  ()->assertTrue(s.contains("Administrator and Attorney definitions are protected.")),()->assertTrue(s.contains("Built-in membership is managed through user flags.")),
  ()->assertTrue(s.contains("Soft deletion intentionally retains every assignment row")),()->assertFalse(eligibility.contains("CaseUsers")),()->assertFalse(eligibility.contains("CaseTeam")));
 }
 @Test void phase1bMigrationAndVerificationAreSeparateAndReadOnly()throws Exception{String m=source("../docs/sql/2026-09-23_firm_wide_roles_audit_allowlist_phase1b.sql"),v=source("../docs/sql/2026-09-23_firm_wide_roles_phase1b_verify.sql");assertAll(()->assertTrue(m.contains("BEGIN TRANSACTION")),()->assertTrue(m.contains("WITH CHECK ADD CONSTRAINT")),()->assertTrue(v.contains("FindingCount")),()->assertFalse(v.matches("(?is).*(INSERT|UPDATE|DELETE|MERGE|ALTER|CREATE|DROP)\\s+dbo\\..*")));}

 @Test void userViewReadsAuthorizeTheSessionActorWithoutGrantingMutation()throws Exception{String s=source("src/main/java/com/shale/data/dao/UserDao.java");String reads=s.substring(s.indexOf("listFirmWideRolesForUserView"),s.indexOf("createFirmWideRole"));assertAll(
  ()->assertTrue(reads.contains("requireRoleViewer(c,tenant,actor)"),"User View reads must validate tenant and actor."),
  ()->assertTrue(s.contains("sessionActor!=actor"),"The requested actor must match session context."),
  ()->assertTrue(reads.contains("d.IsActive=1 AND d.IsDeleted=0"),"Inactive definitions cannot be effective on User View."),
  ()->assertFalse(reads.contains("CaseUsers"),"Firm-wide roles must remain separate from case-team roles."));}

 @Test void managementAssignmentHydrationIsSetBasedAndTenantScoped()throws Exception{String s=source("src/main/java/com/shale/data/dao/UserDao.java");String bulk=method(s,"listTenantUserFirmWideRoleAssignments");assertAll(
  ()->assertTrue(bulk.contains("requireRoleAdmin(c,tenant,actor)"),"The bulk read must retain authoritative administrator authorization."),
  ()->assertTrue(bulk.contains("WHERE a.ShaleClientId=?"),"The assignment query must remain explicitly tenant scoped."),
  ()->assertTrue(bulk.contains("JOIN dbo.Users u")&&bulk.contains("COALESCE(u.IsRemoved,0)=0"),"Removed tenant memberships must not be hydrated."),
  ()->assertTrue(bulk.contains("includeInactive ?")&&bulk.contains("COALESCE(u.is_deleted,0)=0"),"The bulk read must mirror the grid's active-only default."),
  ()->assertEquals(1,count(bulk,"executeQuery()"),"The assignment count must stay constant rather than growing per displayed user."));}

 private static int count(String source,String token){int count=0,from=0;while((from=source.indexOf(token,from))>=0){count++;from+=token.length();}return count;}
 private static String method(String source,String name){int start=source.indexOf(" "+name+"(");int brace=source.indexOf('{',start),depth=0;for(int i=brace;i<source.length();i++){char c=source.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return source.substring(start,i+1);}throw new AssertionError(name);}
}
