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

	public record RelatedCaseRow(
			long id,
			String name,
			java.time.LocalDate intakeDate,
			java.time.LocalDate statuteOfLimitationsDate,
			String responsibleAttorneyName,
			String responsibleAttorneyColor
	) {
	}

	public record SelectableCaseRow(long id, String name) {
	}

	public record OrganizationTypeRow(int organizationTypeId, String name) {
	}

	public record OrganizationCreateRequest(
			int shaleClientId,
			int organizationTypeId,
			String name,
			String phone,
			String fax,
			String email,
			String website,
			String address1,
			String address2,
			String city,
			String state,
			String postalCode,
			String country,
			String notes
	) {
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
				WHERE o.ShaleClientId = ?
				  AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				  AND (? = '' OR o.Name LIKE ?)
				ORDER BY o.Name ASC, o.Id ASC
				OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
				""".formatted(ORGANIZATIONS_TABLE, ORGANIZATION_TYPES_TABLE);

		List<Organization> items = new ArrayList<>(pageSize);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int idx = 1;
			ps.setInt(idx++, requireCurrentShaleClientId(con));
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

	public Organization findById(int organizationId) {
		if (organizationId <= 0) {
			throw new IllegalArgumentException("organizationId must be > 0");
		}

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
				WHERE o.Id = ?
				  AND o.ShaleClientId = ?
				  AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL);
				""".formatted(ORGANIZATIONS_TABLE, ORGANIZATION_TYPES_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int idx = 1;
			ps.setInt(idx++, organizationId);
			ps.setInt(idx++, requireCurrentShaleClientId(con));

			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return mapOrganization(rs);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load organization by id (id=" + organizationId + ")", e);
		}
	}

	public int create(OrganizationCreateRequest request) {
		Objects.requireNonNull(request, "request");
		if (request.shaleClientId() <= 0) {
			throw new IllegalArgumentException("shaleClientId is required");
		}
		if (request.organizationTypeId() <= 0) {
			throw new IllegalArgumentException("organizationTypeId is required");
		}
		if (request.name() == null || request.name().isBlank()) {
			throw new IllegalArgumentException("name is required");
		}

		String sql = """
				INSERT INTO %s (
				  ShaleClientId,
				  OrganizationTypeId,
				  Name,
				  Phone,
				  Fax,
				  Email,
				  Website,
				  Address1,
				  Address2,
				  City,
				  State,
				  PostalCode,
				  Country,
				  Notes,
				  IsDeleted,
				  CreatedAt,
				  UpdatedAt
				)
				OUTPUT INSERTED.Id
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?);
				""".formatted(ORGANIZATIONS_TABLE);

		Timestamp now = Timestamp.from(Instant.now());
		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int currentShaleClientId = requireCurrentShaleClientId(con);
			if (request.shaleClientId() != currentShaleClientId) {
				throw new IllegalArgumentException("shaleClientId does not match current session");
			}

			int idx = 1;
			ps.setInt(idx++, request.shaleClientId());
			ps.setInt(idx++, request.organizationTypeId());
			setNullableString(ps, idx++, request.name());
			setNullableString(ps, idx++, request.phone());
			setNullableString(ps, idx++, request.fax());
			setNullableString(ps, idx++, request.email());
			setNullableString(ps, idx++, request.website());
			setNullableString(ps, idx++, request.address1());
			setNullableString(ps, idx++, request.address2());
			setNullableString(ps, idx++, request.city());
			setNullableString(ps, idx++, request.state());
			setNullableString(ps, idx++, request.postalCode());
			setNullableString(ps, idx++, request.country());
			setNullableString(ps, idx++, request.notes());
			ps.setTimestamp(idx++, now);
			ps.setTimestamp(idx++, now);

			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					throw new RuntimeException("Failed to create organization");
				}
				return rs.getInt(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to create organization", e);
		}
	}

	public void update(Organization organization) {
		Objects.requireNonNull(organization, "organization");
		if (organization.getId() == null || organization.getId() <= 0) {
			throw new IllegalArgumentException("organization.id is required");
		}

		String sql = """
				UPDATE %s
				SET
				  Name = ?,
				  OrganizationTypeId = ?,
				  Phone = ?,
				  Fax = ?,
				  Email = ?,
				  Website = ?,
				  Address1 = ?,
				  Address2 = ?,
				  City = ?,
				  State = ?,
				  PostalCode = ?,
				  Country = ?,
				  Notes = ?,
				  UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND (IsDeleted = 0 OR IsDeleted IS NULL);
				""".formatted(ORGANIZATIONS_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int idx = 1;
			ps.setString(idx++, organization.getName());
			if (organization.getOrganizationTypeId() == null) {
				ps.setNull(idx++, java.sql.Types.INTEGER);
			} else {
				ps.setInt(idx++, organization.getOrganizationTypeId());
			}
			ps.setString(idx++, organization.getPhone());
			ps.setString(idx++, organization.getFax());
			ps.setString(idx++, organization.getEmail());
			ps.setString(idx++, organization.getWebsite());
			ps.setString(idx++, organization.getAddress1());
			ps.setString(idx++, organization.getAddress2());
			ps.setString(idx++, organization.getCity());
			ps.setString(idx++, organization.getState());
			ps.setString(idx++, organization.getPostalCode());
			ps.setString(idx++, organization.getCountry());
			ps.setString(idx++, organization.getNotes());
			ps.setInt(idx++, organization.getId());
			ps.setInt(idx++, requireCurrentShaleClientId(con));

			int affected = ps.executeUpdate();
			if (affected == 0) {
				throw new RuntimeException("Organization not found or cannot be updated (id=" + organization.getId() + ")");
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update organization (id=" + organization.getId() + ")", e);
		}
	}



	public List<SelectableCaseRow> findLinkableCases(int organizationId) {
		if (organizationId <= 0) {
			throw new IllegalArgumentException("organizationId must be > 0");
		}

		String sql = """
				SELECT c.Id, c.Name
				FROM Cases c
				LEFT JOIN CaseOrganizations co
				  ON co.CaseId = c.Id
				 AND co.OrganizationId = ?
				WHERE c.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  AND co.CaseId IS NULL
				ORDER BY c.Name ASC, c.Id ASC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setInt(idx++, organizationId);
			ps.setInt(idx++, shaleClientId);

			List<SelectableCaseRow> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new SelectableCaseRow(rs.getLong("Id"), rs.getString("Name")));
				}
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load linkable cases for organization (id=" + organizationId + ")", e);
		}
	}

	public boolean linkCaseToOrganization(int organizationId, long caseId) {
		if (organizationId <= 0)
			throw new IllegalArgumentException("organizationId must be > 0");
		if (caseId <= 0)
			throw new IllegalArgumentException("caseId must be > 0");

		String sql = """
				INSERT INTO CaseOrganizations (
				  CaseId,
				  OrganizationId,
				  RoleId,
				  IsPrimary,
				  Notes,
				  CreatedAt,
				  UpdatedAt
				)
				SELECT
				  ?,
				  ?,
				  NULL,
				  0,
				  NULL,
				  SYSUTCDATETIME(),
				  SYSUTCDATETIME()
				WHERE EXISTS (
				    SELECT 1
				    FROM Cases c
				    WHERE c.Id = ?
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				)
				  AND EXISTS (
				    SELECT 1
				    FROM Organizations o
				    WHERE o.Id = ?
				      AND o.ShaleClientId = ?
				      AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				)
				  AND NOT EXISTS (
				    SELECT 1
				    FROM CaseOrganizations co
				    WHERE co.CaseId = ?
				      AND co.OrganizationId = ?
				  );
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, organizationId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setInt(idx++, organizationId);
			ps.setInt(idx++, shaleClientId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, organizationId);

			return ps.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to link case to organization (orgId=" + organizationId + ", caseId=" + caseId + ")", e);
		}
	}


	public boolean unlinkCaseFromOrganization(int organizationId, long caseId) {
		if (organizationId <= 0)
			throw new IllegalArgumentException("organizationId must be > 0");
		if (caseId <= 0)
			throw new IllegalArgumentException("caseId must be > 0");

		String sql = """
				DELETE co
				FROM CaseOrganizations co
				WHERE co.OrganizationId = ?
				  AND co.CaseId = ?
				  AND EXISTS (
				    SELECT 1
				    FROM Cases c
				    WHERE c.Id = co.CaseId
				      AND c.ShaleClientId = ?
				      AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  )
				  AND EXISTS (
				    SELECT 1
				    FROM Organizations o
				    WHERE o.Id = co.OrganizationId
				      AND o.ShaleClientId = ?
				      AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				  );
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setInt(idx++, organizationId);
			ps.setLong(idx++, caseId);
			ps.setInt(idx++, shaleClientId);
			ps.setInt(idx++, shaleClientId);

			return ps.executeUpdate() > 0;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to unlink case from organization (orgId=" + organizationId + ", caseId=" + caseId + ")", e);
		}
	}

	public List<RelatedCaseRow> findRelatedCases(int organizationId) {
		if (organizationId <= 0) {
			throw new IllegalArgumentException("organizationId must be > 0");
		}

		String sql = """
				SELECT
				  c.Id,
				  c.Name,
				  c.CallerDate,
				  c.StatuteOfLimitations,
				  u.color AS ResponsibleAttorneyColor,
				  LTRIM(RTRIM(
				    COALESCE(u.name_first, '') +
				    CASE WHEN COALESCE(u.name_first, '') = '' OR COALESCE(u.name_last, '') = '' THEN '' ELSE ' ' END +
				    COALESCE(u.name_last, '')
				  )) AS ResponsibleAttorneyName
				FROM CaseOrganizations co
				INNER JOIN Cases c
				  ON c.Id = co.CaseId
				OUTER APPLY (
				    SELECT TOP (1) cu.UserId
				    FROM CaseUsers cu
				    WHERE cu.CaseId = c.Id
				      AND cu.RoleId = 4
				      AND cu.IsPrimary = 1
				    ORDER BY cu.UpdatedAt DESC, cu.CreatedAt DESC, cu.Id DESC
				) ra
				LEFT JOIN Users u
				  ON u.Id = ra.UserId
				WHERE co.OrganizationId = ?
				  AND c.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				ORDER BY c.Name ASC, c.Id ASC;
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int shaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			ps.setInt(idx++, organizationId);
			ps.setInt(idx++, shaleClientId);

			List<RelatedCaseRow> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new RelatedCaseRow(
						rs.getLong("Id"),
						rs.getString("Name"),
						toLocalDate(rs.getDate("CallerDate")),
						toLocalDate(rs.getDate("StatuteOfLimitations")),
						rs.getString("ResponsibleAttorneyName"),
						rs.getString("ResponsibleAttorneyColor")
					));
				}
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load related cases for organization (id=" + organizationId + ")", e);
		}
	}


	public List<OrganizationTypeRow> findOrganizationTypes() {
		String sql = """
				SELECT ot.OrganizationTypeId, ot.Name
				FROM %s ot
				WHERE ot.ShaleClientId = ?
				ORDER BY ot.Name ASC, ot.OrganizationTypeId ASC;
				""".formatted(ORGANIZATION_TYPES_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, requireCurrentShaleClientId(con));

			List<OrganizationTypeRow> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new OrganizationTypeRow(rs.getInt("OrganizationTypeId"), rs.getString("Name")));
				}
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load organization types", e);
		}
	}

	public long countAll(String searchName) {
		String normalizedSearch = normalizeSearch(searchName);

		String sql = """
				SELECT COUNT(1)
				FROM %s o
				WHERE o.ShaleClientId = ?
				  AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				  AND (? = '' OR o.Name LIKE ?);
				""".formatted(ORGANIZATIONS_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

			int idx = 1;
			ps.setInt(idx++, requireCurrentShaleClientId(con));
			ps.setString(idx++, normalizedSearch);
			ps.setString(idx++, containsPattern(normalizedSearch));

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


	private static int requireCurrentShaleClientId(Connection con) throws SQLException {
		String sql = "SELECT CAST(SESSION_CONTEXT(N'ShaleClientId') AS INT);";
		try (PreparedStatement ps = con.prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			if (!rs.next()) {
				throw new IllegalStateException("ShaleClientId session context is missing.");
			}

			Integer shaleClientId = getNullableInt(rs, 1);
			if (shaleClientId == null || shaleClientId <= 0) {
				throw new IllegalStateException("ShaleClientId session context is missing.");
			}
			return shaleClientId;
		}
	}

	private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
		if (value == null || value.isBlank()) {
			ps.setNull(index, java.sql.Types.NVARCHAR);
			return;
		}
		ps.setString(index, value.trim());
	}

	private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
		int value = rs.getInt(col);
		return rs.wasNull() ? null : value;
	}


	private static Integer getNullableInt(ResultSet rs, int colIndex) throws SQLException {
		int value = rs.getInt(colIndex);
		return rs.wasNull() ? null : value;
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

	private static java.time.LocalDate toLocalDate(java.sql.Date d) {
		return d == null ? null : d.toLocalDate();
	}
}
