package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Map;

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
		assertFalse(typeMethod.contains("WHERE ot.ShaleClientId"),
				"the legacy compatibility selector remains unchanged and is not cut over to overlay filtering");
    }

	@Test
    void phaseOneBReadsEncodeOverlayHistoricalIsolationOrderingAndReadOnlyBoundaries() throws Exception {
		String source = Files.readString(Path.of("src/main/java/com/shale/data/dao/OrganizationDao.java"))
				.replace("\r\n", "\n").replace('\r', '\n');
		String effective = methodBody(source, "public List<OrganizationTypeDefinitionRow> listEffectiveOrganizationTypeDefinitions(");
		String profile = methodBody(source, "public OrganizationTypeProfileRow findOrganizationTypeProfile(");

		assertTrue(effective.contains("PARTITION BY ot.SystemKey"), "display names cannot determine overlay identity");
		assertTrue(effective.contains("ot.IsDeleted=0"), "deleted overrides reset to the global definition");
		assertTrue(effective.contains("WHERE rn=1 AND IsActive=1"), "inactive tenant winners mask globals");
		assertTrue(effective.contains("ORDER BY SortOrder,Name,OrganizationTypeId"));
		assertTrue(effective.contains("ot.ShaleClientId=? OR ot.ShaleClientId IS NULL"));
		assertTrue(profile.contains("a.ShaleClientId=o.ShaleClientId AND a.IsDeleted=0"));
		assertTrue(profile.contains("ot.OrganizationTypeId=a.OrganizationTypeId"), "stored IDs remain authoritative");
		assertFalse(profile.contains("ot.SystemKey="), "historical assignments must not resolve through an overlay key");
		assertTrue(profile.contains("o.Id=? AND o.ShaleClientId=?"));
		assertTrue(profile.contains("ot.ShaleClientId IS NULL OR ot.ShaleClientId=o.ShaleClientId"));
		assertTrue(profile.contains("ORDER BY a.IsPrimary DESC,a.SortOrder,ot.SortOrder,ot.Name,a.Id"));
		assertFalse((effective + profile).matches("(?is).*\\b(?:INSERT|UPDATE|DELETE|MERGE)\\s+(?:INTO\\s+)?dbo\\..*"),
				"Phase 1B methods contain no mutation SQL");
		assertTrue(source.contains("value.intValue()"));
		assertTrue(source.contains("value.longValue()"));
	}

	@Test
	void definitionMappingAcceptsEveryJdbcNumberImplementationAndDefensivelyCopiesRowVersion() throws Exception {
		Method mapper = OrganizationDao.class.getDeclaredMethod("mapOrganizationTypeDefinition", ResultSet.class);
		mapper.setAccessible(true);
		for (Number id : new Number[] { Integer.valueOf(7), Long.valueOf(7), new BigDecimal("7") }) {
			byte[] rowVer = {1, 2};
			Map<String, Object> values = Map.ofEntries(
					Map.entry("OrganizationTypeId", id), Map.entry("ShaleClientId", Long.valueOf(41)),
					Map.entry("SystemKey", "provider"), Map.entry("Name", "Provider"),
					Map.entry("Description", "Care provider"), Map.entry("Color", "#123456"),
					Map.entry("SortOrder", new BigDecimal("3")), Map.entry("IsActive", true),
					Map.entry("IsDeleted", false), Map.entry("RowVer", rowVer));
			ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { ResultSet.class }, (proxy, method, args) -> switch (method.getName()) {
						case "getObject" -> values.get(String.valueOf(args[0]));
						case "getString" -> values.get(String.valueOf(args[0]));
						case "getBoolean" -> values.get(String.valueOf(args[0]));
						case "getBytes" -> values.get(String.valueOf(args[0]));
						case "close" -> null;
						default -> throw new UnsupportedOperationException(method.getName());
					});
			var mapped = (OrganizationDao.OrganizationTypeDefinitionRow) mapper.invoke(null, resultSet);
			rowVer[0] = 9;
			assertTrue(mapped.organizationTypeId() == 7 && mapped.shaleClientId() == 41 && mapped.sortOrder() == 3,
					"all JDBC Number variants map through Number conversion");
			assertTrue(mapped.rowVer()[0] == 1, "mapped RowVer is not exposed as a mutable JDBC byte array");
		}
	}

	private static String methodBody(String source, String signature) {
		int start = source.indexOf(signature);
		assertTrue(start >= 0, "method signature not found: " + signature);
		int opening = source.indexOf('{', start);
		int depth = 0;
		for (int i = opening; i < source.length(); i++) {
			if (source.charAt(i) == '{') depth++;
			if (source.charAt(i) == '}' && --depth == 0) return source.substring(opening, i + 1);
		}
		throw new AssertionError("unbalanced method: " + signature);
	}
}
