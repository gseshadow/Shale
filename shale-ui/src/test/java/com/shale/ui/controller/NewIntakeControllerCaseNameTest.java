package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NewIntakeControllerCaseNameTest {

	@Test
	void estateCaseNameWrapsTheNormalGeneratedNameOnce() {
		assertEquals("Smith, Jane", NewIntakeController.buildCaseName(" Jane ", " Smith ", false));
		assertEquals("Estate of Smith, Jane", NewIntakeController.buildCaseName(" Jane ", " Smith ", true));
	}

	@Test
	void estateCaseNameContinuesToHandlePartialAndChangingClientNames() {
		assertEquals("Estate of Jane", NewIntakeController.buildCaseName("Jane", "", true));
		assertEquals("Estate of Smith", NewIntakeController.buildCaseName("", "Smith", true));
		assertEquals("", NewIntakeController.buildCaseName("", "", true));
	}
}
