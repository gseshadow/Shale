package com.shale.core.dto;

public record ApplicationReleaseImportResult(long releaseId, String version, Outcome outcome, byte[] rowVersion) {
	public ApplicationReleaseImportResult { rowVersion = rowVersion == null ? null : rowVersion.clone(); }
	@Override public byte[] rowVersion() { return rowVersion == null ? null : rowVersion.clone(); }
	public enum Outcome { CREATED, UNCHANGED, UPDATED }
}
