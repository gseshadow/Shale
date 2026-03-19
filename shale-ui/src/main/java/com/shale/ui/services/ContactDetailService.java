package com.shale.ui.services;

import java.util.List;
import java.util.Objects;

import com.shale.data.dao.ContactDao;
import com.shale.data.dao.ContactDao.ContactDetailRow;
import com.shale.data.dao.ContactDao.ContactProfileUpdateRequest;
import com.shale.data.dao.ContactDao.RelatedCaseRow;

public final class ContactDetailService {

    private final ContactDao contactDao;

    public ContactDetailService(ContactDao contactDao) {
        this.contactDao = Objects.requireNonNull(contactDao, "contactDao");
    }

    public ContactDetailRow loadContact(int contactId, int shaleClientId) {
        return contactDao.findById(contactId, shaleClientId);
    }

    public List<RelatedCaseRow> loadRelatedCases(int contactId, int shaleClientId) {
        return contactDao.findRelatedCases(contactId, shaleClientId);
    }

    public boolean updateBasicProfile(ContactProfileUpdateRequest request) {
        return contactDao.updateBasicProfile(request);
    }

    public boolean softDeleteContact(int contactId, int shaleClientId) {
        return contactDao.softDeleteContact(contactId, shaleClientId);
    }
}
