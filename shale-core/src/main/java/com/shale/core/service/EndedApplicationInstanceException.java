package com.shale.core.service;

/** Raised when a terminal application instance receives a liveness mutation. */
public final class EndedApplicationInstanceException extends IllegalStateException {
	public EndedApplicationInstanceException() { super("Application instance has already ended."); }
}
