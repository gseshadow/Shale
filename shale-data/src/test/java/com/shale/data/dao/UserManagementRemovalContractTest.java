package com.shale.data.dao;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class UserManagementRemovalContractTest {
    private static final String SOURCE;
    static { try { SOURCE=Files.readString(Path.of("src/main/java/com/shale/data/dao/UserDao.java")); } catch(Exception e){throw new ExceptionInInitializerError(e);} }

    @Test void listAlwaysExcludesRemovedButInactiveFilterRemainsIndependent(){
        assertTrue(SOURCE.contains("WHERE ShaleClientId=? AND COALESCE(IsRemoved,0)=0"));
        assertTrue(SOURCE.contains("includeInactive ? \"\" : \" AND COALESCE(is_deleted,0)=0\""));
    }
    @Test void removalIsSoftAtomicConcurrentAuthorizedAndAudited(){
        String method=method("removeUserFromTenant");
        assertTrue(method.contains("requireCurrentAdmin")); assertTrue(method.contains("userId==actor"));
        assertTrue(method.contains("countActiveAdmins")); assertTrue(method.contains("Arrays.equals(target.rowVer(),expectedRowVer)"));
        assertTrue(method.contains("is_deleted=1,IsRemoved=1,RemovedAt=SYSUTCDATETIME(),RemovedByUserId=?"));
        assertTrue(method.contains("EntityActionAuditEvent.Action.REMOVED")); assertTrue(method.contains("con.commit()")); assertTrue(method.contains("con.rollback()"));
        assertFalse(method.contains("DELETE FROM dbo.Users")); assertFalse(method.toLowerCase().contains("email")); assertFalse(method.toLowerCase().contains("password"));
    }
    @Test void removedUsersCannotBeEditedReactivatedOrReset(){
        assertTrue(SOURCE.contains("if(old.removed()) throw"));
        assertTrue(count("AND COALESCE(IsRemoved,0)=0")>=3,"management hydration plus lifecycle/security commands must carry removal predicates");
        assertTrue(SOURCE.contains("Removed users cannot be reactivated."));
        assertTrue(SOURCE.contains("Passwords cannot be reset for removed users."));
        assertTrue(SOURCE.contains("UPDATE dbo.Users SET is_deleted = 0 WHERE Id = ? AND ShaleClientId = ? AND COALESCE(IsRemoved,0)=0"));
        assertTrue(SOURCE.contains("password_alg = 'bcrypt' WHERE Id = ? AND ShaleClientId = ? AND COALESCE(IsRemoved,0)=0"));
    }
    private static int count(String text){int n=0,p=0;while((p=SOURCE.indexOf(text,p))>=0){n++;p+=text.length();}return n;}
    private static String method(String name){int start=SOURCE.indexOf(" "+name+"(");int brace=SOURCE.indexOf('{',start),depth=0;for(int i=brace;i<SOURCE.length();i++){char c=SOURCE.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return SOURCE.substring(start,i+1);}throw new AssertionError(name);}
}
