package com.shale.core.service;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.core.validation.ValueUpdate;
import org.junit.jupiter.api.Test;
class ContactValueUpdateTest {
    @Test void retainSetAndClearAreDistinctAndCannotSmuggleAnUnchangedValue(){
        assertTrue(ValueUpdate.retain().retained());
        assertNull(new ValueUpdate(ValueUpdate.Action.CLEAR,null,null).input());
        assertEquals("0",new ValueUpdate(ValueUpdate.Action.SET,"0",null).input(),"Core preserves input; the dependency-free contract delegates parsing to the implementation.");
        assertThrows(IllegalArgumentException.class,()->new ValueUpdate(ValueUpdate.Action.RETAIN,"0",null));
        assertThrows(IllegalArgumentException.class,()->new ValueUpdate(ValueUpdate.Action.CLEAR,null,"001"));
        assertThrows(IllegalArgumentException.class,()->new ValueUpdate(ValueUpdate.Action.SET,"",null));
    }
}
