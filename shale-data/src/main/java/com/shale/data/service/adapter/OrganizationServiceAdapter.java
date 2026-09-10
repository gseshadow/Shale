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
	@Override public List<OrganizationTypeDefinition> listOrganizationTypesForAdministration(int tenant,int actor){return organizationGateway.listOrganizationTypesForAdministration(tenant,actor).stream().map(OrganizationServiceAdapter::definition).toList();}

	@Override
	public Optional<OrganizationTypeProfile> getOrganizationTypeProfile(int organizationId, int shaleClientId) {
		return Optional.ofNullable(organizationGateway.findOrganizationTypeProfile(organizationId, shaleClientId))
				.map(row -> new OrganizationTypeProfile(row.organizationId(), row.shaleClientId(),
						row.compatibilityOrganizationTypeId(), row.compatibilityConsistent(), row.assignments().stream()
								.map(a -> new AssignedOrganizationType(a.assignmentId(), a.organizationTypeId(), a.primary(),
										a.sortOrder(), definition(a.definition()), a.rowVer())).toList()));
	}

	@Override public Optional<OrganizationStructuredContactProfile> findStructuredContactProfile(int tenant,int organizationId){
		if(tenant<=0)throw new IllegalArgumentException("shaleClientId must be > 0");
		if(organizationId<=0)throw new IllegalArgumentException("organizationId must be > 0");
		return Optional.ofNullable(organizationGateway.findStructuredContactProfile(tenant,organizationId)).map(OrganizationServiceAdapter::structuredProfile);
	}
	private static OrganizationStructuredContactProfile structuredProfile(OrganizationDao.StructuredContactProfileRow r){
		var phones=r.phones().stream().map(x->new OrganizationPhoneNumber(x.id(),x.tenant(),x.organization(),phoneKind(x.kind()),x.kind(),x.display(),x.normalized(),x.extension(),x.primary(),x.sortOrder(),x.deleted(),lifecycle(x.lifecycle()),x.rowVer())).toList();
		var emails=r.emails().stream().map(x->new OrganizationEmailAddress(x.id(),x.tenant(),x.organization(),emailKind(x.kind()),x.kind(),x.email(),x.normalized(),x.primary(),x.sortOrder(),x.deleted(),lifecycle(x.lifecycle()),x.rowVer())).toList();
		var addresses=r.addresses().stream().map(x->new OrganizationAddress(x.id(),x.tenant(),x.organization(),addressKind(x.kind()),x.kind(),x.line1(),x.line2(),x.city(),x.state(),x.postal(),x.country(),x.legacyText(),x.primary(),x.sortOrder(),x.deleted(),lifecycle(x.lifecycle()),x.rowVer())).toList();
		var websites=r.websites().stream().map(x->new OrganizationWebsite(x.id(),x.tenant(),x.organization(),websiteKind(x.kind()),x.kind(),x.website(),x.primary(),x.sortOrder(),x.deleted(),lifecycle(x.lifecycle()),x.rowVer())).toList();
		var voice=preferred(phones.stream().filter(p->!p.fax()).toList());var fax=firstActive(phones.stream().filter(OrganizationPhoneNumber::fax).toList());var email=preferred(emails);var address=preferred(addresses);var website=preferred(websites);
		var legacy=r.legacy();var compatibility=new StructuredContactCompatibility(state(legacy.phone(),voice.map(OrganizationPhoneNumber::displayNumber).orElse(null),invalidPrimary(phones.stream().filter(p->!p.fax()).toList()),false),state(legacy.fax(),fax.map(OrganizationPhoneNumber::displayNumber).orElse(null),invalidPrimary(phones.stream().filter(OrganizationPhoneNumber::fax).toList()),false),state(legacy.email(),email.map(OrganizationEmailAddress::emailAddress).orElse(null),invalidPrimary(emails),true),addressState(legacy,address,invalidPrimary(addresses)),state(legacy.website(),website.map(OrganizationWebsite::website).orElse(null),invalidPrimary(websites),false));
		return new OrganizationStructuredContactProfile(r.organizationId(),r.shaleClientId(),phones,emails,addresses,websites,voice,fax,email,address,website,compatibility);
	}
	private static ContactLifecycle lifecycle(OrganizationDao.LifecycleRow x){return new ContactLifecycle(x.createdAt(),x.createdBy(),x.updatedAt(),x.updatedBy(),x.deletedAt(),x.deletedBy());}
	private static OrganizationPhoneKind phoneKind(String x){try{return OrganizationPhoneKind.valueOf(x);}catch(IllegalArgumentException e){return OrganizationPhoneKind.UNKNOWN;}}
	private static OrganizationEmailKind emailKind(String x){try{return OrganizationEmailKind.valueOf(x);}catch(IllegalArgumentException e){return OrganizationEmailKind.UNKNOWN;}}
	private static OrganizationAddressKind addressKind(String x){try{return OrganizationAddressKind.valueOf(x);}catch(IllegalArgumentException e){return OrganizationAddressKind.UNKNOWN;}}
	private static OrganizationWebsiteKind websiteKind(String x){try{return OrganizationWebsiteKind.valueOf(x);}catch(IllegalArgumentException e){return OrganizationWebsiteKind.UNKNOWN;}}
	private static <T> Optional<T> preferred(List<T> values){var found=values.stream().filter(OrganizationServiceAdapter::activePrimary).toList();return found.size()>1?Optional.empty():found.stream().findFirst().or(()->firstActive(values));}
	private static <T> Optional<T> firstActive(List<T> values){return values.stream().filter(x->!deleted(x)).findFirst();}
	private static boolean deleted(Object x){if(x instanceof OrganizationPhoneNumber v)return v.deleted();if(x instanceof OrganizationEmailAddress v)return v.deleted();if(x instanceof OrganizationAddress v)return v.deleted();return ((OrganizationWebsite)x).deleted();}
	private static boolean invalidPrimary(List<?> values){return values.stream().filter(OrganizationServiceAdapter::activePrimary).count()>1;}
	private static boolean activePrimary(Object x){if(x instanceof OrganizationPhoneNumber v)return !v.deleted()&&v.primary();if(x instanceof OrganizationEmailAddress v)return !v.deleted()&&v.primary();if(x instanceof OrganizationAddress v)return !v.deleted()&&v.primary();return !((OrganizationWebsite)x).deleted()&&((OrganizationWebsite)x).primary();}
	private static CompatibilityState state(String legacy,String structured,boolean invalid,boolean ignoreCase){if(invalid)return CompatibilityState.INVALID_PRIMARY;String a=trim(legacy),b=trim(structured);if(a==null&&b==null)return CompatibilityState.BOTH_ABSENT;if(a==null)return CompatibilityState.STRUCTURED_ONLY;if(b==null)return CompatibilityState.LEGACY_ONLY;return (ignoreCase?a.equalsIgnoreCase(b):a.equals(b))?CompatibilityState.MATCHING:CompatibilityState.DIFFERENT;}
	private static CompatibilityState addressState(OrganizationDao.LegacyContactRow l,Optional<OrganizationAddress> p,boolean invalid){if(invalid)return CompatibilityState.INVALID_PRIMARY;boolean legacy=java.util.stream.Stream.of(l.address1(),l.address2(),l.city(),l.state(),l.postalCode(),l.country()).anyMatch(v->trim(v)!=null);if(p.isEmpty())return legacy?CompatibilityState.LEGACY_ONLY:CompatibilityState.BOTH_ABSENT;var a=p.get();boolean structured=java.util.stream.Stream.of(a.addressLine1(),a.addressLine2(),a.city(),a.stateOrProvince(),a.postalCode(),a.country()).anyMatch(v->trim(v)!=null);if(!legacy)return structured?CompatibilityState.STRUCTURED_ONLY:CompatibilityState.BOTH_ABSENT;return same(l.address1(),a.addressLine1())&&same(l.address2(),a.addressLine2())&&same(l.city(),a.city())&&same(l.state(),a.stateOrProvince())&&same(l.postalCode(),a.postalCode())&&same(l.country(),a.country())?CompatibilityState.MATCHING:CompatibilityState.DIFFERENT;}
	private static boolean same(String a,String b){return Objects.equals(trim(a),trim(b));}
	private static String trim(String x){if(x==null)return null;String v=x.trim();return v.isEmpty()?null:v;}

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
	@Override public OrganizationAggregateResult createOrganizationAggregate(CreateOrganizationAggregateCommand c){OrganizationTypeProfile p=organizationGateway.createOrganizationAggregate(c);int id=p.organizationId();return authoritative(id,c.shaleClientId(),organizationGateway.findOrganizationRowVer(id,c.shaleClientId()),p,c.fields());}
	@Override public OrganizationAggregateResult updateOrganizationAggregate(UpdateOrganizationAggregateCommand c){var saved=organizationGateway.updateOrganizationAggregate(c);return authoritative(c.organizationId(),c.shaleClientId(),saved.organizationRowVer(),saved.typeProfile(),c.fields());}
	private OrganizationAggregateResult authoritative(int id,int tenant,byte[] rowVer,OrganizationTypeProfile types,OrganizationFields fallback){var row=organizationGateway.findStructuredContactProfile(tenant,id);if(row==null)return new OrganizationAggregateResult(id,rowVer,types,fallback,null);var l=row.legacy();var fields=new OrganizationFields(fallback.name(),l.phone(),l.fax(),l.email(),l.website(),l.address1(),l.address2(),l.city(),l.state(),l.postalCode(),l.country(),fallback.notes());return new OrganizationAggregateResult(id,rowVer,types,fields,structuredProfile(row));}
	@Override public int createOrganization(CreateOrganizationCommand c){Objects.requireNonNull(c,"command");var type=listEffectiveOrganizationTypes(c.shaleClientId()).stream().filter(t->c.organizationTypeId()==null||t.organizationTypeId()==c.organizationTypeId()).findFirst().orElseThrow(()->new IllegalStateException("No effective Organization Types are configured."));var fields=new OrganizationFields(c.name(),c.phone(),c.fax(),c.email(),c.website(),c.address1(),c.address2(),c.city(),c.state(),c.postalCode(),c.country(),c.notes());return createOrganizationAggregate(new CreateOrganizationAggregateCommand(c.shaleClientId(),c.actorUserId(),fields,List.of(new StagedOrganizationTypeAssignment(null,type.organizationTypeId(),true,0,null)))).organizationId();}

	@Override public boolean updateOrganization(UpdateOrganizationCommand c){Objects.requireNonNull(c,"command");var row=organizationGateway.findById(c.organizationId());if(row==null||row.getShaleClientId()==null||row.getShaleClientId()!=c.shaleClientId())return false;var profile=getOrganizationTypeProfile(c.organizationId(),c.shaleClientId()).orElseThrow(()->new IllegalStateException("Organization Type profile was not found."));if(!profile.compatibilityConsistent())throw new IllegalStateException("Organization primary type compatibility is inconsistent; reconcile before editing.");int requested=c.organizationTypeId()==null?profile.compatibilityOrganizationTypeId():c.organizationTypeId();if(c.organizationTypeId()!=null&&listEffectiveOrganizationTypes(c.shaleClientId()).stream().noneMatch(t->t.organizationTypeId()==requested))throw new IllegalArgumentException("Requested Organization Type is not effective and selectable.");var desired=new java.util.ArrayList<StagedOrganizationTypeAssignment>();boolean found=false;for(var a:profile.assignments()){boolean primary=a.organizationTypeId()==requested;found|=primary;desired.add(new StagedOrganizationTypeAssignment(a.assignmentId(),a.organizationTypeId(),primary,a.sortOrder(),a.rowVer()));}if(!found)desired.add(new StagedOrganizationTypeAssignment(null,requested,true,desired.size(),null));var fields=new OrganizationFields(c.name(),c.phone(),c.fax(),c.email(),c.website(),c.address1(),c.address2(),c.city(),c.state(),c.postalCode(),c.country(),c.notes());updateOrganizationAggregate(new UpdateOrganizationAggregateCommand(c.organizationId(),c.shaleClientId(),c.actorUserId(),c.expectedOrganizationRowVer()==null?organizationGateway.findOrganizationRowVer(c.organizationId(),c.shaleClientId()):c.expectedOrganizationRowVer(),fields,desired));return true;}


	interface OrganizationGateway {
		OrganizationDao.PagedResult<OrganizationDao.DirectoryOrganizationRow> findDirectoryPage(int page, int pageSize, String query);
		Organization findById(int organizationId);
		List<OrganizationDao.OrganizationTypeRow> findOrganizationTypes();
		List<OrganizationDao.OrganizationTypeDefinitionRow> listEffectiveOrganizationTypeDefinitions(int shaleClientId);
		default List<OrganizationDao.OrganizationTypeDefinitionRow> listOrganizationTypesForAdministration(int tenant,int actor){throw new UnsupportedOperationException("Organization Type administration is not supported");}
		OrganizationDao.OrganizationTypeProfileRow findOrganizationTypeProfile(int organizationId, int shaleClientId);
		OrganizationDao.StructuredContactProfileRow findStructuredContactProfile(int shaleClientId,int organizationId);
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
		default OrganizationTypeProfile createOrganizationAggregate(CreateOrganizationAggregateCommand c){throw new UnsupportedOperationException("Organization aggregate creation is not supported");}
		default OrganizationAggregateResult updateOrganizationAggregate(UpdateOrganizationAggregateCommand c){throw new UnsupportedOperationException("Organization aggregate update is not supported");}
		default byte[] findOrganizationRowVer(int organizationId,int tenant){throw new UnsupportedOperationException("Organization concurrency reads are not supported");}
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
		@Override public List<OrganizationDao.OrganizationTypeDefinitionRow> listOrganizationTypesForAdministration(int tenant,int actor){return dao.listOrganizationTypesForAdministration(tenant,actor);}
		@Override public OrganizationDao.OrganizationTypeProfileRow findOrganizationTypeProfile(int organizationId, int shaleClientId) { return dao.findOrganizationTypeProfile(organizationId, shaleClientId); }
		@Override public OrganizationDao.StructuredContactProfileRow findStructuredContactProfile(int tenant,int organizationId){return dao.findStructuredContactProfile(tenant,organizationId);}
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
		@Override public OrganizationTypeProfile createOrganizationAggregate(CreateOrganizationAggregateCommand c){return dao.createOrganizationAggregate(c);}
		@Override public OrganizationAggregateResult updateOrganizationAggregate(UpdateOrganizationAggregateCommand c){return dao.updateOrganizationAggregate(c);}
		@Override public byte[] findOrganizationRowVer(int organizationId,int tenant){return dao.findOrganizationRowVer(organizationId,tenant);}
	}

}
