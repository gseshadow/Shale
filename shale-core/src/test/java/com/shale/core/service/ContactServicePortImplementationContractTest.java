package com.shale.core.service;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** Keeps interface expansion compile-enforced for every concrete adapter and test double. */
class ContactServicePortImplementationContractTest {
	@Test
	void applicationBoundaryDoesNotHideMissingImplementationsBehindDefaults() {
		Arrays.stream(ContactServicePort.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()))
				.filter(method -> !Modifier.isStatic(method.getModifiers()))
				.forEach(method -> assertFalse(method.isDefault(),
						() -> method.getName() + " must remain compile-time enforced on every implementation"));
	}
}
