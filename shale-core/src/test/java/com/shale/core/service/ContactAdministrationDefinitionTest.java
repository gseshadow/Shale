package com.shale.core.service;

import static com.shale.core.service.ContactServicePort.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ContactAdministrationDefinitionTest {
    @Test void defensivelyCopiesAdministrationRowVersion() {
        byte[] rowVer={1,2};
        AdministrationDefinition d=new AdministrationDefinition(DefinitionCategory.CREDENTIAL, 8, 7,
                "doctor_of_medicine", "Doctor of Medicine", "MD", null, 0, true, false,
                DefinitionOrigin.CUSTOM, null, DefinitionOverlayState.EFFECTIVE, rowVer);
        rowVer[0]=9;
        assertArrayEquals(new byte[]{1,2},d.rowVer());
        byte[] returned=d.rowVer(); returned[1]=9;
        assertArrayEquals(new byte[]{1,2},d.rowVer());
        assertNotEquals(d.name(),d.abbreviation());
    }
}
