package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class UserServiceAdapterDelegationContractTest {
    @Test void productionAdapterDelegatesFirmWideEligibilityToUserDao()throws Exception{
        String source=Files.readString(Path.of("src/main/java/com/shale/data/service/adapter/UserServiceAdapter.java")).replace("\r\n","\n");
        String method=method(source,"currentActorHasFirmWideRole");
        assertTrue(method.contains("userDao.currentActorHasFirmWideRole(shaleClientId, firmWideRoleDefinitionId)"),
            "the production service adapter must reach the authoritative DAO eligibility operation");
    }
    @Test void productionAdapterDelegatesFirmWideAdministrationToUserDao()throws Exception{
        String source=Files.readString(Path.of("src/main/java/com/shale/data/service/adapter/UserServiceAdapter.java"));
        for(String method:java.util.List.of("listFirmWideRolesForAdministration","listUserFirmWideRoleAssignments","listFirmWideRolesForUserView","listUserFirmWideRoleAssignmentsForView","createFirmWideRole","renameFirmWideRole","setFirmWideRoleActive","deleteFirmWideRole","assignFirmWideRole","removeFirmWideRoleAssignment","restoreFirmWideRoleAssignment"))
            assertTrue(method(source,method).contains("userDao."+method+"("),method+" must delegate to UserDao");
    }
    private static String method(String s,String name){int start=s.indexOf(" "+name+"(");int brace=s.indexOf('{',start),depth=0;for(int i=brace;i<s.length();i++){char c=s.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return s.substring(start,i+1);}throw new AssertionError(name);}
}
