package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.shale.core.model.Organization;
import com.shale.core.runtime.DbSessionProvider;

public final class OrganizationDao {

	private static final String ORGANIZATIONS_TABLE = "Organizations";
	private static final String ORGANIZATION_TYPES_TABLE = "OrganizationTypes";

	private final DbSessionProvider db;

	public OrganizationDao(DbSessionProvider dbSessionProvider) {
		this.db = Objects.requireNonNull(dbSessionProvider, "dbSessionProvider");
	}

	public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
	}

	/** page is 0-based */
	public PagedResult<Organization> findPage(int page, int pageSize) {
		return findPage(page, pageSize, null);
	}

	/** page is 0-based */
	public PagedResult<Organization> findPage(int page, int pageSize, String searchName) {
		if (page < 0)
			throw new IllegalArgumentException("page must be >= 0");
		if (pageSize <= 0)
			throw new IllegalArgumentException("pageSize must be > 0");

		String normalizedSearch = normalizeSearch(searchName);
		long total = countAll(normalizedSearch);
		if (total == 0) {
			return new PagedResult<>(List.of(), page, pageSize, 0);
		}

		int offset = page * pageSize;
		String sql = """
				SELECT
				  o.Id,
				  o.ShaleClientId,
				  o.OrganizationTypeId,
				  ot.Name AS OrganizationTypeName,
				  o.Name,
				  o.Phone,
				  o.Fax,
				  o.Email,
				  o.Website,
				  o.Address1,
				  o.Address2,
				  o.City,
				  o.State,
				  o.PostalCode,
				  o.Country,
				  o.Notes,
				  o.IsDeleted,
				  o.CreatedAt,
				  o.UpdatedAt
				FROM %s o
				LEFT JOIN %s ot
				  ON ot.OrganizationTypeId = o.OrganizationTypeId
				 AND ot.ShaleClientId = o.ShaleClientId
				WHERE (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				  AND (? = '' OR o.Name LIKE ?)
				ORDER BY o.Name ASC, o.Id ASC
				OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
				""".formatted(ORGANIZATIONS_TABLE, ORGANIZATION_TYPES_TABLE);

		List<Organization> items = new ArrayList<>(pageSize);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int idx = 1;
			ps.setString(idx++, normalizedSearch);
			ps.setString(idx++, containsPattern(normalizedSearch));
			ps.setInt(idx++, offset);
			ps.setInt(idx++, pageSize);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					items.add(mapOrganization(rs));
				}
			}

			return new PagedResult<>(items, page, pageSize, total);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load organizations page (page=" + page + ", pageSize=" + pageSize + ")", e);
		}
	}

	public long countAll() {
		return countAll(null);
	}

	public long countAll(String searchName) {
		String normalizedSearch = normalizeSearch(searchName);

		String sql = """
				SELECT COUNT(1)
				FROM %s o
				WHERE (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				  AND (? = '' OR o.Name LIKE ?);
				""".formatted(ORGANIZATIONS_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			ps.setString(1, normalizedSearch);
			ps.setString(2, containsPattern(normalizedSearch));

			try (ResultSet rs = ps.executeQuery()) {
				rs.next();
				return rs.getLong(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to count organizations", e);
		}
	}

	private static Organization mapOrganization(ResultSet rs) throws SQLException {
		return Organization.builder()
				.id(getNullableInt(rs, "Id"))
				.shaleClientId(getNullableInt(rs, "ShaleClientId"))
				.organizationTypeId(getNullableInt(rs, "OrganizationTypeId"))
				.organizationTypeName(rs.getString("OrganizationTypeName"))
				.name(rs.getString("Name"))
				.phone(rs.getString("Phone"))
				.fax(rs.getString("Fax"))
				.email(rs.getString("Email"))
				.website(rs.getString("Website"))
				.address1(rs.getString("Address1"))
				.address2(rs.getString("Address2"))
				.city(rs.getString("City"))
				.state(rs.getString("State"))
				.postalCode(rs.getString("PostalCode"))
				.country(rs.getString("Country"))
				.notes(rs.getString("Notes"))
				.deleted(rs.getBoolean("IsDeleted"))
				.createdAt(toInstant(rs.getTimestamp("CreatedAt")))
				.updatedAt(toInstant(rs.getTimestamp("UpdatedAt")))
				.build();
	}

	private static String normalizeSearch(String searchName) {
		if (searchName == null) {
			return "";
		}
		return searchName.trim();
	}

	private static String containsPattern(String normalizedSearch) {
		if (normalizedSearch == null || normalizedSearch.isBlank()) {
			return "%";
		}
		return "%" + normalizedSearch + "%";
	}

	private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
		int value = rs.getInt(col);
		return rs.wasNull() ? null : value;
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}
}
