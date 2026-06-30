package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.service.CaseServicePort.AddCaseNoteCommand;
import com.shale.core.service.CaseServicePort.CaseStatusCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseCoreDetailsCommand;
import com.shale.data.dao.CaseDao;

class CaseServiceAdapterTest {

	@Test
	void listCaseUpdatesDelegatesToGateway() {
		CaseUpdateDto update = new CaseUpdateDto(11, 99, "note", LocalDateTime.now(), null, 5, "Author");
		FakeCaseGateway gateway = new FakeCaseGateway(List.of(update));
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);

		List<CaseUpdateDto> actual = adapter.listCaseUpdates(99, 42);

		assertEquals(99, gateway.lastCaseUpdatesCaseId);
		assertEquals(List.of(update), actual);
	}

	@Test
	void addCaseNoteDelegatesToExistingDaoContract() {
		FakeCaseGateway gateway = new FakeCaseGateway(List.of());
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);

		adapter.addCaseNote(new AddCaseNoteCommand(99, 42, 5, " note "));

		assertEquals(99, gateway.lastNoteCaseId);
		assertEquals(42, gateway.lastNoteShaleClientId);
		assertEquals(5, gateway.lastNoteCreatedByUserId);
		assertEquals(" note ", gateway.lastNoteText);
	}

	@Test
	void updateCaseCoreDetailsDelegatesWithRowVersion() {
		CaseDetailDto updated = detail(99, "Updated");
		FakeCaseGateway gateway = new FakeCaseGateway(List.of());
		gateway.updatedCase = updated;
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);
		byte[] rowVer = new byte[] {1, 2, 3};

		CaseDetailDto actual = adapter.updateCaseCoreDetails(new UpdateCaseCoreDetailsCommand(
				99, 42, 5, "Updated", "C-1", "description", LocalDate.of(2026, 1, 2),
				LocalDate.of(2026, 2, 3), LocalDate.of(2026, 3, 4), "summary", rowVer));

		assertSame(updated, actual);
		assertEquals(99, gateway.lastUpdateCaseId);
		assertEquals("Updated", gateway.lastUpdateName);
		assertEquals("C-1", gateway.lastUpdateCaseNumber);
		assertEquals("description", gateway.lastUpdateDescription);
		assertEquals(LocalDate.of(2026, 1, 2), gateway.lastUpdateIncidentDate);
		assertEquals(LocalDate.of(2026, 2, 3), gateway.lastUpdateSolDate);
		assertEquals(LocalDate.of(2026, 3, 4), gateway.lastUpdateTortNoticeDeadline);
		assertEquals("summary", gateway.lastUpdateSummary);
		assertArrayEquals(rowVer, gateway.lastUpdateRowVer);
		assertEquals(5, gateway.lastUpdateActorUserId);
	}

	@Test
	void listPracticeAreasReturnsEffectiveTenantOverlayOnly() {
		FakeCaseGateway gateway = new FakeCaseGateway(List.of());
		gateway.practiceAreas = List.of(
				new PracticeAreaDto(101, "Medical Malpractice", "#111111", true, false, "medical_malpractice", null),
				new PracticeAreaDto(102, "Personal Injury", "#222222", true, false, "personal_injury", null),
				new PracticeAreaDto(103, "Sexual Assault", "#333333", true, false, "sexual_assault", null),
				new PracticeAreaDto(201, "Tenant Medical", "#aaaaaa", true, false, " medical_malpractice ", 7),
				new PracticeAreaDto(202, "Tenant Personal", "#bbbbbb", true, false, "personal_injury", 7),
				new PracticeAreaDto(203, "Other Tenant Sexual", "#cccccc", true, false, "sexual_assault", 8));
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);

		List<PracticeAreaDto> actual = adapter.listPracticeAreas(7, true);

		assertEquals(List.of(103, 201, 202), actual.stream().map(PracticeAreaDto::id).sorted().toList());
		assertEquals(List.of(), actual.stream()
				.filter(area -> area.shaleClientId() != null && area.shaleClientId() != 7)
				.map(PracticeAreaDto::id)
				.toList());
	}

	@Test
	void caseStatusCommandsDelegateRealStatusColumns() {
		FakeCaseGateway gateway = new FakeCaseGateway(List.of());
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);

		CaseStatusDto created = adapter.createCaseStatus(new CaseStatusCommand(
				null, 7, "Pending", false, 10, "#336699", "OPEN", "pending"));
		CaseStatusDto updated = adapter.updateCaseStatus(new CaseStatusCommand(
				created.id(), 7, "Closed", true, 20, "#663399", "CLOSED", "closed"));

		assertEquals("Pending", created.name());
		assertEquals(false, created.closed());
		assertEquals("#336699", created.color());
		assertEquals("OPEN", created.lifecycleKey());
		assertEquals("pending", created.systemKey());
		assertEquals("Closed", updated.name());
		assertEquals(true, updated.closed());
		assertEquals(20, updated.sortOrder());
		assertEquals("#663399", updated.color());
		assertEquals("CLOSED", updated.lifecycleKey());
		assertEquals("closed", updated.systemKey());
	}

	private static CaseDetailDto detail(long caseId, String caseName) {
		return new CaseDetailDto(caseId, "C-1", caseName, "description", "open", null,
				null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, LocalDateTime.now(), new byte[] {1});
	}

	private static final class FakeCaseGateway implements CaseServiceAdapter.CaseGateway {
		private final List<CaseUpdateDto> caseUpdates;
		private long lastCaseUpdatesCaseId;
		private long lastNoteCaseId;
		private int lastNoteShaleClientId;
		private String lastNoteText;
		private Integer lastNoteCreatedByUserId;
		private CaseDetailDto updatedCase;
		private List<PracticeAreaDto> practiceAreas = List.of();
		private long lastUpdateCaseId;
		private String lastUpdateName;
		private String lastUpdateCaseNumber;
		private String lastUpdateDescription;
		private LocalDate lastUpdateIncidentDate;
		private LocalDate lastUpdateSolDate;
		private LocalDate lastUpdateTortNoticeDeadline;
		private String lastUpdateSummary;
		private byte[] lastUpdateRowVer;
		private Integer lastUpdateActorUserId;

		private FakeCaseGateway(List<CaseUpdateDto> caseUpdates) {
			this.caseUpdates = caseUpdates;
		}

		@Override
		public CaseDetailDto getDetail(long caseId) {
			return null;
		}

		@Override
		public CaseOverviewDto getOverview(long caseId) {
			return null;
		}

		@Override
		public List<CaseDao.CaseRow> searchCasesByName(String query) {
			return List.of();
		}

		@Override
		public List<CaseDao.CaseRow> listAssignedCasesForBoard(int assignedUserId) {
			return List.of();
		}

		@Override
		public List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId) {
			lastCaseUpdatesCaseId = caseId;
			return caseUpdates;
		}

		@Override
		public List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
			return List.of();
		}

		@Override
		public List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive) {
			return List.of();
		}

		@Override
		public List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
			return practiceAreas;
		}

		@Override
		public List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive) {
			return List.of();
		}

		@Override
		public PracticeAreaDto createPracticeArea(int shaleClientId, String name, String color, boolean active, String systemKey) {
			return new PracticeAreaDto(2, name, color, active, false, systemKey, shaleClientId);
		}

		@Override
		public PracticeAreaDto updatePracticeArea(int shaleClientId, int practiceAreaId, String name, String color, boolean active, String systemKey) {
			return new PracticeAreaDto(practiceAreaId, name, color, active, false, systemKey, shaleClientId);
		}

		@Override
		public void deactivatePracticeArea(int shaleClientId, int practiceAreaId) {
		}

		@Override
		public CaseStatusDto createCaseStatus(int shaleClientId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey) {
			return new CaseStatusDto(1, name, closed, sortOrder, color, lifecycleKey, systemKey, shaleClientId);
		}

		@Override
		public CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey) {
			return new CaseStatusDto(statusId, name, closed, sortOrder, color, lifecycleKey, systemKey, shaleClientId);
		}

		@Override
		public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
		}

		@Override
		public void addCaseNote(long caseId, int shaleClientId, String noteText, Integer createdByUserId) {
			lastNoteCaseId = caseId;
			lastNoteShaleClientId = shaleClientId;
			lastNoteText = noteText;
			lastNoteCreatedByUserId = createdByUserId;
		}

		@Override
		public void updateCaseAssignment(long caseId, int shaleClientId, int practiceAreaId, int responsibleAttorneyUserId) {
		}

		@Override
		public CaseDetailDto updateCase(long caseId, String name, String caseNumber, String description,
				LocalDate incidentDate, LocalDate solDate, LocalDate tortNoticeDeadline, String summary,
				byte[] expectedRowVer, Integer actorUserId) {
			lastUpdateCaseId = caseId;
			lastUpdateName = name;
			lastUpdateCaseNumber = caseNumber;
			lastUpdateDescription = description;
			lastUpdateIncidentDate = incidentDate;
			lastUpdateSolDate = solDate;
			lastUpdateTortNoticeDeadline = tortNoticeDeadline;
			lastUpdateSummary = summary;
			lastUpdateRowVer = expectedRowVer;
			lastUpdateActorUserId = actorUserId;
			return updatedCase;
		}
	}
}
