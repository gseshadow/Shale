package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.shale.core.service.MaterialRequestServicePort;

final class RequestMethodEditorContractTest {
    @Test void createLeavesOrderingToDaoAndEditPreservesAuthoritativeOrder(){
        var create=RequestDefinitionAdminPane.requestMethodCreateCommand(41,73,"Email","#123456",true,"email");
        assertNull(create.id()); assertEquals(41,create.shaleClientId()); assertEquals(73,create.actorUserId());
        assertEquals("email",create.systemKey()); assertNull(create.sortOrder()); assertNull(create.expectedRowVer());
        byte[] rv={1,2,3};
        MaterialRequestServicePort.RequestMethodCommand edit=RequestDefinitionAdminPane.requestMethodEditCommand(19,41,73,"Secure Email","#654321",false,"email",27,rv);
        assertEquals(27,edit.sortOrder()); assertSame(rv,edit.expectedRowVer()); assertFalse(edit.active());
    }
}
