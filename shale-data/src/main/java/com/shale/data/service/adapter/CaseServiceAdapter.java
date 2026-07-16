package com.shale.data.service.adapter;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.dto.CaseLinkDto;
import com.shale.core.dto.LinkTypeDto;
import com.shale.core.dto.CaseStatusDto;
import com.shale.core.dto.PracticeAreaDto;
import com.shale.core.service.CaseServicePort;
import com.shale.data.dao.CaseDao;

/**
 * Thin CaseServicePort adapter over existing CaseDao read operations.
 */
public final class CaseServiceAdapter implements CaseServicePort {

	private final CaseGateway caseGateway;

	public CaseServiceAdapter(CaseDao caseDao) {
		this(new DaoCaseGateway(caseDao));
	}

	CaseServiceAdapter(CaseGateway caseGateway) {
		this.caseGateway = Objects.requireNonNull(caseGateway, "caseGateway");
	}

	@Override
	public Optional<CaseDetailDto> getCaseDetail(long caseId, int shaleClientId) {
		return Optional.ofNullable(caseGateway.getDetail(caseId));
	}

	@Override
	public Optional<CaseOverviewDto> getCaseOverview(long caseId, int shaleClientId) {
		return Optional.ofNullable(caseGateway.getOverview(caseId));
	}

	@Override
	public List<CaseOverviewDto> searchCases(String query, int shaleClientId, int limit) {
		int resolvedLimit = limit <= 0 ? 25 : limit;
		return caseGateway.searchCasesByName(query).stream()
				.limit(resolvedLimit)
				.map(CaseDao.CaseRow::id)
				.map(caseGateway::getOverview)
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	public List<CaseOverviewDto> listAssignedCases(int assignedUserId, int shaleClientId, int limit) {
		int resolvedLimit = limit <= 0 ? 25 : limit;
		return caseGateway.listAssignedCasesForBoard(assignedUserId).stream()
				.limit(resolvedLimit)
				.map(CaseDao.CaseRow::id)
				.map(caseGateway::getOverview)
				.filter(Objects::nonNull)
				.toList();
	}

	@Override
	public List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId) {
		return caseGateway.listCaseUpdates(caseId, shaleClientId);
	}

	@Override
	public CaseDetailDto createCase(CreateCaseCommand command) {
		Objects.requireNonNull(command, "command");
		List<CaseStatusDto> statuses = listCaseStatuses(command.shaleClientId(), false);
		CaseStatusDto initialStatus = statuses.stream()
				.filter(Objects::nonNull)
				.filter(status -> !status.closed())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No active non-closed case status is available for this tenant."));
		long caseId = caseGateway.createBasicCase(command, initialStatus.id());
		return caseGateway.getDetail(caseId);
	}

	@Override
	public List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
		return caseGateway.listCaseStatuses(shaleClientId, includeInactive);
	}

	@Override
	public List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive) {
		return caseGateway.listTenantCaseStatuses(shaleClientId, includeInactive);
	}

	@Override
	public List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
		return resolveEffectivePracticeAreas(caseGateway.listPracticeAreas(shaleClientId, includeInactive), shaleClientId);
	}

	static List<PracticeAreaDto> resolveEffectivePracticeAreas(List<PracticeAreaDto> rows, int shaleClientId) {
		if (rows == null || rows.isEmpty() || shaleClientId <= 0) {
			return List.of();
		}

		Map<String, PracticeAreaDto> keyed = new LinkedHashMap<>();
		List<PracticeAreaDto> unkeyed = new ArrayList<>();
		for (PracticeAreaDto area : rows) {
			if (area == null) {
				continue;
			}
			Integer areaTenantId = area.shaleClientId();
			if (areaTenantId != null && areaTenantId != shaleClientId) {
				continue;
			}

			String systemKey = normalizeSystemKey(area.systemKey());
			if (systemKey == null) {
				unkeyed.add(area);
				continue;
			}

			PracticeAreaDto existing = keyed.get(systemKey);
			boolean tenantRow = areaTenantId != null && areaTenantId == shaleClientId;
			boolean existingTenantRow = existing != null
					&& existing.shaleClientId() != null
					&& existing.shaleClientId() == shaleClientId;
			if (existing == null || (tenantRow && !existingTenantRow)) {
				keyed.put(systemKey, area);
			}
		}

		List<PracticeAreaDto> effective = new ArrayList<>(keyed.size() + unkeyed.size());
		effective.addAll(keyed.values());
		effective.addAll(unkeyed);
		effective.sort(Comparator
				.comparing((PracticeAreaDto area) -> area.name() == null ? "" : area.name(), String.CASE_INSENSITIVE_ORDER)
				.thenComparingInt(PracticeAreaDto::id));
		return List.copyOf(effective);
	}

	private static String normalizeSystemKey(String systemKey) {
		String normalized = systemKey == null ? "" : systemKey.trim().toLowerCase(Locale.ROOT);
		return normalized.isBlank() ? null : normalized;
	}

	@Override
	public List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive) {
		return caseGateway.listTenantPracticeAreas(shaleClientId, includeInactive);
	}

	@Override
	public List<LinkTypeDto> listLinkTypes(int shaleClientId, boolean includeInactive) {
		return resolveEffectiveLinkTypes(caseGateway.listLinkTypes(shaleClientId, includeInactive), shaleClientId, includeInactive);
	}

	static List<LinkTypeDto> resolveEffectiveLinkTypes(List<LinkTypeDto> rows, int shaleClientId, boolean includeInactive) {
		if (rows == null || rows.isEmpty() || shaleClientId <= 0) {
			return List.of();
		}

		Map<String, LinkTypeDto> bySystemKey = new LinkedHashMap<>();
		List<LinkTypeDto> unkeyed = new ArrayList<>();
		for (LinkTypeDto type : rows) {
			if (type == null) {
				continue;
			}
			Integer tenantId = type.shaleClientId();
			if (tenantId != null && tenantId != shaleClientId) {
				continue;
			}

			String key = normalizeSystemKey(type.systemKey());
			if (key == null) {
				if (includeInactive || (type.active() && !type.deleted())) {
					unkeyed.add(type);
				}
				continue;
			}

			if (tenantId != null && tenantId == shaleClientId && type.deleted()) {
				// A deleted tenant override is a reset/removal marker and must not suppress the global row.
				continue;
			}

			LinkTypeDto existing = bySystemKey.get(key);
			boolean tenantRow = tenantId != null && tenantId == shaleClientId;
			boolean existingTenantRow = existing != null
					&& existing.shaleClientId() != null
					&& existing.shaleClientId() == shaleClientId;
			if (existing == null || (tenantRow && !existingTenantRow)) {
				bySystemKey.put(key, type);
			}
		}

		List<LinkTypeDto> effective = new ArrayList<>(bySystemKey.size() + unkeyed.size());
		for (LinkTypeDto winner : bySystemKey.values()) {
			if (includeInactive || (winner.active() && !winner.deleted())) {
				effective.add(winner);
			}
		}
		effective.addAll(unkeyed);
		effective.sort(Comparator
				.comparing((LinkTypeDto type) -> type.name() == null ? "" : type.name(), String.CASE_INSENSITIVE_ORDER)
				.thenComparingInt(LinkTypeDto::id));
		return List.copyOf(effective);
	}

	@Override
	public List<LinkTypeDto> listLinkTypesForAdministration(int shaleClientId, int actorUserId) {
		return caseGateway.listLinkTypesForAdministration(shaleClientId, actorUserId);
	}

	@Override
	public LinkTypeDto createLinkType(LinkTypeCommand command) {
		Objects.requireNonNull(command, "command");
		validateLinkTypeName(command.name());
		validateLinkTypeColor(command.color());
		validateSystemKey(command.systemKey());
		return caseGateway.createLinkType(
				command.shaleClientId(),
				command.actorUserId(),
				command.name(),
				command.color(),
				command.active(),
				command.systemKey());
	}

	@Override
	public LinkTypeDto updateLinkType(LinkTypeCommand command) {
		Objects.requireNonNull(command, "command");
		if (command.id() == null) {
			throw new IllegalArgumentException("Link type id is required.");
		}
		validateRequiredRowVer(command.expectedRowVer(), "expectedRowVer");
		validateLinkTypeName(command.name());
		validateLinkTypeColor(command.color());
		validateSystemKey(command.systemKey());
		return caseGateway.updateLinkType(
				command.shaleClientId(),
				command.actorUserId(),
				command.id(),
				command.name(),
				command.color(),
				command.active(),
				command.systemKey(),
				command.expectedRowVer());
	}

	@Override
	public LinkTypeDto setLinkTypeActive(SetLinkTypeActiveCommand command) {
		Objects.requireNonNull(command, "command");
		validateRequiredRowVer(command.expectedRowVer(), "expectedRowVer");
		return caseGateway.setLinkTypeActive(
				command.shaleClientId(),
				command.actorUserId(),
				command.linkTypeId(),
				command.active(),
				command.expectedRowVer());
	}

	@Override
	public void resetLinkTypeOverride(ResetLinkTypeOverrideCommand command) {
		Objects.requireNonNull(command, "command");
		caseGateway.resetLinkTypeOverride(command.shaleClientId(), command.actorUserId(), command.linkTypeId());
	}

	@Override
	public List<CaseLinkDto> listCaseLinks(long caseId, int shaleClientId) {
		return caseGateway.listCaseLinks(caseId, shaleClientId);
	}

	@Override
	public Optional<CaseLinkDto> getPrimaryCaseLink(long caseId, int shaleClientId) {
		return caseGateway.getPrimaryCaseLink(caseId, shaleClientId);
	}

	@Override
	public CaseLinkDto createCaseLink(CreateCaseLinkCommand command) {
		Objects.requireNonNull(command, "command");
		return caseGateway.createCaseLink(
				command.shaleClientId(),
				command.actorUserId(),
				command.caseId(),
				command.linkTypeId(),
				validateDisplayName(command.displayName()),
				validateUrl(command.url()),
				validateDescription(command.description()),
				command.primary(),
				validateNotes(command.notes()),
				command.sortOrder());
	}

	@Override
	public CaseLinkDto updateCaseLink(UpdateCaseLinkCommand command) {
		Objects.requireNonNull(command, "command");
		validateRequiredRowVer(command.expectedCaseLinkRowVer(), "expectedCaseLinkRowVer");
		validateRequiredRowVer(command.expectedExternalLinkRowVer(), "expectedExternalLinkRowVer");
		return caseGateway.updateCaseLink(
				command.shaleClientId(),
				command.actorUserId(),
				command.caseId(),
				command.caseLinkId(),
				command.externalLinkId(),
				command.linkTypeId(),
				validateDisplayName(command.displayName()),
				validateUrl(command.url()),
				validateDescription(command.description()),
				command.primary(),
				validateNotes(command.notes()),
				command.sortOrder(),
				command.expectedCaseLinkRowVer(),
				command.expectedExternalLinkRowVer());
	}

	@Override
	public CaseLinkDto setPrimaryCaseLink(SetPrimaryCaseLinkCommand command) {
		Objects.requireNonNull(command, "command");
		return caseGateway.setPrimaryCaseLink(command.shaleClientId(), command.actorUserId(), command.caseId(), command.caseLinkId());
	}

	@Override
	public List<CaseLinkDto> reorderCaseLinks(ReorderCaseLinksCommand command) {
		Objects.requireNonNull(command, "command");
		validateOrderedCaseLinkIds(command.orderedCaseLinkIds());
		return caseGateway.reorderCaseLinks(command.shaleClientId(), command.actorUserId(), command.caseId(), command.orderedCaseLinkIds());
	}

	@Override
	public void deleteCaseLink(DeleteCaseLinkCommand command) {
		Objects.requireNonNull(command, "command");
		validateRequiredRowVer(command.expectedCaseLinkRowVer(), "expectedCaseLinkRowVer");
		caseGateway.deleteCaseLink(
				command.shaleClientId(),
				command.actorUserId(),
				command.caseId(),
				command.caseLinkId(),
				command.expectedCaseLinkRowVer());
	}

	private static String validateLinkTypeName(String value) {
		return validateRequiredLength(value, "Name", 100);
	}

	private static String validateLinkTypeColor(String value) {
		if (value != null && value.trim().length() > 20) {
			throw new IllegalArgumentException("Color must be 20 characters or fewer.");
		}
		return value;
	}

	private static String validateSystemKey(String value) {
		String normalized = normalizeSystemKey(value);
		if (normalized != null && normalized.length() > 64) {
			throw new IllegalArgumentException("System key must be 64 characters or fewer.");
		}
		return normalized;
	}

	private static String validateDisplayName(String value) {
		return validateRequiredLength(value, "Display name", 255);
	}

	private static String validateRequiredLength(String value, String label, int maxLength) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.isBlank()) {
			throw new IllegalArgumentException(label + " is required.");
		}
		if (trimmed.length() > maxLength) {
			throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
		}
		return trimmed;
	}

	private static String validateDescription(String value) {
		return value;
	}

	private static String validateNotes(String value) {
		if (value != null && value.length() > 2000) {
			throw new IllegalArgumentException("Notes must be 2000 characters or fewer.");
		}
		return value;
	}

	static String validateUrl(String value) {
		String url = value == null ? "" : value.trim();
		if (url.isBlank()) {
			throw new IllegalArgumentException("URL is required.");
		}
		if (url.length() > 2048) {
			throw new IllegalArgumentException("URL must be 2048 characters or fewer.");
		}
		for (int i = 0; i < url.length(); i++) {
			if (Character.isISOControl(url.charAt(i))) {
				throw new IllegalArgumentException("URL must not contain control characters.");
			}
		}
		try {
			URI uri = new URI(url);
			String scheme = uri.getScheme();
			if (!uri.isAbsolute() || scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
				throw new IllegalArgumentException("URL must be an absolute http or https URL.");
			}
			if (uri.getHost() == null || uri.getHost().isBlank()) {
				throw new IllegalArgumentException("URL must include a host.");
			}
			if (uri.getUserInfo() != null) {
				throw new IllegalArgumentException("URL must not include credentials.");
			}
			return url;
		} catch (URISyntaxException ex) {
			throw new IllegalArgumentException("URL is not valid.");
		}
	}

	private static void validateRequiredRowVer(byte[] rowVer, String label) {
		if (rowVer == null || rowVer.length == 0) {
			throw new IllegalArgumentException(label + " is required.");
		}
	}

	private static void validateOrderedCaseLinkIds(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			throw new IllegalArgumentException("Ordered case link ids are required.");
		}
		if (ids.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("Ordered case link ids must not contain null values.");
		}
		if (ids.stream().distinct().count() != ids.size()) {
			throw new IllegalArgumentException("Ordered case link ids must not contain duplicates.");
		}
	}

	@Override
	public PracticeAreaDto createPracticeArea(PracticeAreaCommand command) {
		Objects.requireNonNull(command, "command");
		return caseGateway.createPracticeArea(command.shaleClientId(), command.name(), command.color(), command.active(), command.systemKey());
	}

	@Override
	public PracticeAreaDto updatePracticeArea(PracticeAreaCommand command) {
		Objects.requireNonNull(command, "command");
		if (command.id() == null) {
			throw new IllegalArgumentException("Practice area id is required.");
		}
		return caseGateway.updatePracticeArea(command.shaleClientId(), command.id(), command.name(), command.color(), command.active(), command.systemKey());
	}

	@Override
	public void deactivatePracticeArea(int shaleClientId, int practiceAreaId) {
		caseGateway.deactivatePracticeArea(shaleClientId, practiceAreaId);
	}

	@Override
	public CaseStatusDto createCaseStatus(CaseStatusCommand command) {
		Objects.requireNonNull(command, "command");
		return caseGateway.createCaseStatus(command.shaleClientId(), command.name(), command.closed(), command.sortOrder(), command.color(), command.lifecycleKey(), command.systemKey());
	}

	@Override
	public CaseStatusDto updateCaseStatus(CaseStatusCommand command) {
		Objects.requireNonNull(command, "command");
		if (command.id() == null) {
			throw new IllegalArgumentException("Status id is required.");
		}
		return caseGateway.updateCaseStatus(command.shaleClientId(), command.id(), command.name(), command.closed(), command.sortOrder(), command.color(), command.lifecycleKey(), command.systemKey());
	}


	@Override
	public CaseDetailDto updateCaseCurrentStatus(UpdateCaseStatusCommand command) {
		Objects.requireNonNull(command, "command");
		CaseDao.StatusRow status = caseGateway.findStatusForTenantById(command.shaleClientId(), command.statusId());
		if (status == null) {
			throw new IllegalArgumentException("Case status is not available for this tenant.");
		}
		caseGateway.setPrimaryStatus(command.caseId(), status.id(), null);
		caseGateway.populateLifecycleDateIfNull(command.caseId(), status.lifecycleKey());
		return caseGateway.getDetail(command.caseId());
	}

	@Override
	public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
		caseGateway.reorderCaseStatuses(shaleClientId, firstStatusId, secondStatusId);
	}

	@Override
	public void addCaseNote(AddCaseNoteCommand command) {
		Objects.requireNonNull(command, "command");
		caseGateway.addCaseNote(command.caseId(), command.shaleClientId(), command.noteText(), command.actorUserId());
	}

	@Override
	public CaseDetailDto updateCaseAssignment(UpdateCaseAssignmentCommand command) {
		Objects.requireNonNull(command, "command");
		caseGateway.updateCaseAssignment(command.caseId(), command.shaleClientId(), command.practiceAreaId(), command.responsibleAttorneyUserId());
		return caseGateway.getDetail(command.caseId());
	}

	@Override
	public CaseDetailDto updateCaseCoreDetails(UpdateCaseCoreDetailsCommand command) {
		Objects.requireNonNull(command, "command");
		return caseGateway.updateCase(
				command.caseId(),
				command.caseName(),
				command.caseNumber(),
				command.description(),
				command.dateOfInjury(),
				command.statuteOfLimitations(),
				command.tortNoticeDeadline(),
				command.summary(),
				command.expectedRowVer(),
				command.actorUserId());
	}

	interface CaseGateway {
		CaseDetailDto getDetail(long caseId);

		CaseOverviewDto getOverview(long caseId);

		List<CaseDao.CaseRow> searchCasesByName(String query);

		List<CaseDao.CaseRow> listAssignedCasesForBoard(int assignedUserId);

		List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId);

		void addCaseNote(long caseId, int shaleClientId, String noteText, Integer createdByUserId);

		List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive);

		List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive);

		List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive);

		List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive);

		List<LinkTypeDto> listLinkTypes(int shaleClientId, boolean includeInactive);
		List<LinkTypeDto> listLinkTypesForAdministration(int shaleClientId, int actorUserId);
		LinkTypeDto createLinkType(int shaleClientId, int actorUserId, String name, String color, boolean active, String systemKey);
		LinkTypeDto updateLinkType(int shaleClientId, int actorUserId, int linkTypeId, String name, String color, boolean active, String systemKey, byte[] expectedRowVer);
		LinkTypeDto setLinkTypeActive(int shaleClientId, int actorUserId, int linkTypeId, boolean active, byte[] expectedRowVer);
		void resetLinkTypeOverride(int shaleClientId, int actorUserId, int linkTypeId);
		List<CaseLinkDto> listCaseLinks(long caseId, int shaleClientId);
		Optional<CaseLinkDto> getPrimaryCaseLink(long caseId, int shaleClientId);
		CaseLinkDto createCaseLink(int shaleClientId, int actorUserId, long caseId, int linkTypeId, String displayName, String url, String description, boolean primary, String notes, Integer sortOrder);
		CaseLinkDto updateCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId, long externalLinkId, int linkTypeId, String displayName, String url, String description, Boolean primary, String notes, Integer sortOrder, byte[] expectedCaseLinkRowVer, byte[] expectedExternalLinkRowVer);
		CaseLinkDto setPrimaryCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId);
		List<CaseLinkDto> reorderCaseLinks(int shaleClientId, int actorUserId, long caseId, List<Long> orderedCaseLinkIds);
		void deleteCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId, byte[] expectedCaseLinkRowVer);

		PracticeAreaDto createPracticeArea(int shaleClientId, String name, String color, boolean active, String systemKey);

		PracticeAreaDto updatePracticeArea(int shaleClientId, int practiceAreaId, String name, String color, boolean active, String systemKey);

		void deactivatePracticeArea(int shaleClientId, int practiceAreaId);

		CaseStatusDto createCaseStatus(int shaleClientId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey);

		CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey);


		CaseDao.StatusRow findStatusForTenantById(int shaleClientId, int statusId);

		void setPrimaryStatus(long caseId, int statusId, String notes);

		void populateLifecycleDateIfNull(long caseId, String lifecycleKey);

		void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId);

		void updateCaseAssignment(long caseId, int shaleClientId, int practiceAreaId, int responsibleAttorneyUserId);

		CaseDetailDto updateCase(long caseId, String name, String caseNumber, String description,
				LocalDate incidentDate, LocalDate solDate, LocalDate tortNoticeDeadline, String summary,
				byte[] expectedRowVer, Integer actorUserId);

		long createBasicCase(CreateCaseCommand command, int statusId);
	}

	private record DaoCaseGateway(CaseDao caseDao) implements CaseGateway {
		private DaoCaseGateway {
			Objects.requireNonNull(caseDao, "caseDao");
		}

		@Override
		public CaseDetailDto getDetail(long caseId) {
			return caseDao.getDetail(caseId);
		}

		@Override
		public CaseOverviewDto getOverview(long caseId) {
			return caseDao.getOverview(caseId);
		}

		@Override
		public List<CaseDao.CaseRow> searchCasesByName(String query) {
			return caseDao.searchCasesByName(query);
		}

		@Override
		public List<CaseDao.CaseRow> listAssignedCasesForBoard(int assignedUserId) {
			return caseDao.listAssignedCasesForBoard(assignedUserId);
		}

		@Override
		public List<CaseUpdateDto> listCaseUpdates(long caseId, int shaleClientId) {
			return caseDao.listCaseUpdates(caseId, shaleClientId);
		}

		@Override
		public void addCaseNote(long caseId, int shaleClientId, String noteText, Integer createdByUserId) {
			caseDao.addCaseNote(caseId, shaleClientId, noteText, createdByUserId);
		}

		@Override
		public List<CaseStatusDto> listCaseStatuses(int shaleClientId, boolean includeInactive) {
			return caseDao.listCaseStatuses(shaleClientId, includeInactive);
		}

		@Override
		public List<CaseStatusDto> listTenantCaseStatuses(int shaleClientId, boolean includeInactive) {
			return caseDao.listTenantCaseStatuses(shaleClientId, includeInactive);
		}

		@Override
		public List<PracticeAreaDto> listPracticeAreas(int shaleClientId, boolean includeInactive) {
			return caseDao.listPracticeAreas(shaleClientId, includeInactive);
		}

		@Override
		public List<PracticeAreaDto> listTenantPracticeAreas(int shaleClientId, boolean includeInactive) {
			return caseDao.listTenantPracticeAreas(shaleClientId, includeInactive);
		}

		@Override
		public PracticeAreaDto createPracticeArea(int shaleClientId, String name, String color, boolean active, String systemKey) {
			return caseDao.createPracticeArea(shaleClientId, name, color, active, systemKey);
		}

		@Override
		public PracticeAreaDto updatePracticeArea(int shaleClientId, int practiceAreaId, String name, String color, boolean active, String systemKey) {
			return caseDao.updatePracticeArea(shaleClientId, practiceAreaId, name, color, active, systemKey);
		}

		@Override
		public void deactivatePracticeArea(int shaleClientId, int practiceAreaId) {
			caseDao.deactivatePracticeArea(shaleClientId, practiceAreaId);
		}

		@Override
		public CaseStatusDto createCaseStatus(int shaleClientId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey) {
			return caseDao.createCaseStatus(shaleClientId, name, closed, sortOrder, color, lifecycleKey, systemKey);
		}

		@Override
		public CaseStatusDto updateCaseStatus(int shaleClientId, int statusId, String name, boolean closed, Integer sortOrder, String color, String lifecycleKey, String systemKey) {
			return caseDao.updateCaseStatus(shaleClientId, statusId, name, closed, sortOrder, color, lifecycleKey, systemKey);
		}


		@Override
		public CaseDao.StatusRow findStatusForTenantById(int shaleClientId, int statusId) {
			return caseDao.findStatusForTenantById(shaleClientId, statusId);
		}

		@Override
		public void setPrimaryStatus(long caseId, int statusId, String notes) {
			caseDao.setPrimaryStatus(caseId, statusId, notes);
		}

		@Override
		public void populateLifecycleDateIfNull(long caseId, String lifecycleKey) {
			caseDao.populateLifecycleDateIfNull(caseId, lifecycleKey);
		}

		@Override
		public void reorderCaseStatuses(int shaleClientId, int firstStatusId, int secondStatusId) {
			caseDao.reorderCaseStatuses(shaleClientId, firstStatusId, secondStatusId);
		}

		@Override
		public void updateCaseAssignment(long caseId, int shaleClientId, int practiceAreaId, int responsibleAttorneyUserId) {
			caseDao.updateCaseAssignment(caseId, shaleClientId, practiceAreaId, responsibleAttorneyUserId);
		}

		@Override public List<LinkTypeDto> listLinkTypes(int shaleClientId, boolean includeInactive) { return caseDao.listLinkTypes(shaleClientId, includeInactive); }
		@Override public List<LinkTypeDto> listLinkTypesForAdministration(int shaleClientId, int actorUserId) { return caseDao.listLinkTypesForAdministration(shaleClientId, actorUserId); }
		@Override public LinkTypeDto createLinkType(int shaleClientId, int actorUserId, String name, String color, boolean active, String systemKey) { return caseDao.createLinkType(shaleClientId, actorUserId, name, color, active, systemKey); }
		@Override public LinkTypeDto updateLinkType(int shaleClientId, int actorUserId, int linkTypeId, String name, String color, boolean active, String systemKey, byte[] expectedRowVer) { return caseDao.updateLinkType(shaleClientId, actorUserId, linkTypeId, name, color, active, systemKey, expectedRowVer); }
		@Override public LinkTypeDto setLinkTypeActive(int shaleClientId, int actorUserId, int linkTypeId, boolean active, byte[] expectedRowVer) { return caseDao.setLinkTypeActive(shaleClientId, actorUserId, linkTypeId, active, expectedRowVer); }
		@Override public void resetLinkTypeOverride(int shaleClientId, int actorUserId, int linkTypeId) { caseDao.resetLinkTypeOverride(shaleClientId, actorUserId, linkTypeId); }
		@Override public List<CaseLinkDto> listCaseLinks(long caseId, int shaleClientId) { return caseDao.listCaseLinks(caseId, shaleClientId); }
		@Override public Optional<CaseLinkDto> getPrimaryCaseLink(long caseId, int shaleClientId) { return caseDao.getPrimaryCaseLink(caseId, shaleClientId); }
		@Override public CaseLinkDto createCaseLink(int shaleClientId, int actorUserId, long caseId, int linkTypeId, String displayName, String url, String description, boolean primary, String notes, Integer sortOrder) { return caseDao.createCaseLink(shaleClientId, actorUserId, caseId, linkTypeId, displayName, url, description, primary, notes, sortOrder); }
		@Override public CaseLinkDto updateCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId, long externalLinkId, int linkTypeId, String displayName, String url, String description, Boolean primary, String notes, Integer sortOrder, byte[] expectedCaseLinkRowVer, byte[] expectedExternalLinkRowVer) { return caseDao.updateCaseLink(shaleClientId, actorUserId, caseId, caseLinkId, externalLinkId, linkTypeId, displayName, url, description, primary, notes, sortOrder, expectedCaseLinkRowVer, expectedExternalLinkRowVer); }
		@Override public CaseLinkDto setPrimaryCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId) { return caseDao.setPrimaryCaseLink(shaleClientId, actorUserId, caseId, caseLinkId); }
		@Override public List<CaseLinkDto> reorderCaseLinks(int shaleClientId, int actorUserId, long caseId, List<Long> orderedCaseLinkIds) { return caseDao.reorderCaseLinks(shaleClientId, actorUserId, caseId, orderedCaseLinkIds); }
		@Override public void deleteCaseLink(int shaleClientId, int actorUserId, long caseId, long caseLinkId, byte[] expectedCaseLinkRowVer) { caseDao.deleteCaseLink(shaleClientId, actorUserId, caseId, caseLinkId, expectedCaseLinkRowVer); }

		@Override
		public CaseDetailDto updateCase(long caseId, String name, String caseNumber, String description,
				LocalDate incidentDate, LocalDate solDate, LocalDate tortNoticeDeadline, String summary,
				byte[] expectedRowVer, Integer actorUserId) {
			return caseDao.updateCase(caseId, name, caseNumber, description, incidentDate, solDate, tortNoticeDeadline, summary, expectedRowVer, actorUserId);
		}

		@Override
		public long createBasicCase(CreateCaseCommand command, int statusId) {
			return caseDao.createBasicCase(command.shaleClientId(), command.caseName(), command.caseNumber(),
					command.callerDate(), command.practiceAreaId(), command.responsibleAttorneyUserId(),
					statusId, command.description(), command.summary(), command.dateOfInjury(),
					command.statuteOfLimitations(), command.tortNoticeDeadline(), command.actorUserId());
		}
	}
}
