package com.shale.ui.services;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.shale.ui.util.PerfLog;

import com.shale.data.dao.ContactDao;
import com.shale.data.dao.CaseSummaryDao;
import com.shale.data.dao.ContactDao.ContactDetailRow;
import com.shale.data.dao.ContactDao.ContactProfileUpdateRequest;
import com.shale.data.dao.CaseSummaryDao.RelatedCaseRow;

public final class ContactDetailService {

    public record ContactDetailSnapshot(ContactDetailRow contact, List<RelatedCaseRow> relatedCases) {
        public ContactDetailSnapshot {
            relatedCases = List.copyOf(relatedCases == null ? List.of() : relatedCases);
        }
    }

    private record CacheKey(int contactId, int shaleClientId) {}

    private final ContactDao contactDao;
    private final CaseSummaryDao caseSummaryDao;
    private final Map<CacheKey, ContactDetailSnapshot> sessionCache = new ConcurrentHashMap<>();

    public ContactDetailService(ContactDao contactDao, CaseSummaryDao caseSummaryDao) {
        this.contactDao = Objects.requireNonNull(contactDao, "contactDao");
        this.caseSummaryDao = Objects.requireNonNull(caseSummaryDao, "caseSummaryDao");
    }

    public ContactDetailRow loadContact(int contactId, int shaleClientId) {
        long started = PerfLog.start();
        ContactDetailRow row = contactDao.findById(contactId, shaleClientId);
        PerfLog.logDone("contacts.detail.dao", "operation=loadContact contactId=" + contactId + " tenantId=" + shaleClientId + " found=" + (row != null), started);
        return row;
    }

    public List<RelatedCaseRow> loadRelatedCases(int contactId, int shaleClientId) {
        long started = PerfLog.start();
        List<RelatedCaseRow> rows = caseSummaryDao.listActiveRelatedToContact(shaleClientId, contactId);
        PerfLog.logDone("contacts.relatedCases.dao", "operation=loadRelatedCases contactId=" + contactId + " tenantId=" + shaleClientId + " rows=" + (rows == null ? 0 : rows.size()), started);
        return rows;
    }

    public ContactDetailSnapshot loadSnapshot(int contactId, int shaleClientId) {
        CacheKey key = new CacheKey(contactId, shaleClientId);
        ContactDetailSnapshot cached = sessionCache.get(key);
        if (cached != null) {
            PerfLog.log("contacts.detail.cache", "hit", "contactId=" + contactId + " tenantId=" + shaleClientId + " relatedCases=" + cached.relatedCases().size());
            return cached;
        }
        long started = PerfLog.start();
        ContactDetailRow contact = loadContact(contactId, shaleClientId);
        List<RelatedCaseRow> cases = contact == null ? List.of() : loadRelatedCases(contactId, shaleClientId);
        ContactDetailSnapshot snapshot = new ContactDetailSnapshot(contact, cases);
        if (contact != null) {
            sessionCache.put(key, snapshot);
        }
        PerfLog.logDone("contacts.detail.snapshot", "contactId=" + contactId + " tenantId=" + shaleClientId + " found=" + (contact != null) + " relatedCases=" + snapshot.relatedCases().size(), started);
        return snapshot;
    }

    public boolean updateBasicProfile(ContactProfileUpdateRequest request) {
        long started = PerfLog.start();
        boolean updated = contactDao.updateBasicProfile(request);
        if (updated) {
            invalidate(request.contactId(), request.shaleClientId());
        }
        PerfLog.logDone("contacts.save.dao", "operation=updateBasicProfile contactId=" + request.contactId() + " tenantId=" + request.shaleClientId() + " updated=" + updated, started);
        return updated;
    }

    public boolean softDeleteContact(int contactId, int shaleClientId) {
        long started = PerfLog.start();
        boolean deleted = contactDao.softDeleteContact(contactId, shaleClientId);
        if (deleted) {
            invalidate(contactId, shaleClientId);
        }
        PerfLog.logDone("contacts.delete.dao", "operation=softDelete contactId=" + contactId + " tenantId=" + shaleClientId + " deleted=" + deleted, started);
        return deleted;
    }

    private void invalidate(int contactId, int shaleClientId) {
        sessionCache.remove(new CacheKey(contactId, shaleClientId));
    }
}
