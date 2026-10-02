package com.shale.core.update;

public enum UpdateInvocationMode {
	MANUAL,
	UNATTENDED;

	public static UpdateInvocationMode fromArgument(String value) {
		return "UNATTENDED".equalsIgnoreCase(value) ? UNATTENDED : MANUAL;
	}
}
