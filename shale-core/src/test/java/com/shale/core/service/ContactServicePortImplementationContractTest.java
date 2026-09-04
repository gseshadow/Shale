package com.shale.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/** Keeps interface expansion compile-enforced for every concrete adapter and test double. */
class ContactServicePortImplementationContractTest {
	@Test
	void onlyContactCreationProvidesAnExplicitUnsupportedCompatibilityDefault() {
		Arrays.stream(ContactServicePort.class.getDeclaredMethods())
				.filter(method -> Modifier.isPublic(method.getModifiers()))
				.filter(method -> !Modifier.isStatic(method.getModifiers()))
				.filter(method -> !method.getName().equals("createContactProfile"))
				.forEach(method -> assertFalse(method.isDefault(),
						() -> method.getName() + " must remain compile-time enforced on every implementation"));

		var create = Arrays.stream(ContactServicePort.class.getDeclaredMethods())
				.filter(method -> method.getName().equals("createContactProfile"))
				.findFirst().orElseThrow();
		assertTrue(create.isDefault(),
				"contact creation must remain source-compatible with existing anonymous implementations");
		ContactServicePort unsupported = (ContactServicePort) Proxy.newProxyInstance(
				getClass().getClassLoader(), new Class<?>[] { ContactServicePort.class },
				(proxy, method, arguments) -> method.isDefault()
						? InvocationHandler.invokeDefault(proxy, method, arguments)
						: throwUnsupported(method.getName()));
		UnsupportedOperationException failure = assertThrows(UnsupportedOperationException.class,
				() -> unsupported.createContactProfile(null));
		assertEquals("Contact creation is not supported", failure.getMessage());
	}

	private static Object throwUnsupported(String methodName) {
		throw new AssertionError("Unexpected invocation of " + methodName);
	}
}
