package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ContactClassificationAdminPaneTest {
    @Test void generatesDeterministicSnakeCaseWithoutCollisionSuffixes() {
        assertEquals("doctor_of_medicine", ContactClassificationAdminPane.systemKeyFromName("Doctor of Medicine"));
        assertEquals("ete_specialty", ContactClassificationAdminPane.systemKeyFromName("Été Specialty"));
		assertEquals("doctor_of_medicine", ContactClassificationAdminPane.systemKeyFromName("  Doctor---of...Medicine  "));
		assertEquals("", ContactClassificationAdminPane.systemKeyFromName(" --!? "));
        assertFalse(ContactClassificationAdminPane.systemKeyFromName("Expert").matches("expert_[0-9]+"));
    }

    @Test void validatesPhaseOneCSystemKeyShape() {
        assertTrue(ContactClassificationAdminPane.validSystemKey("doctor_of_medicine"));
        assertFalse(ContactClassificationAdminPane.validSystemKey("Doctor of Medicine"));
        assertFalse(ContactClassificationAdminPane.validSystemKey("_doctor"));
    }
}
