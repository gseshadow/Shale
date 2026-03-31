package com.shale.ui.document;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.data.dao.CaseDao;
import com.shale.data.dao.ContactDao;

public final class CaseDocumentService {
    private final CaseDao caseDao;
    private final ContactDao contactDao;
    private final CaseDocumentRenderer renderer;

    public CaseDocumentService(CaseDao caseDao, ContactDao contactDao) {
        this(caseDao, contactDao, new CaseDocumentRenderer());
    }

    public CaseDocumentService(CaseDao caseDao, ContactDao contactDao, CaseDocumentRenderer renderer) {
        this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
        this.contactDao = Objects.requireNonNull(contactDao, "contactDao");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public String generateCaseDocumentHtml(int caseId, int shaleClientId, CaseDocumentType type) {
        return renderer.render(buildCaseDocumentModel(caseId, shaleClientId, type), type, LocalDateTime.now());
    }

    public CaseDocumentModel buildCaseDocumentModel(int caseId, int shaleClientId, CaseDocumentType type) {
        if (caseId <= 0 || shaleClientId <= 0) throw new IllegalArgumentException("caseId and shaleClientId must be > 0");
        if (type != CaseDocumentType.CASE_SUMMARY) throw new IllegalArgumentException("Unsupported type: " + type);

        CaseOverviewDto overview = caseDao.getOverview(caseId);
        CaseDetailDto detail = caseDao.getDetail(caseId);
        if (overview == null || detail == null) throw new IllegalStateException("Case not found for id=" + caseId);

        ContactDao.ContactDetailRow caller = loadContact(overview.getPrimaryCallerContactId(), shaleClientId);
        ContactDao.ContactDetailRow client = loadContact(overview.getPrimaryClientContactId(), shaleClientId);

        List<CaseDocumentModel.UpdateEntry> updates = caseDao.listCaseUpdates(caseId).stream()
                .sorted(Comparator
                        .comparing(CaseUpdateDto::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingLong(CaseUpdateDto::getId))
                .map(this::toUpdateEntry)
                .toList();

        return new CaseDocumentModel(
                overview.getCaseName(),
                overview.getCaseStatus(),
                overview.getPracticeArea(),
                coalesceName(overview.getCaller(), caller),
                caller == null ? "" : caller.phone(),
                caller == null ? "" : caller.addressHome(),
                caller == null ? "" : caller.email(),
                coalesceName(overview.getClient(), client),
                client == null ? "" : client.phone(),
                client == null ? "" : client.addressHome(),
                client == null ? "" : client.email(),
                overview.getIncidentDate(),
                overview.getSolDate(),
                detail.getAcceptedDate(),
                detail.getDeniedDate(),
                detail.getClosedDate(),
                overview.getDescription(),
                detail.getSummary(),
                updates);
    }

    private ContactDao.ContactDetailRow loadContact(Integer contactId, int shaleClientId) {
        if (contactId == null || contactId <= 0) {
            return null;
        }
        return contactDao.findById(contactId, shaleClientId);
    }

    private String coalesceName(String preferredName, ContactDao.ContactDetailRow detail) {
        if (preferredName != null && !preferredName.isBlank()) {
            return preferredName;
        }
        if (detail == null) {
            return "";
        }
        return detail.displayName();
    }

    private CaseDocumentModel.UpdateEntry toUpdateEntry(CaseUpdateDto dto) {
        return new CaseDocumentModel.UpdateEntry(dto.getCreatedAt(), dto.getCreatedByDisplayName(), dto.getNoteText());
    }
}
