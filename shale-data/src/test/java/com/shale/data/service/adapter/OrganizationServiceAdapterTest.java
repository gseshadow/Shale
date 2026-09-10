package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseSummaryProjection;
import com.shale.core.model.Organization;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.data.dao.OrganizationDao;

final class OrganizationServiceAdapterTest {
	@Test void structuredReadDelegatesOnceMapsHistoryUnknownKindsAndCompatibility(){
		var g=new FakeOrganizations();byte[] rv={1,2};g.structuredProfile=new OrganizationDao.StructuredContactProfileRow(7,41,
				new OrganizationDao.LegacyContactRow(" 555 ","999","USER@example.test","https://x", "One",null,"Town","NM","1","US"),
				List.of(phoneRow(2,"ALIEN","old",false,1,true,new byte[]{3}),phoneRow(1,"WORK","555",true,0,false,rv),phoneRow(3,"FAX","999",true,0,false,new byte[]{4})),
				List.of(new OrganizationDao.EmailRow(4,41,7,"WORK","user@example.test","user@example.test",true,0,false,life(),new byte[]{5})),
				List.of(new OrganizationDao.AddressRow(5,41,7,"WORK","One",null,"Town","NM","1","US",null,true,0,false,life(),new byte[]{6})),
				List.of(new OrganizationDao.WebsiteRow(6,41,7,"MAIN","https://x",true,0,false,life(),new byte[]{7})));
		var profile=new OrganizationServiceAdapter(g,(t,o)->List.of()).findStructuredContactProfile(41,7).orElseThrow();rv[0]=9;
		assertEquals(1,g.structuredCalls);assertEquals(1,profile.primaryPhone().orElseThrow().id());assertEquals(3,profile.primaryFax().orElseThrow().id());
		assertEquals(com.shale.core.service.OrganizationServicePort.OrganizationPhoneKind.UNKNOWN,profile.phones().get(2).kind());
		assertEquals("ALIEN",profile.phones().get(2).rawKind());assertTrue(profile.compatibilityConsistent());assertArrayEquals(new byte[]{1,2},profile.primaryPhone().orElseThrow().rowVer());
	}
	@Test void structuredCompatibilityReportsMissingDifferentAndDuplicatePrimariesWithoutSelectingArbitrarily(){
		var g=new FakeOrganizations();g.structuredProfile=new OrganizationDao.StructuredContactProfileRow(7,41,new OrganizationDao.LegacyContactRow("legacy",null,null,null,null,null,null,null,null,null),List.of(phoneRow(1,"WORK","one",true,0,false,new byte[]{1}),phoneRow(2,"HOME","two",true,1,false,new byte[]{2})),List.of(),List.of(),List.of());
		var profile=new OrganizationServiceAdapter(g,(t,o)->List.of()).findStructuredContactProfile(41,7).orElseThrow();
		assertTrue(profile.primaryPhone().isEmpty());assertEquals(com.shale.core.service.OrganizationServicePort.CompatibilityState.INVALID_PRIMARY,profile.compatibility().phone());assertFalse(profile.compatibilityConsistent());
	}
	@Test void structuredReadValidatesIdsAndMissingRemainsEmpty(){var g=new FakeOrganizations();var service=new OrganizationServiceAdapter(g,(t,o)->List.of());assertThrows(IllegalArgumentException.class,()->service.findStructuredContactProfile(0,1));assertThrows(IllegalArgumentException.class,()->service.findStructuredContactProfile(1,0));assertTrue(service.findStructuredContactProfile(41,404).isEmpty());assertEquals(1,g.structuredCalls);}
	private static OrganizationDao.PhoneRow phoneRow(long id,String kind,String value,boolean primary,int order,boolean deleted,byte[] rv){return new OrganizationDao.PhoneRow(id,41,7,kind,value,null,null,primary,order,deleted,life(),rv);}
	private static OrganizationDao.LifecycleRow life(){return new OrganizationDao.LifecycleRow(Instant.EPOCH,null,null,null,null,null);}
	@Test void administrationReadDelegatesWithoutLosingLifecycleRows(){var gateway=new FakeOrganizations();gateway.administrationTypes=List.of(new OrganizationDao.OrganizationTypeDefinitionRow(20,41,"removed","Removed","history","#6C757D",2,false,true,new byte[]{9}));var service=new OrganizationServiceAdapter(gateway,(id,tenant)->List.of());var rows=service.listOrganizationTypesForAdministration(41,7);assertEquals(1,gateway.administrationCalls);assertTrue(rows.get(0).deleted());}
	@Test void phaseOneCMutationsDelegateToTheAuthoritativeOrganizationGateway() {
		FakeOrganizations organizations = new FakeOrganizations(organization(7, 41));
		OrganizationServiceAdapter adapter = new OrganizationServiceAdapter(organizations, (tenant, organization) -> List.of());
		var command = new com.shale.core.service.OrganizationServicePort.AssignOrganizationTypeCommand(41, 9, 7, 12);
		var result = adapter.assignOrganizationType(command);
		assertSame(command, organizations.assignmentCommand);
		assertEquals(101L, result.assignmentId());
	}
	@Test void phaseTwoBAggregateCreateDelegatesOnceAndReturnsAuthoritativeProfileAndRowVersion(){
		FakeOrganizations organizations=new FakeOrganizations(organization(7,41));var service=new OrganizationServiceAdapter(organizations,(t,o)->List.of());
		var command=new com.shale.core.service.OrganizationServicePort.CreateOrganizationAggregateCommand(41,9,new com.shale.core.service.OrganizationServicePort.OrganizationFields("Org",null,null,null,null,null,null,null,null,null,null,null),List.of(new com.shale.core.service.OrganizationServicePort.StagedOrganizationTypeAssignment(null,12,true,0,null)));
		var result=service.createOrganizationAggregate(command);assertSame(command,organizations.aggregateCreate);assertEquals(7,result.organizationId());assertArrayEquals(new byte[]{8},result.organizationRowVer());assertTrue(result.typeProfile().compatibilityConsistent());
	}
	@Test void phaseTwoBAggregateUpdateReturnsTheTransactionCapturedRowVersionWithoutASecondRead(){
		FakeOrganizations organizations=new FakeOrganizations(organization(7,41));var service=new OrganizationServiceAdapter(organizations,(t,o)->List.of());
		var command=new com.shale.core.service.OrganizationServicePort.UpdateOrganizationAggregateCommand(7,41,9,new byte[]{7},new com.shale.core.service.OrganizationServicePort.OrganizationFields("Org",null,null,null,null,null,null,null,null,null,null,null),List.of(new com.shale.core.service.OrganizationServicePort.StagedOrganizationTypeAssignment(101L,13,true,0,new byte[]{3})));
		var result=service.updateOrganizationAggregate(command);assertSame(command,organizations.aggregateUpdate);assertArrayEquals(new byte[]{8},result.organizationRowVer());assertEquals(0,organizations.rowVerReads,"the adapter must not replace the transaction-captured token with a later read");
	}
	@Test void legacyCreateMapsSelectedTypeToOnePrimaryAggregateAssignment(){FakeOrganizations g=new FakeOrganizations(organization(7,41));g.effectiveTypes=List.of(new OrganizationDao.OrganizationTypeDefinitionRow(12,41,"provider","Provider",null,"#123456",0,true,false,new byte[]{1}));var service=new OrganizationServiceAdapter(g,(t,o)->List.of());int id=service.createOrganization(new com.shale.core.service.OrganizationServicePort.CreateOrganizationCommand(41,9,"Org",null,null,null,null,null,null,null,null,null,null,null,12));assertEquals(7,id);assertEquals(1,g.aggregateCreate.assignments().size());assertTrue(g.aggregateCreate.assignments().get(0).primary());assertEquals(12,g.aggregateCreate.assignments().get(0).organizationTypeId());}
	@Test void typeReadsDelegateAndPreserveLifecycleIdentityOrderingAndDefensiveRowVersions() {
		FakeOrganizations organizations = new FakeOrganizations(organization(7, 41));
		byte[] definitionRowVer = {1, 2};
		byte[] assignmentRowVer = {3, 4};
		var historical = new OrganizationDao.OrganizationTypeDefinitionRow(12, null, "hospital", "Hospital",
				"Historical", "#123456", 5, false, true, definitionRowVer);
		organizations.effectiveTypes = List.of(new OrganizationDao.OrganizationTypeDefinitionRow(13, 41,
				"provider", "Provider", null, "#ABCDEF", 1, true, false, new byte[] {5, 6}));
		organizations.profile = new OrganizationDao.OrganizationTypeProfileRow(7, 41, 12, true,
				List.of(new OrganizationDao.AssignedOrganizationTypeRow(101L, 12, true, 2, historical, assignmentRowVer)));

		OrganizationServiceAdapter adapter = new OrganizationServiceAdapter(organizations, (tenant, organization) -> List.of());
		var definitions = adapter.listEffectiveOrganizationTypes(41);
		var profile = adapter.getOrganizationTypeProfile(7, 41).orElseThrow();
		definitionRowVer[0] = 9;
		assignmentRowVer[0] = 9;

		assertEquals(1, organizations.effectiveCalls, "effective definitions use one gateway call");
		assertEquals(1, organizations.profileCalls, "the profile uses one aggregate gateway call");
		assertEquals(13, definitions.get(0).organizationTypeId());
		assertEquals(com.shale.core.service.OrganizationServicePort.OrganizationTypeOrigin.TENANT,
				definitions.get(0).origin());
		assertTrue(profile.compatibilityConsistent());
		assertEquals(12, profile.assignments().get(0).definition().organizationTypeId());
		assertTrue(profile.assignments().get(0).historical());
		assertArrayEquals(new byte[] {1, 2}, profile.assignments().get(0).definition().rowVer());
		assertArrayEquals(new byte[] {3, 4}, profile.assignments().get(0).rowVer());
		byte[] exposed = profile.assignments().get(0).rowVer();
		exposed[0] = 8;
		assertArrayEquals(new byte[] {3, 4}, profile.assignments().get(0).rowVer());
	}

	@Test void missingTypeProfileIsExposedAsEmptyWithoutCompatibilityRepair() {
		FakeOrganizations organizations = new FakeOrganizations(null);
		OrganizationServiceAdapter adapter = new OrganizationServiceAdapter(organizations, (tenant, organization) -> List.of());
		assertEquals(Optional.empty(), adapter.getOrganizationTypeProfile(404, 41));
		assertEquals(1, organizations.profileCalls);
	}
	@Test void detailDelegatesOnceToAuthoritativeSetProjectionAndPreservesRowsAndMetadata() {
		FakeOrganizations organizations = new FakeOrganizations(organization(7, 41));
		var summary = new CaseSummaryProjection(91,41,"C-91","Alpha",3,"open","OPEN","Open","#fff",4,"PI",
				12,"Responsible Lawyer","#123",13,"Assistant","#456",LocalDateTime.MIN,LocalDateTime.MAX,false);
		var first = new CaseSummaryDao.RelatedCaseRow(501,8,summary,LocalDate.of(2026,1,2),LocalDate.of(2027,3,4),
				null,"#abc",true,"Client","Plaintiff",true,"first");
		var second = new CaseSummaryDao.RelatedCaseRow(502,9,summary,null,null,null,"#abc",false,"Witness","Plaintiff",false,"second");
		RecordingRelatedCases related = new RecordingRelatedCases(List.of(first, second));
		var detail = new OrganizationServiceAdapter(organizations, related).getOrganizationDetail(7,41).orElseThrow();

		assertEquals(1, related.calls);
		assertEquals(41, related.tenantId);
		assertEquals(7, related.organizationId);
		assertEquals(2, detail.relatedCases().size(), "one result is retained for each CaseParties relationship");
		assertEquals("Client", detail.relatedCases().get(0).partyRoleName());
		assertTrue(detail.relatedCases().get(0).primary());
		assertEquals("Responsible Lawyer", detail.relatedCases().get(0).responsibleAttorneyName());
		assertEquals(LocalDate.of(2026,1,2), detail.relatedCases().get(0).intakeDate());
		assertEquals(LocalDate.of(2027,3,4), detail.relatedCases().get(0).statuteOfLimitationsDate());
		assertNull(detail.relatedCases().get(1).intakeDate());
		assertNull(detail.relatedCases().get(1).statuteOfLimitationsDate());
	}

	@Test void inaccessibleOrganizationReturnsEmptyWithoutLoadingRelatedCases() {
		RecordingRelatedCases related = new RecordingRelatedCases(List.of());
		assertTrue(new OrganizationServiceAdapter(new FakeOrganizations(null), related)
				.getOrganizationDetail(7,99).isEmpty());
		assertEquals(0, related.calls);
		assertTrue(new OrganizationServiceAdapter(new FakeOrganizations(organization(7, 41)), related)
				.getOrganizationDetail(7,99).isEmpty(), "tenant mismatch is indistinguishable from not found");
		assertEquals(0, related.calls);
	}

	private static Organization organization(int id,int tenant) { return Organization.builder().id(id).shaleClientId(tenant)
			.name("Org").deleted(false).build(); }

	private static final class RecordingRelatedCases implements OrganizationServiceAdapter.RelatedCasesGateway {
		private final List<CaseSummaryDao.RelatedCaseRow> rows; int calls,tenantId,organizationId;
		RecordingRelatedCases(List<CaseSummaryDao.RelatedCaseRow> rows){this.rows=rows;}
		@Override public List<CaseSummaryDao.RelatedCaseRow> listActiveRelatedToOrganization(int tenantId,int organizationId){
			calls++;this.tenantId=tenantId;this.organizationId=organizationId;return rows;
		}
	}
	private static final class FakeOrganizations implements OrganizationServiceAdapter.OrganizationGateway {
		private final Organization organization;
		private List<OrganizationDao.OrganizationTypeDefinitionRow> effectiveTypes=List.of();
		private List<OrganizationDao.OrganizationTypeDefinitionRow> administrationTypes=List.of(); private int administrationCalls;
		private OrganizationDao.OrganizationTypeProfileRow profile;
		private OrganizationDao.StructuredContactProfileRow structuredProfile;
		private int effectiveCalls,profileCalls;
		private int structuredCalls;
		private com.shale.core.service.OrganizationServicePort.AssignOrganizationTypeCommand assignmentCommand;
		private com.shale.core.service.OrganizationServicePort.CreateOrganizationAggregateCommand aggregateCreate;
		private com.shale.core.service.OrganizationServicePort.UpdateOrganizationAggregateCommand aggregateUpdate;
		private int rowVerReads;
		FakeOrganizations(){this(null);}
		FakeOrganizations(Organization organization){this.organization=organization;}
		@Override public Organization findById(int id){return organization;}
		@Override public OrganizationDao.PagedResult<OrganizationDao.DirectoryOrganizationRow> findDirectoryPage(int p,int s,String q){return new OrganizationDao.PagedResult<>(List.of(),p,s,0);}
		@Override public List<OrganizationDao.OrganizationTypeRow> findOrganizationTypes(){return List.of();}
		@Override public List<OrganizationDao.OrganizationTypeDefinitionRow> listEffectiveOrganizationTypeDefinitions(int tenant){effectiveCalls++;return effectiveTypes;}
		@Override public List<OrganizationDao.OrganizationTypeDefinitionRow> listOrganizationTypesForAdministration(int tenant,int actor){administrationCalls++;return administrationTypes;}
		@Override public OrganizationDao.OrganizationTypeProfileRow findOrganizationTypeProfile(int organization,int tenant){profileCalls++;return profile;}
		@Override public OrganizationDao.StructuredContactProfileRow findStructuredContactProfile(int tenant,int organization){structuredCalls++;return structuredProfile;}
		@Override public int create(OrganizationDao.OrganizationCreateRequest r){return 0;}
		@Override public void update(Organization o){}
		@Override public com.shale.core.service.OrganizationServicePort.OrganizationTypeAssignmentMutationResult assignOrganizationType(com.shale.core.service.OrganizationServicePort.AssignOrganizationTypeCommand c){assignmentCommand=c;return new com.shale.core.service.OrganizationServicePort.OrganizationTypeAssignmentMutationResult(101, c.organizationId(), c.organizationTypeId(), false, 1, false, new byte[]{1});}
		@Override public com.shale.core.service.OrganizationServicePort.OrganizationTypeProfile createOrganizationAggregate(com.shale.core.service.OrganizationServicePort.CreateOrganizationAggregateCommand c){aggregateCreate=c;return new com.shale.core.service.OrganizationServicePort.OrganizationTypeProfile(7,41,12,true,List.of());}
		@Override public com.shale.core.service.OrganizationServicePort.OrganizationAggregateResult updateOrganizationAggregate(com.shale.core.service.OrganizationServicePort.UpdateOrganizationAggregateCommand c){aggregateUpdate=c;return new com.shale.core.service.OrganizationServicePort.OrganizationAggregateResult(7,new byte[]{8},new com.shale.core.service.OrganizationServicePort.OrganizationTypeProfile(7,41,13,true,List.of()));}
		@Override public byte[] findOrganizationRowVer(int organization,int tenant){rowVerReads++;return new byte[]{8};}
	}
}
