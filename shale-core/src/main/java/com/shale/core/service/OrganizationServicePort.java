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
}
