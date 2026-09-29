package com.shale.data.dao;

import java.time.Instant;
import java.util.Objects;

/** A bounded, allowlisted description of one completed sensitive administrative read. */
public record AdministrativeReadAuditEvent(
		int shaleClientId,
		int actorUserId,
		ReadType readType,
		int resultCount,
		String metadata) {
	public static final int MAX_METADATA_LENGTH = 1000;

	public enum ReadType {
		APPLICATION_INSTANCE_RECENT_LIST,
		APPLICATION_INSTANCE_VERSION_DISTRIBUTION
	}

	public AdministrativeReadAuditEvent {
		if (shaleClientId <= 0) throw new IllegalArgumentException("shaleClientId must be > 0");
		if (actorUserId <= 0) throw new IllegalArgumentException("actorUserId must be > 0");
		Objects.requireNonNull(readType, "readType");
		if (resultCount < 0) throw new IllegalArgumentException("resultCount must be >= 0");
		if (metadata != null && metadata.length() > MAX_METADATA_LENGTH)
			throw new IllegalArgumentException("metadata exceeds the audit storage bound");
	}

	public static AdministrativeReadAuditEvent recentList(int tenant, int actor, int resultCount,
			int page, int pageSize, String clientType, String version, boolean userFilterPresent,
			boolean activeOnly, Instant startedSince) {
		return new AdministrativeReadAuditEvent(tenant, actor, ReadType.APPLICATION_INSTANCE_RECENT_LIST,
				resultCount, json(
						"activeOnly", Boolean.toString(activeOnly),
						"applicationVersionFilter", nullable(version),
						"clientTypeFilter", nullable(clientType),
						"page", Integer.toString(page),
						"pageSize", Integer.toString(pageSize),
						"since", quote(startedSince.toString()),
						"userFilterPresent", Boolean.toString(userFilterPresent)));
	}

	public static AdministrativeReadAuditEvent versionDistribution(int tenant, int actor, int resultCount,
			Instant startedSince) {
		return new AdministrativeReadAuditEvent(tenant, actor,
				ReadType.APPLICATION_INSTANCE_VERSION_DISTRIBUTION, resultCount,
				json("since", quote(startedSince.toString())));
	}

	private static String nullable(String value) { return value == null ? "null" : quote(value); }
	private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
	private static String json(String... entries) {
		StringBuilder out = new StringBuilder("{");
		for (int i = 0; i < entries.length; i += 2) {
			if (i > 0) out.append(',');
			out.append(quote(entries[i])).append(':').append(entries[i + 1]);
		}
		return out.append('}').toString();
	}
}
