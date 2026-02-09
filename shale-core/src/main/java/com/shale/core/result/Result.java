package com.shale.core.result;

import java.util.Objects;
import java.util.Optional;

/**
 * Lightweight success/failure wrapper for service methods.
 */
public final class Result<T> {
	private final T value;
	private final String error;

	private Result(T value, String error) {
		this.value = value;
		this.error = error;
	}

	public static <T> Result<T> ok(T value) {
		return new Result<>(Objects.requireNonNull(value), null);
	}

	public static <T> Result<T> fail(String message) {
		return new Result<>(null, Objects.requireNonNullElse(message, "Unknown error"));
	}

	public boolean isOk() {
		return error == null;
	}

	public Optional<T> value() {
		return Optional.ofNullable(value);
	}

	public Optional<String> error() {
		return Optional.ofNullable(error);
	}

	@Override
	public String toString() {
		return isOk() ? "Result{ok}" : "Result{error=" + error + "}";
	}
}
