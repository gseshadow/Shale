package com.shale.core.update;

/** Substantive editor/workflow categories which make cooperative shutdown unsafe while open. */
public enum MutationWorkflowType {
	NEW_INTAKE,
	CASE_EDIT,
	CONTACT_CREATE_EDIT,
	ORGANIZATION_CREATE_EDIT,
	TASK_CREATE_EDIT,
	CALENDAR_EVENT_CREATE_EDIT,
	OTHER_SUBSTANTIVE_MUTATION
}
