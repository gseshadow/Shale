package com.shale.data.service.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.model.Organization;
import com.shale.core.service.OrganizationServicePort;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.data.dao.OrganizationDao;

/**
 * Thin OrganizationServicePort adapter over existing OrganizationDao operations.
 */
public final class OrganizationServiceAdapter implements OrganizationServicePort {
	private final OrganizationGateway organizationGateway;
	private final RelatedCasesGateway relatedCasesGateway;

	public OrganizationServiceAdapter(OrganizationDao organizationDao, CaseSummaryDao caseSummaryDao) {
		this(new DaoOrganizationGateway(organizationDao), caseSummaryDao::listActiveRelatedToOrganization);
	}

	OrganizationServiceAdapter(OrganizationGateway organizationGateway, RelatedCasesGateway relatedCasesGateway) {
		this.organizationGateway = Objects.requireNonNull(organizationGateway, "organizationGateway");
		this.relatedCasesGateway = Objects.requireNonNull(relatedCasesGateway, "relatedCasesGateway");
	}

	@Override
	public List<OrganizationSummary> searchOrganizations(int shaleClientId, String query, int limit) {
		int resolvedLimit = limit <= 0 ? 25 : limit;
		return organizationGateway.findDirectoryPage(0, resolvedLimit, query).items().stream()
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
		Organization organization = organizationGateway.findById(organizationId);
		if (organization == null || organization.getShaleClientId() == null
				|| organization.getShaleClientId() != shaleClientId || organization.isDeleted()) {
			return Optional.empty();
		}
		List<RelatedCaseSummary> relatedCases = relatedCasesGateway.listActiveRelatedToOrganization(shaleClientId, organizationId).stream()
				.map(row -> new RelatedCaseSummary(
						row.summary().caseId(),
						row.summary().caseName(),
						row.intakeDate(),
						row.statuteOfLimitationsDate(),
						row.summary().responsibleAttorneyName(),
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
	public List<OrganizationTypeDefinition> listEffectiveOrganizationTypes(int shaleClientId) {
		return organizationGateway.listEffectiveOrganizationTypeDefinitions(shaleClientId).stream()
				.map(OrganizationServiceAdapter::definition).toList();
	}

	@Override
	public Optional<OrganizationTypeProfile> getOrganizationTypeProfile(int organizationId, int shaleClientId) {
		return Optional.ofNullable(organizationGateway.findOrganizationTypeProfile(organizationId, shaleClientId))
				.map(row -> new OrganizationTypeProfile(row.organizationId(), row.shaleClientId(),
						row.compatibilityOrganizationTypeId(), row.compatibilityConsistent(), row.assignments().stream()
								.map(a -> new AssignedOrganizationType(a.assignmentId(), a.organizationTypeId(), a.primary(),
										a.sortOrder(), definition(a.definition()), a.rowVer())).toList()));
	}

	private static OrganizationTypeDefinition definition(OrganizationDao.OrganizationTypeDefinitionRow row) {
		return new OrganizationTypeDefinition(row.organizationTypeId(), row.shaleClientId(), row.systemKey(),
				row.name(), row.description(), row.color(), row.sortOrder(), row.active(), row.deleted(),
				row.shaleClientId() == null ? OrganizationTypeOrigin.GLOBAL : OrganizationTypeOrigin.TENANT,
				row.rowVer());
	}
	@Override public OrganizationTypeMutationResult createOrganizationType(CreateOrganizationTypeCommand c){return organizationGateway.createOrganizationType(c);}
	@Override public OrganizationTypeMutationResult updateOrganizationType(UpdateOrganizationTypeCommand c){return organizationGateway.updateOrganizationType(c);}
	@Override public OrganizationTypeMutationResult setOrganizationTypeActive(OrganizationTypeLifecycleCommand c){return organizationGateway.setOrganizationTypeActive(c);}
	@Override public OrganizationTypeMutationResult removeOrganizationType(OrganizationTypeLifecycleCommand c){return organizationGateway.removeOrganizationType(c);}
	@Override public OrganizationTypeMutationResult restoreOrganizationType(OrganizationTypeLifecycleCommand c){return organizationGateway.restoreOrganizationType(c);}
	@Override public OrganizationTypeAssignmentMutationResult assignOrganizationType(AssignOrganizationTypeCommand c){return organizationGateway.assignOrganizationType(c);}
	@Override public OrganizationTypeAssignmentMutationResult removeOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand c){return organizationGateway.removeOrganizationTypeAssignment(c);}
	@Override public OrganizationTypeAssignmentMutationResult restoreOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand c){return organizationGateway.restoreOrganizationTypeAssignment(c);}
	@Override public OrganizationTypeAssignmentMutationResult setPrimaryOrganizationType(SetPrimaryOrganizationTypeCommand c){return organizationGateway.setPrimaryOrganizationType(c);}
	@Override public OrganizationTypeAssignmentMutationResult replaceAndRemovePrimaryOrganizationType(ReplaceAndRemovePrimaryOrganizationTypeCommand c){return organizationGateway.replaceAndRemovePrimaryOrganizationType(c);}
	@Override public List<OrganizationTypeAssignmentMutationResult> reorderOrganizationTypeAssignments(ReorderOrganizationTypeAssignmentsCommand c){return organizationGateway.reorderOrganizationTypeAssignments(c);}
	@Override
	public int createOrganization(CreateOrganizationCommand command) {
		Objects.requireNonNull(command, "command");
		OrganizationDao.OrganizationTypeRow organizationType = organizationGateway.findOrganizationTypes().stream()
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No organization types are configured."));
		return organizationGateway.create(new OrganizationDao.OrganizationCreateRequest(
				command.shaleClientId(),
				organizationType.organizationTypeId(),
				command.name(),
				command.phone(),
				command.fax(),
				command.email(),
				command.website(),
				command.address1(),
				command.address2(),
				command.city(),
				command.state(),
				command.postalCode(),
				command.country(),
				command.notes()));
	}

	@Override
	public boolean updateOrganization(UpdateOrganizationCommand command) {
		Objects.requireNonNull(command, "command");
		Organization current = organizationGateway.findById(command.organizationId());
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
		organizationGateway.update(updated);
		return true;
	}


	interface OrganizationGateway {
		OrganizationDao.PagedResult<OrganizationDao.DirectoryOrganizationRow> findDirectoryPage(int page, int pageSize, String query);
		Organization findById(int organizationId);
		List<OrganizationDao.OrganizationTypeRow> findOrganizationTypes();
		List<OrganizationDao.OrganizationTypeDefinitionRow> listEffectiveOrganizationTypeDefinitions(int shaleClientId);
		OrganizationDao.OrganizationTypeProfileRow findOrganizationTypeProfile(int organizationId, int shaleClientId);
		int create(OrganizationDao.OrganizationCreateRequest request);
		void update(Organization organization);
		default OrganizationTypeMutationResult createOrganizationType(CreateOrganizationTypeCommand c){throw new UnsupportedOperationException("Organization Type creation is not supported");}
		default OrganizationTypeMutationResult updateOrganizationType(UpdateOrganizationTypeCommand c){throw new UnsupportedOperationException("Organization Type update is not supported");}
		default OrganizationTypeMutationResult setOrganizationTypeActive(OrganizationTypeLifecycleCommand c){throw new UnsupportedOperationException("Organization Type lifecycle is not supported");}
		default OrganizationTypeMutationResult removeOrganizationType(OrganizationTypeLifecycleCommand c){throw new UnsupportedOperationException("Organization Type removal is not supported");}
		default OrganizationTypeMutationResult restoreOrganizationType(OrganizationTypeLifecycleCommand c){throw new UnsupportedOperationException("Organization Type restoration is not supported");}
		default OrganizationTypeAssignmentMutationResult assignOrganizationType(AssignOrganizationTypeCommand c){throw new UnsupportedOperationException("Organization Type assignment is not supported");}
		default OrganizationTypeAssignmentMutationResult removeOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand c){throw new UnsupportedOperationException("Organization Type assignment removal is not supported");}
		default OrganizationTypeAssignmentMutationResult restoreOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand c){throw new UnsupportedOperationException("Organization Type assignment restoration is not supported");}
		default OrganizationTypeAssignmentMutationResult setPrimaryOrganizationType(SetPrimaryOrganizationTypeCommand c){throw new UnsupportedOperationException("Organization Type primary selection is not supported");}
		default OrganizationTypeAssignmentMutationResult replaceAndRemovePrimaryOrganizationType(ReplaceAndRemovePrimaryOrganizationTypeCommand c){throw new UnsupportedOperationException("Organization Type replacement is not supported");}
		default List<OrganizationTypeAssignmentMutationResult> reorderOrganizationTypeAssignments(ReorderOrganizationTypeAssignmentsCommand c){throw new UnsupportedOperationException("Organization Type assignment ordering is not supported");}
	}

	@FunctionalInterface
	interface RelatedCasesGateway {
		List<CaseSummaryDao.RelatedCaseRow> listActiveRelatedToOrganization(int shaleClientId, int organizationId);
	}

	private record DaoOrganizationGateway(OrganizationDao dao) implements OrganizationGateway {
		private DaoOrganizationGateway { Objects.requireNonNull(dao, "dao"); }
		@Override public OrganizationDao.PagedResult<OrganizationDao.DirectoryOrganizationRow> findDirectoryPage(int page, int pageSize, String query) { return dao.findDirectoryPage(page, pageSize, query); }
		@Override public Organization findById(int organizationId) { return dao.findById(organizationId); }
		@Override public List<OrganizationDao.OrganizationTypeRow> findOrganizationTypes() { return dao.findOrganizationTypes(); }
		@Override public List<OrganizationDao.OrganizationTypeDefinitionRow> listEffectiveOrganizationTypeDefinitions(int shaleClientId) { return dao.listEffectiveOrganizationTypeDefinitions(shaleClientId); }
		@Override public OrganizationDao.OrganizationTypeProfileRow findOrganizationTypeProfile(int organizationId, int shaleClientId) { return dao.findOrganizationTypeProfile(organizationId, shaleClientId); }
		@Override public int create(OrganizationDao.OrganizationCreateRequest request) { return dao.create(request); }
		@Override public void update(Organization organization) { dao.update(organization); }
		@Override public OrganizationTypeMutationResult createOrganizationType(CreateOrganizationTypeCommand c){return dao.createOrganizationType(c);}
		@Override public OrganizationTypeMutationResult updateOrganizationType(UpdateOrganizationTypeCommand c){return dao.updateOrganizationType(c);}
		@Override public OrganizationTypeMutationResult setOrganizationTypeActive(OrganizationTypeLifecycleCommand c){return dao.setOrganizationTypeActive(c);}
		@Override public OrganizationTypeMutationResult removeOrganizationType(OrganizationTypeLifecycleCommand c){return dao.removeOrganizationType(c);}
		@Override public OrganizationTypeMutationResult restoreOrganizationType(OrganizationTypeLifecycleCommand c){return dao.restoreOrganizationType(c);}
		@Override public OrganizationTypeAssignmentMutationResult assignOrganizationType(AssignOrganizationTypeCommand c){return dao.assignOrganizationType(c);}
		@Override public OrganizationTypeAssignmentMutationResult removeOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand c){return dao.removeOrganizationTypeAssignment(c);}
		@Override public OrganizationTypeAssignmentMutationResult restoreOrganizationTypeAssignment(OrganizationTypeAssignmentLifecycleCommand c){return dao.restoreOrganizationTypeAssignment(c);}
		@Override public OrganizationTypeAssignmentMutationResult setPrimaryOrganizationType(SetPrimaryOrganizationTypeCommand c){return dao.setPrimaryOrganizationType(c);}
		@Override public OrganizationTypeAssignmentMutationResult replaceAndRemovePrimaryOrganizationType(ReplaceAndRemovePrimaryOrganizationTypeCommand c){return dao.replaceAndRemovePrimaryOrganizationType(c);}
		@Override public List<OrganizationTypeAssignmentMutationResult> reorderOrganizationTypeAssignments(ReorderOrganizationTypeAssignmentsCommand c){return dao.reorderOrganizationTypeAssignments(c);}
	}

}
