package com.shale.desktop;

import javafx.application.Application;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaleLauncherTest {

	@Test
	void packagedEntryPointIsAPlainPublicStaticMainClass() throws Exception {
		assertFalse(Application.class.isAssignableFrom(ShaleLauncher.class));
		assertEquals(Object.class, ShaleLauncher.class.getSuperclass());
		Method main = ShaleLauncher.class.getDeclaredMethod("main", String[].class);
		assertTrue(Modifier.isPublic(main.getModifiers()));
		assertTrue(Modifier.isStatic(main.getModifiers()));
		assertEquals(void.class, main.getReturnType());
	}
}
