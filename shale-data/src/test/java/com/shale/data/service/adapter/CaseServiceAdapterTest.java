package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseLinkDto;
import com.shale.core.dto.ContactSharedCaseLinkDto;
import com.shale.core.dto.LinkTypeDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.CaseServicePort.AddCaseNoteCommand;
import com.shale.core.service.CaseServicePort.CaseStatusCommand;
import com.shale.core.service.CaseServicePort.CreateCaseLinkCommand;
import com.shale.core.service.CaseServicePort.DeleteCaseLinkCommand;
import com.shale.core.service.CaseServicePort.LinkTypeCommand;
import com.shale.core.service.CaseServicePort.ReorderCaseLinksCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseLinkCommand;
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

	@Test
	void listCaseLinksSharedWithContactDelegatesToGateway() {
		CaseLinkDto link = new CaseLinkDto(3, 3, 6502, 7, 5, "Box", "#123456", "box",
				"Shared Link", "https://example.invalid", null, false, null, 0, null, null, new byte[] {1}, new byte[] {2}, List.of());
		ContactSharedCaseLinkDto row = new ContactSharedCaseLinkDto(6502, "Test Case", link);
		FakeCaseGateway gateway = new FakeCaseGateway(List.of());
		gateway.contactSharedCaseLinks = List.of(row);
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);

		List<ContactSharedCaseLinkDto> actual = adapter.listCaseLinksSharedWithContact(10192, 7);

		assertEquals(10192, gateway.lastSharedContactId);
		assertEquals(7, gateway.lastSharedShaleClientId);
		assertEquals(List.of(row), actual);
	}


	@Test
	void linkTypeOverlayHonorsInactiveMaskDeletedResetAndSorting() {
		List<LinkTypeDto> rows = List.of(
				new LinkTypeDto(1, null, "Zulu Global", "#111", true, false, "shared", new byte[] {1}),
				new LinkTypeDto(2, 7, "Alpha Tenant", "#222", true, false, "shared", new byte[] {2}),
				new LinkTypeDto(3, null, "Visible Global", "#333", true, false, "visible", new byte[] {3}),
				new LinkTypeDto(4, 7, "Inactive Mask", "#444", false, false, "visible", new byte[] {4}),
				new LinkTypeDto(5, null, "Reset Global", "#555", true, false, "reset", new byte[] {5}),
				new LinkTypeDto(6, 7, "Deleted Override", "#666", false, true, "reset", new byte[] {6}),
				new LinkTypeDto(7, 8, "Other Tenant", "#777", true, false, "other", new byte[] {7}),
				new LinkTypeDto(8, 7, "Custom Tenant", "#888", true, false, null, new byte[] {8}));

		List<LinkTypeDto> active = CaseServiceAdapter.resolveEffectiveLinkTypes(rows, 7, false);
		assertEquals(List.of("Alpha Tenant", "Custom Tenant", "Reset Global"), active.stream().map(LinkTypeDto::name).toList());

		List<LinkTypeDto> admin = CaseServiceAdapter.resolveEffectiveLinkTypes(rows, 7, true);
		assertEquals(List.of("Alpha Tenant", "Custom Tenant", "Inactive Mask", "Reset Global"), admin.stream().map(LinkTypeDto::name).toList());
	}

	@Test
	void linkTypeValidationEnforcesConfiguredLimitsAndRowVer() {
		FakeCaseGateway gateway = new FakeCaseGateway(List.of());
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);
		String maxName = "N".repeat(100);
		String maxColor = "C".repeat(20);
		String maxSystemKey = "S".repeat(64);

		adapter.createLinkType(new LinkTypeCommand(null, 7, 5, maxName, maxColor, true, maxSystemKey, null));
		assertEquals(maxName, gateway.lastLinkTypeName);
		assertEquals(maxColor, gateway.lastLinkTypeColor);
		assertEquals(maxSystemKey, gateway.lastLinkTypeSystemKey);

		assertThrows(IllegalArgumentException.class,
				() -> adapter.createLinkType(new LinkTypeCommand(null, 7, 5, "N".repeat(101), maxColor, true, maxSystemKey, null)));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.createLinkType(new LinkTypeCommand(null, 7, 5, maxName, "C".repeat(21), true, maxSystemKey, null)));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.createLinkType(new LinkTypeCommand(null, 7, 5, maxName, maxColor, true, "S".repeat(65), null)));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.updateLinkType(new LinkTypeCommand(1, 7, 5, maxName, maxColor, true, maxSystemKey, null)));
	}

	@Test
	void caseLinkCommandsValidateBeforeDelegatingAndPreservePrimaryContract() {
		FakeCaseGateway gateway = new FakeCaseGateway(List.of());
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);
		byte[] caseRowVer = new byte[] {1};
		byte[] externalRowVer = new byte[] {2};

		adapter.createCaseLink(new CreateCaseLinkCommand(7, 5, 99, 3, " Court ", " https://Example.com/a?b=c#D ", "desc", true, "notes", 4));
		assertEquals("Court", gateway.lastCaseLinkDisplayName);
		assertEquals("https://Example.com/a?b=c#D", gateway.lastCaseLinkUrl);
		assertTrue(gateway.lastCaseLinkPrimary);

		adapter.updateCaseLink(new UpdateCaseLinkCommand(7, 5, 99, 11, 12, 3, "Updated", "https://example.com", null, null, null, null, caseRowVer, externalRowVer));
		assertNull(gateway.lastUpdateCaseLinkPrimary);
		adapter.updateCaseLink(new UpdateCaseLinkCommand(7, 5, 99, 11, 12, 3, "Updated", "https://example.com", null, true, null, null, caseRowVer, externalRowVer));
		assertEquals(Boolean.TRUE, gateway.lastUpdateCaseLinkPrimary);
		adapter.updateCaseLink(new UpdateCaseLinkCommand(7, 5, 99, 11, 12, 3, "Updated", "https://example.com", null, false, null, null, caseRowVer, externalRowVer));
		assertEquals(Boolean.FALSE, gateway.lastUpdateCaseLinkPrimary);

		assertThrows(IllegalArgumentException.class,
				() -> adapter.updateCaseLink(new UpdateCaseLinkCommand(7, 5, 99, 11, 12, 3, "Updated", "https://example.com", null, true, null, null, null, externalRowVer)));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.updateCaseLink(new UpdateCaseLinkCommand(7, 5, 99, 11, 12, 3, "Updated", "https://example.com", null, true, null, null, caseRowVer, null)));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.deleteCaseLink(new DeleteCaseLinkCommand(7, 5, 99, 11, null)));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.reorderCaseLinks(new ReorderCaseLinksCommand(7, 5, 99, List.of(1L, 1L))));
		assertThrows(IllegalArgumentException.class,
				() -> adapter.reorderCaseLinks(new ReorderCaseLinksCommand(7, 5, 99, Arrays.asList(1L, null))));
	}

	@Test
	void caseLinkGatewayDefaultsFailExplicitlyInsteadOfReturningPlausibleSuccess() throws Exception {
		String source = java.nio.file.Files.readString(java.nio.file.Path.of(
				"src/main/java/com/shale/data/service/adapter/CaseServiceAdapter.java"));
		for (Method method : CaseServiceAdapter.CaseGateway.class.getDeclaredMethods()) {
			if (!method.getName().matches("listCaseLinksSharedWithContact|createCaseLinkWithShares|updateCaseLinkWithShares|searchCaseLinkShareContacts|listCaseLinkShareContacts|listCaseLinkShareCaseContacts|listRequestedFromCaseParties|listCaseLinkShares|addCaseLinkShare|updateCaseLinkShare|removeCaseLinkShare")) {
				continue;
			}
			assertTrue(method.isDefault(), () -> method.getName() + " must be an explicit rejecting default when not implemented");
			String body = extractMethodBody(source.replace("\r\n", "\n"), method.getName());
			assertTrue(body.contains("unsupportedCaseLinkGatewayOperation"), method.getName() + " must reject explicitly");
		}
	}

	@Test
	void daoCaseGatewayOverridesEveryCaseLinkDefault() throws Exception {
		for (Method method : CaseServiceAdapter.CaseGateway.class.getDeclaredMethods()) {
			if (!method.isDefault() || !method.getName().matches("listCaseLinksSharedWithContact|createCaseLinkWithShares|updateCaseLinkWithShares|searchCaseLinkShareContacts|listCaseLinkShareContacts|listCaseLinkShareCaseContacts|listRequestedFromCaseParties|listCaseLinkShares|addCaseLinkShare|updateCaseLinkShare|removeCaseLinkShare")) {
				continue;
			}
			try {
				Class.forName("com.shale.data.service.adapter.CaseServiceAdapter$DaoCaseGateway").getDeclaredMethod(method.getName(), method.getParameterTypes());
			} catch (NoSuchMethodException ex) {
				fail("DaoCaseGateway must override " + method.getName() + " so production reaches CaseDao");
			}
		}
	}

	private static String extractMethodBody(String source, String methodName) {
		int name = source.indexOf("default ");
		while (name >= 0 && !source.substring(name, Math.min(source.length(), source.indexOf('{', name) < 0 ? source.length() : source.indexOf('{', name))).contains(methodName + "(")) {
			name = source.indexOf("default ", name + 1);
		}
		assertTrue(name >= 0, () -> "method missing: " + methodName);
		int open = source.indexOf('{', name);
		int depth = 0;
		for (int i = open; i < source.length(); i++) {
			char ch = source.charAt(i);
			if (ch == '{') depth++;
			else if (ch == '}' && --depth == 0) return source.substring(open + 1, i);
		}
		throw new AssertionError("unterminated method: " + methodName);
	}


	private static CaseDetailDto detail(long caseId, String caseName) {
		return new CaseDetailDto(caseId, "C-1", caseName, "description", "open", null,
				null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, LocalDateTime.now(), new byte[] {1});
	}

	static class FakeCaseGateway implements CaseServiceAdapter.CaseGateway {
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
		private String lastLinkTypeName;
		private String lastLinkTypeColor;
		private String lastLinkTypeSystemKey;
		private String lastCaseLinkDisplayName;
		String lastCaseLinkUrl;
		private boolean lastCaseLinkPrimary;
		private Boolean lastUpdateCaseLinkPrimary;
		private List<ContactSharedCaseLinkDto> contactSharedCaseLinks = List.of();
		private int lastSharedContactId;
		private int lastSharedShaleClientId;

		FakeCaseGateway(List<CaseUpdateDto> caseUpdates) {
			this.caseUpdates = caseUpdates;
		}

		@Override
		public CaseDetailDto getDetail(long caseId) {
			return detail(caseId, "Created case");
		}

		@Override
		public CaseOverviewDto getOverview(long caseId) {
			return null;
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
			return new PracticeAreaDto(2, name, color, active, false, systemKey, shaleClientId, true, false);
		}

		@Override
		public PracticeAreaDto updatePracticeArea(int shaleClientId, int practiceAreaId, String name, String color, boolean active, String systemKey) {
			return new PracticeAreaDto(practiceAreaId, name, color, active, false, systemKey, shaleClientId, true, false);
		}

		@Override
		public void deactivatePracticeArea(int shaleClientId, int practiceAreaId) {
		}

		@Override
		public CaseStatusDto createCaseStatus(int shaleClientId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey) {
			return new CaseStatusDto(1, name, closed, sortOrder, color, lifecycleKey, systemKey, shaleClientId, true, false);
		}

		@Override
		public CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey) {
			return new CaseStatusDto(statusId, name, closed, sortOrder, color, lifecycleKey, systemKey, shaleClientId, true, false);
		}

		@Override
		public CaseDao.StatusRow findStatusForTenantById(int shaleClientId, int statusId) {
			return new CaseDao.StatusRow(statusId, "Open", 10, "#00AA00", null, "open", true, false);
		}

		@Override
		public void setPrimaryStatus(long caseId, int statusId, String notes) {
		}

		@Override
		public void populateLifecycleDateIfNull(long caseId, String lifecycleKey) {
		}

		@Override
		public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
		}

		@Override public void removeCaseStatus(int t,int a,int id) { }
		@Override public CaseStatusDto restoreCaseStatus(int t,int a,int id) { return new CaseStatusDto(id,"Restored",false,10,null,null,null,t,true,false); }

		@Override
		public void addCaseNote(long caseId, int shaleClientId, String noteText, Integer createdByUserId) {
			lastNoteCaseId = caseId;
			lastNoteShaleClientId = shaleClientId;
			lastNoteText = noteText;
			lastNoteCreatedByUserId = createdByUserId;
		}


		@Override
		public List<LinkTypeDto> listLinkTypes(int shaleClientId, boolean includeInactive) { return List.of(); }

		@Override
		public List<LinkTypeDto> listLinkTypesForAdministration(int shaleClientId, int actorUserId) { return List.of(); }

		@Override
		public LinkTypeDto createLinkType(int shaleClientId, int actorUserId, String name, String color, boolean active, String systemKey) {
			lastLinkTypeName = name;
			lastLinkTypeColor = color;
			lastLinkTypeSystemKey = systemKey;
			return new LinkTypeDto(1, shaleClientId, name, color, active, false, systemKey, new byte[] {1});
		}

		@Override
		public LinkTypeDto updateLinkType(int shaleClientId, int actorUserId, int linkTypeId, String name, String color, boolean active, String systemKey, byte[] expectedRowVer) {
			return new LinkTypeDto(linkTypeId, shaleClientId, name, color, active, false, systemKey, new byte[] {2});
		}

		@Override
		public LinkTypeDto setLinkTypeActive(int shaleClientId, int actorUserId, int linkTypeId, boolean active, byte[] expectedRowVer) {
			return new LinkTypeDto(linkTypeId, shaleClientId, "Link", "#fff", active, false, "link", new byte[] {2});
		}

		@Override
		public void resetLinkTypeOverride(int shaleClientId, int actorUserId, int linkTypeId) {}

		@Override
		public List<CaseLinkDto> listCaseLinks(long caseId, int shaleClientId) { return List.of(); }

		@Override
		public java.util.Optional<CaseLinkDto> getPrimaryCaseLink(long caseId, int shaleClientId) { return java.util.Optional.empty(); }

		@Override
		public List<ContactSharedCaseLinkDto> listCaseLinksSharedWithContact(int contactId, int shaleClientId) {
			lastSharedContactId = contactId;
			lastSharedShaleClientId = shaleClientId;
			return contactSharedCaseLinks;
		}

		@Override
		public CaseLinkDto createCaseLink(int shaleClientId, int actorUserId, long caseId, int linkTypeId, String displayName, String url, String description, boolean primary, String notes, Integer sortOrder) {
			lastCaseLinkDisplayName = displayName;
			lastCaseLinkUrl = url;
			lastCaseLinkPrimary = primary;
			return null;
		}

		@Override
		public CaseLinkDto updateCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId, long externalLinkId, int linkTypeId, String displayName, String url, String description, Boolean primary, String notes, Integer sortOrder, byte[] expectedCaseLinkRowVer, byte[] expectedExternalLinkRowVer) {
			lastCaseLinkUrl = url;
			lastUpdateCaseLinkPrimary = primary;
			return null;
		}

		@Override
		public CaseLinkDto setPrimaryCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId) { return null; }

		@Override
		public List<CaseLinkDto> reorderCaseLinks(int shaleClientId, int actorUserId, long caseId, List<Long> orderedCaseLinkIds) { return List.of(); }

		@Override
		public void deleteCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId, byte[] expectedCaseLinkRowVer) {}

		@Override
		public void updateCaseAssignment(long caseId, int shaleClientId, int practiceAreaId, int responsibleAttorneyUserId) {
		}


		@Override
		public long createCaseAggregate(CaseServicePort.CreateCaseCommand command, int statusId) {
			return 42L;
		}
	}
}
