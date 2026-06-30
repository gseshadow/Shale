package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.model.Organization;
import com.shale.core.service.OrganizationServicePort;
import com.shale.data.dao.OrganizationDao;

/**
 * Thin OrganizationServicePort adapter over existing OrganizationDao operations.
 */
public final class OrganizationServiceAdapter implements OrganizationServicePort {
	private final OrganizationDao organizationDao;

	public OrganizationServiceAdapter(OrganizationDao organizationDao) {
		this.organizationDao = Objects.requireNonNull(organizationDao, "organizationDao");
	}

	@Override
	public List<OrganizationSummary> searchOrganizations(int shaleClientId, String query, int limit) {
		int resolvedLimit = limit <= 0 ? 25 : limit;
		return organizationDao.findDirectoryPage(0, resolvedLimit, query).items().stream()
				.map(row -> new OrganizationSummary(
						row.id() == null ? 0 : row.id(),
						row.name(),
						row.organizationTypeId(),
						row.organizationTypeName(),
						row.phone(),
						row.email(),
						row.website(),
						row.city(),
						row.state()))
				.toList();
	}

	@Override
	public Optional<OrganizationDetail> getOrganizationDetail(int organizationId, int shaleClientId) {
		Organization organization = organizationDao.findById(organizationId);
		if (organization == null) {
			return Optional.empty();
		}
		List<RelatedCaseSummary> relatedCases = organizationDao.findRelatedCases(organizationId).stream()
				.map(row -> new RelatedCaseSummary(
						row.id(),
						row.name(),
						row.intakeDate(),
						row.statuteOfLimitationsDate(),
						row.responsibleAttorneyName(),
						row.partyRoleName(),
						row.side(),
						row.primary(),
						row.notes()))
				.toList();
		return Optional.of(new OrganizationDetail(
				organization.getId() == null ? 0 : organization.getId(),
				organization.getShaleClientId() == null ? shaleClientId : organization.getShaleClientId(),
				organization.getOrganizationTypeId(),
				organization.getOrganizationTypeName(),
				organization.getName(),
				organization.getPhone(),
				organization.getFax(),
				organization.getEmail(),
				organization.getWebsite(),
				organization.getAddress1(),
				organization.getAddress2(),
				organization.getCity(),
				organization.getState(),
				organization.getPostalCode(),
				organization.getCountry(),
				organization.getNotes(),
				relatedCases));
	}
	@Override
	public boolean updateOrganization(UpdateOrganizationCommand command) {
		Objects.requireNonNull(command, "command");
		Organization current = organizationDao.findById(command.organizationId());
		if (current == null) {
			return false;
		}
		Organization updated = Organization.builder()
				.id(command.organizationId())
				.shaleClientId(command.shaleClientId())
				.organizationTypeId(current.getOrganizationTypeId())
				.organizationTypeName(current.getOrganizationTypeName())
				.name(command.name())
				.phone(command.phone())
				.fax(command.fax())
				.email(command.email())
				.website(command.website())
				.address1(command.address1())
				.address2(command.address2())
				.city(command.city())
				.state(command.state())
				.postalCode(command.postalCode())
				.country(command.country())
				.notes(command.notes())
				.deleted(current.isDeleted())
				.createdAt(current.getCreatedAt())
				.updatedAt(current.getUpdatedAt())
				.build();
		organizationDao.update(updated);
		return true;
	}

}
