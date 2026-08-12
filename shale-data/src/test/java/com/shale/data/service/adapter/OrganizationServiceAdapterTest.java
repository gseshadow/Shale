package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseSummaryProjection;
import com.shale.core.model.Organization;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.data.dao.OrganizationDao;

final class OrganizationServiceAdapterTest {
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
		private final Organization organization; FakeOrganizations(Organization organization){this.organization=organization;}
		@Override public Organization findById(int id){return organization;}
		@Override public OrganizationDao.PagedResult<OrganizationDao.DirectoryOrganizationRow> findDirectoryPage(int p,int s,String q){return new OrganizationDao.PagedResult<>(List.of(),p,s,0);}
		@Override public List<OrganizationDao.OrganizationTypeRow> findOrganizationTypes(){return List.of();}
		@Override public int create(OrganizationDao.OrganizationCreateRequest r){return 0;}
		@Override public void update(Organization o){}
	}
}
