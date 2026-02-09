package com.shale.data.errors;

public final class DataException extends RuntimeException {
	public DataException(String message, Throwable cause) {
		super(message, cause);
	}

	public static DataException wrap(String op, String target, Throwable t) {
		return new DataException(op + " failed for " + target, t);
	}
}
