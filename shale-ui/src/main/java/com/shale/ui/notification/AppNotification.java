package com.shale.ui.notification;

import java.time.Instant;
import java.util.Objects;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public final class AppNotification {
	private final String id;
	private final NotificationCategory category;
	private final NotificationSeverity severity;
	private final String title;
	private final String message;
	private final Instant createdAt;
	private final NotificationTargetScope targetScope;
	private final boolean showAsBanner;
	private final Long durableNotificationId;
	private final String eventKey;
	private final String entityType;
	private final Long entityId;
	private final String entityTitle;
	private final String actionType;
	private final String actorDisplayName;
	private final Long caseId;
	private final String caseName;
	private final String caseResponsibleAttorney;
	private final String caseResponsibleAttorneyColor;
	private final Boolean caseNonEngagementLetterSent;
	private final String casePrimaryStatusName;
	private final String casePrimaryStatusColor;
	private final String casePracticeAreaColor;
	private final BooleanProperty unread;

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner,
			NotificationTargetScope targetScope) {
		this(id, category, severity, title, message, createdAt, unread, showAsBanner, targetScope, null, null);
	}

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner,
			NotificationTargetScope targetScope,
			Long durableNotificationId,
			String eventKey) {
		this(id, category, severity, title, message, createdAt, unread, showAsBanner, targetScope, durableNotificationId, eventKey, null, null, null, null);
	}

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner,
			NotificationTargetScope targetScope,
			Long durableNotificationId,
			String eventKey,
			String entityType,
			Long entityId,
			String entityTitle) {
		this(id, category, severity, title, message, createdAt, unread, showAsBanner, targetScope, durableNotificationId, eventKey, entityType, entityId, entityTitle, null);
	}

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner,
			NotificationTargetScope targetScope,
			Long durableNotificationId,
			String eventKey,
			String entityType,
			Long entityId,
			String entityTitle,
			String actionType) {
		this(id, category, severity, title, message, createdAt, unread, showAsBanner, targetScope, durableNotificationId, eventKey, entityType, entityId, entityTitle, actionType, null, null);
	}

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner,
			NotificationTargetScope targetScope,
			Long durableNotificationId,
			String eventKey,
			String entityType,
			Long entityId,
			String entityTitle,
			String actionType,
			Long caseId,
			String caseName) {
		this(id, category, severity, title, message, createdAt, unread, showAsBanner, targetScope, durableNotificationId, eventKey, entityType, entityId, entityTitle, actionType, null, caseId, caseName, null, null, null);
	}

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner,
			NotificationTargetScope targetScope,
			Long durableNotificationId,
			String eventKey,
			String entityType,
			Long entityId,
			String entityTitle,
			String actionType,
			String actorDisplayName,
			Long caseId,
			String caseName,
			String caseResponsibleAttorney,
			String caseResponsibleAttorneyColor,
			Boolean caseNonEngagementLetterSent) {
		this(id, category, severity, title, message, createdAt, unread, showAsBanner, targetScope, durableNotificationId,
				eventKey, entityType, entityId, entityTitle, actionType, actorDisplayName, caseId, caseName,
				caseResponsibleAttorney, caseResponsibleAttorneyColor, caseNonEngagementLetterSent, null, null, null);
	}

	public AppNotification(
			String id,
			NotificationCategory category,
			NotificationSeverity severity,
			String title,
			String message,
			Instant createdAt,
			boolean unread,
			boolean showAsBanner,
			NotificationTargetScope targetScope,
			Long durableNotificationId,
			String eventKey,
			String entityType,
			Long entityId,
			String entityTitle,
			String actionType,
			String actorDisplayName,
			Long caseId,
			String caseName,
			String caseResponsibleAttorney,
			String caseResponsibleAttorneyColor,
			Boolean caseNonEngagementLetterSent,
			String casePrimaryStatusName,
			String casePrimaryStatusColor,
			String casePracticeAreaColor) {
		this.id = Objects.requireNonNull(id, "id");
		this.category = Objects.requireNonNull(category, "category");
		this.severity = Objects.requireNonNull(severity, "severity");
		this.title = Objects.requireNonNull(title, "title");
		this.message = Objects.requireNonNull(message, "message");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.unread = new SimpleBooleanProperty(unread);
		this.showAsBanner = showAsBanner;
		this.targetScope = Objects.requireNonNull(targetScope, "targetScope");
		this.durableNotificationId = durableNotificationId;
		this.eventKey = eventKey;
		this.entityType = entityType;
		this.entityId = entityId;
		this.entityTitle = entityTitle;
		this.actionType = actionType;
		this.actorDisplayName = actorDisplayName;
		this.caseId = caseId;
		this.caseName = caseName;
		this.caseResponsibleAttorney = caseResponsibleAttorney;
		this.caseResponsibleAttorneyColor = caseResponsibleAttorneyColor;
		this.caseNonEngagementLetterSent = caseNonEngagementLetterSent;
		this.casePrimaryStatusName = casePrimaryStatusName;
		this.casePrimaryStatusColor = casePrimaryStatusColor;
		this.casePracticeAreaColor = casePracticeAreaColor;
	}

	public String getId() {
		return id;
	}

	public NotificationCategory getCategory() {
		return category;
	}

	public NotificationSeverity getSeverity() {
		return severity;
	}

	public String getTitle() {
		return title;
	}

	public String getMessage() {
		return message;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}


	public NotificationTargetScope getTargetScope() {
		return targetScope;
	}

	public Long getDurableNotificationId() {
		return durableNotificationId;
	}

	public String getEventKey() {
		return eventKey;
	}

	public String getEntityType() {
		return entityType;
	}

	public Long getEntityId() {
		return entityId;
	}

	public String getEntityTitle() {
		return entityTitle;
	}

	public String getActionType() {
		return actionType;
	}

	public String getActorDisplayName() {
		return actorDisplayName;
	}

	public Long getCaseId() {
		return caseId;
	}

	public String getCaseName() {
		return caseName;
	}

	public String getCaseResponsibleAttorney() {
		return caseResponsibleAttorney;
	}

	public String getCaseResponsibleAttorneyColor() {
		return caseResponsibleAttorneyColor;
	}

	public Boolean getCaseNonEngagementLetterSent() {
		return caseNonEngagementLetterSent;
	}

	public String getCasePrimaryStatusName() {
		return casePrimaryStatusName;
	}

	public String getCasePrimaryStatusColor() {
		return casePrimaryStatusColor;
	}

	public String getCasePracticeAreaColor() {
		return casePracticeAreaColor;
	}

	public boolean isShowAsBanner() {
		return showAsBanner;
	}

	public boolean isUnread() {
		return unread.get();
	}

	public void setUnread(boolean unread) {
		this.unread.set(unread);
	}

	public BooleanProperty unreadProperty() {
		return unread;
	}
}
