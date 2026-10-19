package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.service.ContactServicePort.ContactPhoneNumber;
import com.shale.core.service.OrganizationServicePort.OrganizationPhoneNumber;
import com.shale.core.service.OrganizationServicePort.OrganizationPhoneKind;
import com.shale.ui.testutil.JavaFxTestSupport;
import java.lang.reflect.*;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The real Contact/Organization stages must agree with persistence on complete phone keys. */
class ContactPhoneEntryStageTest {
    @BeforeAll static void toolkit(){JavaFxTestSupport.ensureToolkitStarted();}
    private static Object stage(Class<?> owner,List<?> rows)throws Exception {
        Class<?> type=Class.forName(owner.getName()+"$PhoneEditor");
        Constructor<?> constructor=type.getDeclaredConstructor(List.class);constructor.setAccessible(true);
        return constructor.newInstance(rows);
    }
    private static Object item(Object stage,int index)throws Exception {
        Field items=stage.getClass().getSuperclass().getDeclaredField("items");items.setAccessible(true);
        return ((List<?>)items.get(stage)).get(index);
    }
    private static void set(Object item,String name,Object value)throws Exception {
        Field field=item.getClass().getDeclaredField(name);field.setAccessible(true);field.set(item,value);
    }
    private static void validate(Object stage)throws Exception {
        Method method=stage.getClass().getDeclaredMethod("validate");method.setAccessible(true);
        try{method.invoke(stage);}catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException error)throw error;throw failure;}
    }
    private static List<ContactPhoneNumber> contacts(){return List.of(
        new ContactPhoneNumber(1,"WORK","0",null,"001",true,0,false,null,null,new byte[]{1}),
        new ContactPhoneNumber(2,"WORK","00",null,"001",false,1,false,null,null,new byte[]{2}));}
    private static List<OrganizationPhoneNumber> organizations(){return List.of(
        new OrganizationPhoneNumber(1,7,4,OrganizationPhoneKind.FAX,"FAX","0",null,"001",true,0,false,null,new byte[]{1}),
        new OrganizationPhoneNumber(2,7,4,OrganizationPhoneKind.FAX,"FAX","00",null,"001",false,1,false,null,new byte[]{2}));}
    @Test void localAndFullNumbersRemainDistinctButFormattingVariantsCollide() {
        JavaFxTestSupport.runAndWait(()->{
            for(Object stage:List.of(stage(ContactViewController.class,contacts()),stage(OrganizationAggregateEditor.class,organizations()))) {
                assertDoesNotThrow(()->validate(stage),"Unchanged invalid legacy rows remain retainable");
                set(item(stage,0),"number","Call: (903) 3568");set(item(stage,1),"number","1 505 903 3568");
                assertDoesNotThrow(()->validate(stage),"Local and full must never match by their last seven digits");
                set(item(stage,1),"number","903-3568 x001");
                assertThrows(IllegalArgumentException.class,()->validate(stage),"Equivalent local numbers/extensions must collide");
                set(item(stage,1),"extension","002");set(item(stage,1),"number","9033568");
                assertDoesNotThrow(()->validate(stage),"Distinct extensions may coexist");
                set(item(stage,1),"number","2 505 903 3568");assertThrows(IllegalArgumentException.class,()->validate(stage));
            }
        });
    }
}
