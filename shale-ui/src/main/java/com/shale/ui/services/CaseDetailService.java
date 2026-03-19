package com.shale.ui.services;

import java.util.Objects;

import com.shale.data.dao.CaseDao;
import com.shale.ui.state.AppState;

public final class CaseDetailService {

    private final CaseDao caseDao;
    private final AppState appState;

    public CaseDetailService(CaseDao caseDao, AppState appState) {
        this.caseDao = Objects.requireNonNull(caseDao, "caseDao");
        this.appState = Objects.requireNonNull(appState, "appState");
    }

    public boolean canDeleteCase() {
        return appState.isAdmin() || appState.isAttorney();
    }

    public boolean softDeleteCase(long caseId, Integer shaleClientId) {
        if (!canDeleteCase()) {
            throw new IllegalStateException("Only admin and attorney users can delete cases.");
        }
        return caseDao.softDeleteCase(caseId, shaleClientId);
    }
}
