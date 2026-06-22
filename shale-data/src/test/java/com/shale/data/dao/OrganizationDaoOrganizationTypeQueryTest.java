package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class OrganizationDaoOrganizationTypeQueryTest {

    @Test
    void organizationSearchQueriesDoNotAssumeTenantColumnOnOrganizationTypes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/OrganizationDao.java"));
        String searchMethod = source.substring(
                source.indexOf("public List<Organization> searchOrganizations"),
                source.indexOf("/** Lightweight directory/list page"));
        String directoryMethod = source.substring(
                source.indexOf("public PagedResult<DirectoryOrganizationRow> findDirectoryPage"),
                source.indexOf("/** page is 0-based */", source.indexOf("public PagedResult<DirectoryOrganizationRow> findDirectoryPage")));
        String detailMethod = source.substring(
                source.indexOf("public Organization findById"),
                source.indexOf("public int create"));
        String typeMethod = source.substring(
                source.indexOf("public List<OrganizationTypeRow> findOrganizationTypes"),
                source.indexOf("public List<OrganizationOptionRow> findSelectableOrganizations"));

        assertTrue(searchMethod.contains("LEFT JOIN %s ot"));
        assertTrue(directoryMethod.contains("LEFT JOIN %s ot"));
        assertTrue(detailMethod.contains("LEFT JOIN %s ot"));
        assertFalse(searchMethod.contains("ot.ShaleClientId"));
        assertFalse(directoryMethod.contains("ot.ShaleClientId"));
        assertFalse(detailMethod.contains("ot.ShaleClientId"));
        assertFalse(typeMethod.contains("ot.ShaleClientId"));
    }
}
