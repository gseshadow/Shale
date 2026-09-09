package com.shale.core.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Shared organization application boundary for server/web read workflows.
 */
public interface OrganizationServicePort {

	List<OrganizationSummary> searchOrganizations(int shaleClientId, String query, int limit);

	Optional<OrganizationDetail> getOrganizationDetail(int organizationId, int shaleClientId);

	List<OrganizationTypeDefinition> listEffectiveOrganizationTypes(int shaleClientId);
	default List<OrganizationTypeDefinition> listOrganizationTypesForAdministration(int shaleClientId, int actorUserId) {
		throw new UnsupportedOperationException("Organization Type administration is not supported");
	}

	Optional<OrganizationTypeProfile> getOrganizationTypeProfile(int organizationId, int shaleClientId);

	OrganizationTypeMutationResult createOrganizationType(CreateOrganizationTypeCommand command);
	OrganizationTypeMutationResult updateOrganizationType(UpdateOrganizationTypeCommand command);
	OrganizationTypeMutationResult setOrganizationTypeActive(OrganizationTypeLifecycleCommand command);
	OrganizationTypeMutationResult removeOrganizationType(OrganizationTypeLifecycleCommand command);
	OrganizationTypeMutationResult restoreOrganizationType(OrganizationTypeLifecycleCommand command);
	OrganizationTypeAssignmentMutationResult assignOrganizationType(AssignOrganizationTypeCommand command);
	OrganizationTypeAssignmentMutationResult removeOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand command);
	OrganizationTypeAssignmentMutationResult restoreOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand command);
	OrganizationTypeAssignmentMutationResult setPrimaryOrganizationType(SetPrimaryOrganizationTypeCommand command);
	OrganizationTypeAssignmentMutationResult replaceAndRemovePrimaryOrganizationType(ReplaceAndRemovePrimaryOrganizationTypeCommand command);
	List<OrganizationTypeAssignmentMutationResult> reorderOrganizationTypeAssignments(ReorderOrganizationTypeAssignmentsCommand command);

	int createOrganization(CreateOrganizationCommand command);

	boolean updateOrganization(UpdateOrganizationCommand command);

	record OrganizationSummary(
			int id,
			String name,
			Integer organizationTypeId,
			String organizationTypeName,
			String phone,
			String email,
			String website,
			String city,
			String state) {
	}

	record OrganizationDetail(
			int id,
			int shaleClientId,
			Integer organizationTypeId,
			String organizationTypeName,
			String name,
			String phone,
			String fax,
			String email,
			String website,
			String address1,
			String address2,
			String city,
			String state,
			String postalCode,
			String country,
			String notes,
			List<RelatedCaseSummary> relatedCases) {
	}

	enum OrganizationTypeOrigin { GLOBAL, TENANT }

	/** Selector-ready definition after resolving the tenant/global SystemKey overlay. */
	record OrganizationTypeDefinition(
			int organizationTypeId,
			Integer shaleClientId,
			String systemKey,
			String name,
			String description,
			String color,
			int sortOrder,
			boolean active,
			boolean deleted,
			OrganizationTypeOrigin origin,
			byte[] rowVer) {
		public OrganizationTypeDefinition { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}

	/** An active assignment joined to its authoritative stored definition identity. */
	record AssignedOrganizationType(
			long assignmentId,
			int organizationTypeId,
			boolean primary,
			int sortOrder,
			OrganizationTypeDefinition definition,
			byte[] rowVer) {
		public AssignedOrganizationType { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
		public boolean historical() { return !definition.active() || definition.deleted(); }
	}

	/** Read-only assignment aggregate; compatibilityConsistent never repairs either source. */
	record OrganizationTypeProfile(
			int organizationId,
			int shaleClientId,
			Integer compatibilityOrganizationTypeId,
			boolean compatibilityConsistent,
			List<AssignedOrganizationType> assignments) {
		public OrganizationTypeProfile { assignments = List.copyOf(assignments); }
	}

	record CreateOrganizationTypeCommand(int shaleClientId, int actorUserId, String systemKey,
			Integer globalOrganizationTypeId, String name, String description, String color,
			int sortOrder, boolean active) { }
	record UpdateOrganizationTypeCommand(int organizationTypeId, int shaleClientId, int actorUserId,
			String name, String description, String color, int sortOrder, byte[] expectedRowVer) {
		public UpdateOrganizationTypeCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}
	record OrganizationTypeLifecycleCommand(int organizationTypeId, int shaleClientId, int actorUserId,
			boolean active, byte[] expectedRowVer) {
		public OrganizationTypeLifecycleCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}
	record OrganizationTypeMutationResult(int organizationTypeId, String systemKey, boolean active,
			boolean deleted, byte[] rowVer) {
		public OrganizationTypeMutationResult { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}
	record AssignOrganizationTypeCommand(int shaleClientId, int actorUserId, int organizationId,
			int organizationTypeId) { }
	record OrganizationTypeAssignmentLifecycleCommand(int shaleClientId, int actorUserId,
			int organizationId, long assignmentId, byte[] expectedRowVer) {
		public OrganizationTypeAssignmentLifecycleCommand { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}
	record SetPrimaryOrganizationTypeCommand(int shaleClientId, int actorUserId, int organizationId,
			long assignmentId, byte[] expectedAssignmentRowVer) {
		public SetPrimaryOrganizationTypeCommand { expectedAssignmentRowVer = copyRowVer(expectedAssignmentRowVer); }
		@Override public byte[] expectedAssignmentRowVer() { return copyRowVer(expectedAssignmentRowVer); }
	}
	record ReplaceAndRemovePrimaryOrganizationTypeCommand(int shaleClientId, int actorUserId,
			int organizationId, long removedAssignmentId, byte[] removedExpectedRowVer,
			long replacementAssignmentId, byte[] replacementExpectedRowVer) {
		public ReplaceAndRemovePrimaryOrganizationTypeCommand {
			removedExpectedRowVer = copyRowVer(removedExpectedRowVer);
			replacementExpectedRowVer = copyRowVer(replacementExpectedRowVer);
		}
		@Override public byte[] removedExpectedRowVer() { return copyRowVer(removedExpectedRowVer); }
		@Override public byte[] replacementExpectedRowVer() { return copyRowVer(replacementExpectedRowVer); }
	}
	record OrganizationTypeAssignmentOrder(long assignmentId, byte[] expectedRowVer) {
		public OrganizationTypeAssignmentOrder { expectedRowVer = copyRowVer(expectedRowVer); }
		@Override public byte[] expectedRowVer() { return copyRowVer(expectedRowVer); }
	}
	record ReorderOrganizationTypeAssignmentsCommand(int shaleClientId, int actorUserId,
			int organizationId, List<OrganizationTypeAssignmentOrder> orderedAssignments) {
		public ReorderOrganizationTypeAssignmentsCommand { orderedAssignments = List.copyOf(orderedAssignments); }
	}
	record OrganizationTypeAssignmentMutationResult(long assignmentId, int organizationId,
			int organizationTypeId, boolean primary, int sortOrder, boolean deleted, byte[] rowVer) {
		public OrganizationTypeAssignmentMutationResult { rowVer = copyRowVer(rowVer); }
		@Override public byte[] rowVer() { return copyRowVer(rowVer); }
	}

	record CreateOrganizationCommand(
			int shaleClientId,
			int actorUserId,
			String name,
			String phone,
			String fax,
			String email,
			String website,
			String address1,
			String address2,
			String city,
			String state,
			String postalCode,
			String country,
			String notes) {
	}

	record UpdateOrganizationCommand(
			int organizationId,
			int shaleClientId,
			int actorUserId,
			String name,
			String phone,
			String fax,
			String email,
			String website,
			String address1,
			String address2,
			String city,
			String state,
			String postalCode,
			String country,
			String notes) {
	}

	record RelatedCaseSummary(
			long id,
			String name,
			LocalDate intakeDate,
			LocalDate statuteOfLimitationsDate,
			String responsibleAttorneyName,
			String partyRoleName,
			String side,
			boolean primary,
			String notes) {
	}

	private static byte[] copyRowVer(byte[] value) { return value == null ? null : value.clone(); }
}
