package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrganizationValueValidationTest {
    private static final Class<?> OWNER=OrganizationStructuredContactMutationDao.class;
    private static final Class<?> ROW=Arrays.stream(OWNER.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Row")).findFirst().orElseThrow();
    private static Object row(List<String> values,boolean primary,boolean deleted,boolean historical)throws Exception{
        Constructor<?> constructor=ROW.getDeclaredConstructor(long.class,String.class,List.class,boolean.class,int.class,boolean.class,byte[].class,boolean.class);constructor.setAccessible(true);
        return constructor.newInstance(1L,"WORK",values,primary,0,deleted,new byte[]{1},historical);
    }
    private static List<?> validate(String category,Object input,Object baseline)throws Exception{
        Field field=OWNER.getDeclaredField(category);field.setAccessible(true);Object spec=field.get(null);Method method=OWNER.getDeclaredMethod("validated",spec.getClass(),ROW,ROW);method.setAccessible(true);
        Object result;try{result=method.invoke(null,spec,input,baseline);}catch(InvocationTargetException error){if(error.getCause() instanceof RuntimeException runtime)throw runtime;throw error;}
        Method values=ROW.getDeclaredMethod("values");values.setAccessible(true);return (List<?>)values.invoke(result);
    }
    @Test void unchangedInvalidPhoneAndEmailKeepExactValuesDuringLifecycleAndUnrelatedEdits()throws Exception{
        for(boolean deleted:List.of(false,true)){
            var phone=List.of(" 0 ","old canonical","bad extension");assertEquals(phone,validate("PHONE",row(phone,false,false,false),row(phone,false,deleted,false)));
            var email=List.of(" Legacy identifier ","legacy");assertEquals(email,validate("EMAIL",row(email,false,false,false),row(email,false,deleted,false)));
        }
    }
    @Test void newChangedAndNewlySelectedInvalidValuesFailButTrustedFormerPrimaryRestores()throws Exception{
        var values=List.of("0","","001");var baseline=row(values,false,false,false);
        assertThrows(IllegalArgumentException.class,()->validate("PHONE",row(values,true,false,false),baseline));
        assertThrows(IllegalArgumentException.class,()->validate("PHONE",row(List.of("0","","002"),false,false,false),baseline));
        assertThrows(IllegalArgumentException.class,()->validate("PHONE",row(values,false,false,false),null));
        assertEquals(values,validate("PHONE",row(values,true,false,false),row(values,false,true,true)));
        assertThrows(IllegalArgumentException.class,()->validate("PHONE",row(values,true,false,false),row(values,false,true,false)));
    }
    @Test void validChangedValuesNormalizeOnlyCanonicalColumnsAndExtractExtension()throws Exception{
        assertEquals(List.of("(303) 555-0123","+13035550123","001"),validate("PHONE",row(List.of(" (303) 555-0123 x001 ","",""),true,false,false),null));
        assertEquals(List.of("O'Neil+tag@Example.technology","o'neil+tag@example.technology"),validate("EMAIL",row(List.of(" O'Neil+tag@Example.technology ",""),true,false,false),null));
    }
    @Test void localStructuredValuesNormalizeWithoutNullAndDuplicatesRemainKindAndExtensionSpecific()throws Exception {
        assertEquals(List.of("555-0123","5550123","001"),validate("PHONE",row(List.of("555-0123 x001","",""),true,false,false),null));
        Field spec=OWNER.getDeclaredField("PHONE");spec.setAccessible(true);
        Method key=OWNER.getDeclaredMethod("duplicateKey",spec.getType(),ROW);key.setAccessible(true);
        Object local=key.invoke(null,spec.get(null),row(List.of("555-0123","","001"),false,false,false));
        assertEquals(local,key.invoke(null,spec.get(null),row(List.of("555 0123","","001"),false,false,false)));
        assertNotEquals(local,key.invoke(null,spec.get(null),row(List.of("3035550123","","001"),false,false,false)));
        assertNotEquals(local,key.invoke(null,spec.get(null),row(List.of("5550123","","002"),false,false,false)));
    }

    @Test void pastedPhoneAndFaxRowsShareFormattedWholeNumberNormalization() throws Exception {
        assertEquals(List.of("(505) 903-3568","+15059033568","001"),validate("PHONE",row(List.of("phone: 505 903 3568 x001","",""),true,false,false),null));
        assertEquals(List.of("+1 (505) 903-3568","+15059033568",""),validate("PHONE",row(List.of("1 505 903 3568","",""),true,false,false),null));
        assertThrows(IllegalArgumentException.class,()->validate("PHONE",row(List.of("2 505 903 3568","",""),true,false,false),null));
    }

}
