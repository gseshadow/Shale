package com.shale.core.dto;

import java.time.LocalDateTime;

public final class CaseTimelineEventDto {

	private final long id;
	private final int caseId;
	private final int shaleClientId;
	private final String eventType;
	private final LocalDateTime occurredAt;
	private final Integer actorUserId;
	private final String title;
	private final String body;
	private final String actorDisplayName;

	public CaseTimelineEventDto(long id,
			int caseId,
			int shaleClientId,
			String eventType,
			LocalDateTime occurredAt,
			Integer actorUserId,
			String title,
			String body,
			String actorDisplayName) {
		this.id = id;
		this.caseId = caseId;
		this.shaleClientId = shaleClientId;
		this.eventType = eventType == null ? "" : eventType;
		this.occurredAt = occurredAt;
		this.actorUserId = actorUserId;
		this.title = title == null ? "" : title;
		this.body = body == null ? "" : body;
		this.actorDisplayName = actorDisplayName == null ? "" : actorDisplayName;
	}

	public long getId() {
		return id;
	}

	public int getCaseId() {
		return caseId;
	}

	public int getShaleClientId() {
		return shaleClientId;
	}

	public String getEventType() {
		return eventType;
	}

	public LocalDateTime getOccurredAt() {
		return occurredAt;
	}

	public Integer getActorUserId() {
		return actorUserId;
	}

	public String getTitle() {
		return title;
	}

	public String getBody() {
		return body;
	}

	public String getActorDisplayName() {
		return actorDisplayName;
	}
}
