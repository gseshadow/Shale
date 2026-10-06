package com.shale.data.dao;

import static com.shale.core.service.ContactServicePort.*;
import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;

/** Authoritative baseline and token tests, independent of JavaFX and SQL Server availability. */
class ContactLegacyValidationTest {
    private static final byte[] RV={1};
    private static Object baseline(boolean deleted,String value,String ext,boolean primary)throws Exception{
        var type=Class.forName("com.shale.data.dao.ContactMutationDao$PointState");
        var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        return ctor.newInstance(deleted,RV,value,null,ext,primary,"MOBILE",0,false);
    }
    private static void validate(String method,List<?> intent,Map<Long,Object> baseline)throws Exception{
        var m=ContactMutationDao.class.getDeclaredMethod(method,List.class,Map.class);m.setAccessible(true);
        try{m.invoke(null,intent,baseline);}catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw e;}
    }
    private static IntendedPhoneNumber phone(String value,String extension,boolean primary,boolean deleted){return new IntendedPhoneNumber(1L,RV,"MOBILE",value,extension,primary,deleted,0);}
    @Test void unchangedInvalidActiveAndRemovedPhonesCanBeRetainedRemovedAndRestored()throws Exception{
        for(boolean oldDeleted:List.of(false,true))for(boolean deleted:List.of(false,true))
            assertDoesNotThrow(()->validate("validatePhones",List.of(phone("0",null,false,deleted)),Map.of(1L,baseline(oldDeleted,"0",null,false))));
        assertDoesNotThrow(()->validate("validatePhones",List.of(phone("0",null,true,false)),Map.of(1L,baseline(false,"0",null,true))));
    }
    @Test void restorationOfHistoricalPrimaryRequiresAuthoritativeHistoryAndUnchangedValue()throws Exception{
        var type=Class.forName("com.shale.data.dao.ContactMutationDao$PointState");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
        Object historical=ctor.newInstance(true,RV,"0",null,null,false,"MOBILE",0,true);
        assertDoesNotThrow(()->validate("validatePhones",List.of(phone("0",null,true,false)),Map.of(1L,historical)));
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(phone("00",null,true,false)),Map.of(1L,historical)));
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(phone("0",null,true,false)),Map.of(1L,baseline(true,"0",null,false))));
    }
    @Test void changedCopiedExtensionsAndNewPrimaryCannotBypassSyntax()throws Exception{
        var old=Map.of(1L,baseline(false,"0",null,false));
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(phone("0","01",false,false)),old));
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(phone("0",null,true,false)),old));
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(new IntendedPhoneNumber(null,null,"MOBILE","0",null,false,false,0)),Map.of()));
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(phone("00",null,false,true)),old));
    }
    @Test void baselineIdentityExactSetAndConcurrencyAreMandatory()throws Exception{
        var old=Map.of(1L,baseline(false,"0",null,false));
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(),old));
        assertThrows(SecurityException.class,()->validate("validatePhones",List.of(new IntendedPhoneNumber(2L,RV,"MOBILE","0",null,false,false,0)),old));
        assertThrows(IllegalStateException.class,()->validate("validatePhones",List.of(new IntendedPhoneNumber(1L,new byte[]{2},"MOBILE","0",null,false,false,0)),old));
    }
    @Test void samePhoneDifferentExtensionsCoexist()throws Exception{
        assertDoesNotThrow(()->validate("validatePhones",List.of(new IntendedPhoneNumber(null,null,"WORK","3035550123","001",true,false,0),new IntendedPhoneNumber(null,null,"WORK","(303) 555-0123","002",false,false,1)),Map.of()));
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(new IntendedPhoneNumber(null,null,"WORK","3035550123","001",true,false,0),new IntendedPhoneNumber(null,null,"WORK","(303) 555-0123","001",false,false,1)),Map.of()));
    }
    @Test void unchangedLegacyEmailCanBeRetainedRemovedAndRestoredButCopyOrChangeMustValidate()throws Exception{
        var old=Map.of(1L,baseline(true,"bad",null,false));
        assertDoesNotThrow(()->validate("validateEmails",List.of(new IntendedEmailAddress(1L,RV,"PERSONAL","bad",false,false,0)),old));
        assertThrows(IllegalArgumentException.class,()->validate("validateEmails",List.of(new IntendedEmailAddress(1L,RV,"PERSONAL","worse",false,false,0)),old));
        assertThrows(IllegalArgumentException.class,()->validate("validateEmails",List.of(new IntendedEmailAddress(null,null,"PERSONAL","bad",true,false,0)),Map.of()));
    }
    @Test void userScalarBaselineComparisonDoesNotChangeExistingIdentifiers(){
        assertEquals(" legacy identifier ",UserDao.validatedUserEmail(" legacy identifier "," legacy identifier "));
        assertEquals("0",UserDao.validatedUserPhone("0","0"));
        assertThrows(IllegalArgumentException.class,()->UserDao.validatedUserEmail("changed identifier","legacy identifier"));
        assertThrows(IllegalArgumentException.class,()->UserDao.validatedUserPhone("00","0"));
        assertNull(UserDao.validatedUserPhone(null,"0"));
        assertEquals("display_too_long",assertThrows(com.shale.core.validation.FieldValidationException.class,()->UserDao.validatedUserPhone("303"+" ".repeat(91)+"5550123",null)).errors().getFirst().code());
    }
    @Test void localDuplicatesUseWholeSubscriberAndExtensionAndNeverFullNumberSuffix()throws Exception {
        var local=new IntendedPhoneNumber(null,null,"WORK","555-0123","001",true,false,0);
        var same=new IntendedPhoneNumber(null,null,"WORK","555 0123","001",false,false,1);
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(local,same),Map.of()));
        var otherExtension=new IntendedPhoneNumber(null,null,"WORK","5550123","002",false,false,1);
        assertDoesNotThrow(()->validate("validatePhones",List.of(local,otherExtension),Map.of()));
        var full=new IntendedPhoneNumber(null,null,"WORK","3035550123","001",false,false,1);
        assertDoesNotThrow(()->validate("validatePhones",List.of(local,full),Map.of()));
        assertThrows(IllegalArgumentException.class,()->validate("validatePhones",List.of(phone("555 0123","001",false,false),same),
                Map.of(1L,baseline(false,"555 0123","001",false))));
        assertEquals("555-0123 ext. 001",UserDao.validatedUserPhone("555-0123 x001",null));
    }

}
