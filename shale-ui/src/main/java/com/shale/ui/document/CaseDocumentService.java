package com.shale.ui.document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.shale.core.dto.CaseOverviewDto;
import com.shale.data.dao.CaseDao;

public final class CaseDocumentService {
    private final CaseDao caseDao;
    private final CaseDocumentRenderer renderer;

    public CaseDocumentService(CaseDao caseDao) {
        this(caseDao, new CaseDocumentRenderer());
    }

    public CaseDocumentService(CaseDao caseDao, CaseDocumentRenderer renderer) {
        this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    public String generateCaseDocumentHtml(int caseId, int shaleClientId, CaseDocumentType type) {
        return renderer.render(buildCaseDocumentModel(caseId, shaleClientId, type), type, LocalDateTime.now());
    }

    public CaseDocumentModel buildCaseDocumentModel(int caseId, int shaleClientId, CaseDocumentType type) {
        if (caseId <= 0 || shaleClientId <= 0) throw new IllegalArgumentException("caseId and shaleClientId must be > 0");
        if (type != CaseDocumentType.CASE_SUMMARY) throw new IllegalArgumentException("Unsupported type: " + type);

        CaseOverviewDto overview = caseDao.getOverview(caseId);
        if (overview == null) throw new IllegalStateException("Case not found for id=" + caseId);

        List<CaseDocumentModel.TeamEntry> team = caseDao.listCaseTeamRows(caseId).stream()
                .filter(Objects::nonNull)
                .map(row -> new CaseDocumentModel.TeamEntry(row.displayName(), roleLabel(row.roleId())))
                .toList();

        List<CaseDocumentModel.OrganizationEntry> orgs = caseDao.findRelatedOrganizations(caseId).stream()
                .filter(Objects::nonNull)
                .map(row -> new CaseDocumentModel.OrganizationEntry(row.name()))
                .toList();

        return new CaseDocumentModel(caseId, overview.getCaseName(), overview.getCaseNumber(), overview.getCaseStatus(),
                overview.getResponsibleAttorney(), overview.getPracticeArea(), overview.getCaller(),
                overview.getClient(), overview.getOpposingCounsel(), overview.getIncidentDate(),
                overview.getSolDate(), overview.getDescription(), team, orgs);
    }

    private String roleLabel(int roleId) {
        return switch (roleId) {
            case 4 -> "Responsible Attorney";
            case 5 -> "Intake Staff";
            case 7 -> "Attorney";
            case 11 -> "Legal Assistant";
            case 12 -> "Paralegal";
            case 13 -> "Law Clerk";
            case 14 -> "Co-counsel";
            default -> "";
        };
    }
}
