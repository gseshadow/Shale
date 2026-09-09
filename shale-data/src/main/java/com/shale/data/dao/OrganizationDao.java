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
import com.shale.core.util.PerformanceLogging;

public final class OrganizationDao {

	private static final String ORGANIZATIONS_TABLE = "Organizations";
	private static final String ORGANIZATION_TYPES_TABLE = "OrganizationTypes";

	private final DbSessionProvider db;

	public OrganizationDao(DbSessionProvider dbSessionProvider) {
		this.db = Objects.requireNonNull(dbSessionProvider, "dbSessionProvider");
	}

	public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
	}


	public record SelectableCaseRow(long id, String name) {
	}

	public record OrganizationTypeRow(int organizationTypeId, String name) {
	}

	public record OrganizationTypeDefinitionRow(int organizationTypeId, Integer shaleClientId, String systemKey,
			String name, String description, String color, int sortOrder, boolean active, boolean deleted,
			byte[] rowVer) {
		public OrganizationTypeDefinitionRow { rowVer = rowVer == null ? null : rowVer.clone(); }
		@Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
	}

	public record AssignedOrganizationTypeRow(long assignmentId, int organizationTypeId, boolean primary,
			int sortOrder, OrganizationTypeDefinitionRow definition, byte[] rowVer) {
		public AssignedOrganizationTypeRow { rowVer = rowVer == null ? null : rowVer.clone(); }
		@Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
	}

	public record OrganizationTypeProfileRow(int organizationId, int shaleClientId,
			Integer compatibilityOrganizationTypeId, boolean compatibilityConsistent,
			List<AssignedOrganizationTypeRow> assignments) {
		public OrganizationTypeProfileRow { assignments = List.copyOf(assignments); }
	}

	public record OrganizationOptionRow(Integer organizationId, String name) {
	}

	public record DirectoryOrganizationRow(
			Integer id,
			String name,
			Integer organizationTypeId,
			String organizationTypeName,
			String phone,
			String email,
			String website,
			String city,
			String state
	) {
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

	public List<Organization> searchOrganizations(String query) {
		String normalizedSearch = normalizeSearch(query);
		if (normalizedSearch.isBlank()) {
			return List.of();
		}
		String phoneDigits = normalizePhoneDigits(query);

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
				WHERE o.ShaleClientId = ?
				  AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				  AND (
				    LOWER(COALESCE(o.Name, '')) LIKE ?
				    OR LOWER(COALESCE(o.Email, '')) LIKE ?
				    OR (? <> '' AND %s LIKE ?)
				    OR (? <> '' AND %s LIKE ?)
				  )
				ORDER BY o.Name ASC, o.Id ASC;
				""".formatted(ORGANIZATIONS_TABLE, ORGANIZATION_TYPES_TABLE, phoneDigitsExpression("o.Phone"), phoneDigitsExpression("o.Fax"));

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			String likeValue = containsPattern(normalizedSearch.toLowerCase(java.util.Locale.ROOT));
			String phoneLikeValue = containsPattern(phoneDigits);
			ps.setInt(1, requireCurrentShaleClientId(con));
			ps.setString(2, likeValue);
			ps.setString(3, likeValue);
			ps.setString(4, phoneDigits);
			ps.setString(5, phoneLikeValue);
			ps.setString(6, phoneDigits);
			ps.setString(7, phoneLikeValue);
			try (ResultSet rs = ps.executeQuery()) {
				List<Organization> out = new ArrayList<>();
				while (rs.next()) {
					out.add(mapOrganization(rs));
				}
				return out;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to search organizations", e);
		}
	}

	/** Lightweight directory/list page. Keeps organization cards from hydrating full detail columns. */
	public PagedResult<DirectoryOrganizationRow> findDirectoryPage(int page, int pageSize, String searchName) {
		if (page < 0)
			throw new IllegalArgumentException("page must be >= 0");
		if (pageSize <= 0)
			throw new IllegalArgumentException("pageSize must be > 0");

		long started = perfStart();
		String normalizedSearch = normalizeSearch(searchName);
		int offset = page * pageSize;
		String countSql = """
				SELECT COUNT(1)
				FROM %s o
				WHERE o.ShaleClientId = ?
				  AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				  AND (? = '' OR o.Name LIKE ?);
				""".formatted(ORGANIZATIONS_TABLE);
		String pageSql = """
				SELECT
				  o.Id,
				  o.OrganizationTypeId,
				  ot.Name AS OrganizationTypeName,
				  o.Name,
				  o.Phone,
				  o.Email,
				  o.Website,
				  o.City,
				  o.State
				FROM %s o
				LEFT JOIN %s ot
				  ON ot.OrganizationTypeId = o.OrganizationTypeId
				WHERE o.ShaleClientId = ?
				  AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				  AND (? = '' OR o.Name LIKE ?)
				ORDER BY o.Name ASC, o.Id ASC
				OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
				""".formatted(ORGANIZATIONS_TABLE, ORGANIZATION_TYPES_TABLE);

		try (Connection con = db.requireConnection()) {
			int shaleClientId = requireCurrentShaleClientId(con);
			long countStarted = perfStart();
			long total;
			try (PreparedStatement ps = con.prepareStatement(countSql)) {
				ps.setInt(1, shaleClientId);
				ps.setString(2, normalizedSearch);
				ps.setString(3, containsPattern(normalizedSearch));
				try (ResultSet rs = ps.executeQuery()) {
					rs.next();
					total = rs.getLong(1);
				}
			}
			logPerf("organizations.directory.count", "tenantId=" + shaleClientId + " page=" + page + " queryLength=" + normalizedSearch.length() + " total=" + total, countStarted);
			if (total == 0) {
				logPerf("organizations.directory.lightweightPage", "tenantId=" + shaleClientId + " page=" + page + " pageSize=" + pageSize + " queryLength=" + normalizedSearch.length() + " rows=0 total=0 fullDetailHydration=false", started);
				return new PagedResult<>(List.of(), page, pageSize, 0);
			}

			List<DirectoryOrganizationRow> items = new ArrayList<>(pageSize);
			try (PreparedStatement ps = con.prepareStatement(pageSql)) {
				int idx = 1;
				ps.setInt(idx++, shaleClientId);
				ps.setString(idx++, normalizedSearch);
				ps.setString(idx++, containsPattern(normalizedSearch));
				ps.setInt(idx++, offset);
				ps.setInt(idx++, pageSize);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						items.add(mapDirectoryOrganization(rs));
					}
				}
			}
			logPerf("organizations.directory.lightweightPage", "tenantId=" + shaleClientId + " page=" + page + " pageSize=" + pageSize + " queryLength=" + normalizedSearch.length() + " rows=" + items.size() + " total=" + total + " fullDetailHydration=false selectedFields=id,name,type,phone,email,website,city,state", started);
			return new PagedResult<>(items, page, pageSize, total);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load organization directory page (page=" + page + ", pageSize=" + pageSize + ")", e);
		}
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
		long started = perfStart();
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
					logPerf("organizations.detail.findById", "organizationId=" + organizationId + " found=false fullDetailHydration=true", started);
					return null;
				}
				Organization organization = mapOrganization(rs);
				logPerf("organizations.detail.findById", "organizationId=" + organizationId + " found=true fullDetailHydration=true", started);
				return organization;
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
		long started = perfStart();
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
			logPerf("organizations.save.update", "organizationId=" + organization.getId() + " affected=" + affected, started);
			if (affected == 0) {
				throw new RuntimeException("Organization not found or cannot be updated (id=" + organization.getId() + ")");
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to update organization (id=" + organization.getId() + ")", e);
		}
	}



	public boolean softDeleteOrganization(int organizationId, Integer shaleClientId) {
		long started = perfStart();
		if (organizationId <= 0) {
			throw new IllegalArgumentException("organizationId must be > 0");
		}
		if (shaleClientId == null || shaleClientId <= 0) {
			throw new IllegalArgumentException("shaleClientId must be > 0");
		}

		String sql = """
				UPDATE %s
				SET
				  IsDeleted = 1,
				  UpdatedAt = SYSUTCDATETIME()
				WHERE Id = ?
				  AND ShaleClientId = ?
				  AND (IsDeleted = 0 OR IsDeleted IS NULL);
				""".formatted(ORGANIZATIONS_TABLE);
		String cleanupCasePartiesSql = """
				DELETE cp
				FROM dbo.CaseParties cp
				WHERE cp.OrganizationId = ?
				  AND EXISTS (
				      SELECT 1
				      FROM dbo.Cases c
				      WHERE c.Id = cp.CaseId
				        AND c.ShaleClientId = ?
				  );
				""";

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql);
				PreparedStatement cleanupPs = con.prepareStatement(cleanupCasePartiesSql)) {
			int currentShaleClientId = requireCurrentShaleClientId(con);
			if (shaleClientId.intValue() != currentShaleClientId) {
				throw new IllegalArgumentException("shaleClientId does not match current session");
			}

			boolean previousAutoCommit = con.getAutoCommit();
			con.setAutoCommit(false);
			try {
				cleanupPs.setInt(1, organizationId);
				cleanupPs.setInt(2, shaleClientId);
				cleanupPs.executeUpdate();

				int idx = 1;
				ps.setInt(idx++, organizationId);
				ps.setInt(idx++, shaleClientId);
				boolean deleted = ps.executeUpdate() > 0;
				con.commit();
				logPerf("organizations.delete.softDelete", "organizationId=" + organizationId + " tenantId=" + shaleClientId + " deleted=" + deleted, started);
				return deleted;
			} catch (SQLException e) {
				con.rollback();
				throw e;
			} finally {
				con.setAutoCommit(previousAutoCommit);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to soft delete organization (id=" + organizationId + ")", e);
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



	public List<OrganizationTypeRow> findOrganizationTypes() {
		String sql = """
				SELECT ot.OrganizationTypeId, ot.Name
				FROM %s ot
				ORDER BY ot.Name ASC, ot.OrganizationTypeId ASC;
				""".formatted(ORGANIZATION_TYPES_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {

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

	/** One bounded selector query implementing deleted-reset and inactive-mask overlay semantics. */
	public List<OrganizationTypeDefinitionRow> listEffectiveOrganizationTypeDefinitions(int shaleClientId) {
		validateTenantId(shaleClientId);
		String sql = """
				WITH visible AS (
				  SELECT ot.OrganizationTypeId,ot.ShaleClientId,ot.SystemKey,ot.Name,ot.Description,
				         ot.Color,ot.SortOrder,ot.IsActive,ot.IsDeleted,ot.RowVer,
				         ROW_NUMBER() OVER (PARTITION BY ot.SystemKey
				           ORDER BY CASE WHEN ot.ShaleClientId=? THEN 0 ELSE 1 END,ot.OrganizationTypeId) rn
				  FROM dbo.OrganizationTypes ot
				  WHERE (ot.ShaleClientId=? OR ot.ShaleClientId IS NULL) AND ot.IsDeleted=0
				)
				SELECT OrganizationTypeId,ShaleClientId,SystemKey,Name,Description,Color,SortOrder,
				       IsActive,IsDeleted,RowVer
				FROM visible WHERE rn=1 AND IsActive=1
				ORDER BY SortOrder,Name,OrganizationTypeId;
				""";
		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, shaleClientId);
				ps.setInt(2, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					List<OrganizationTypeDefinitionRow> rows = new ArrayList<>();
					while (rs.next()) rows.add(mapOrganizationTypeDefinition(rs));
					return List.copyOf(rows);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load effective Organization Types (clientId=" + shaleClientId + ")", e);
		}
	}

	/** One bounded aggregate query; the assignment's stored OrganizationTypeId is never overlaid. */
	public OrganizationTypeProfileRow findOrganizationTypeProfile(int organizationId, int shaleClientId) {
		if (organizationId <= 0) throw new IllegalArgumentException("organizationId must be > 0");
		validateTenantId(shaleClientId);
		String sql = """
				SELECT o.Id OrganizationId,o.OrganizationTypeId CompatibilityOrganizationTypeId,
				       a.Id AssignmentId,a.OrganizationTypeId AssignedOrganizationTypeId,
				       a.IsPrimary AssignmentIsPrimary,a.SortOrder AssignmentSortOrder,a.RowVer AssignmentRowVer,
				       ot.OrganizationTypeId,ot.ShaleClientId DefinitionShaleClientId,ot.SystemKey,ot.Name,
				       ot.Description,ot.Color,ot.SortOrder DefinitionSortOrder,ot.IsActive,ot.IsDeleted,
				       ot.RowVer DefinitionRowVer
				FROM dbo.Organizations o
				LEFT JOIN dbo.OrganizationOrganizationTypes a
				  ON a.OrganizationId=o.Id AND a.ShaleClientId=o.ShaleClientId AND a.IsDeleted=0
				LEFT JOIN dbo.OrganizationTypes ot
				  ON ot.OrganizationTypeId=a.OrganizationTypeId
				 AND (ot.ShaleClientId IS NULL OR ot.ShaleClientId=o.ShaleClientId)
				WHERE o.Id=? AND o.ShaleClientId=? AND ISNULL(o.IsDeleted,0)=0
				ORDER BY a.IsPrimary DESC,a.SortOrder,ot.SortOrder,ot.Name,a.Id;
				""";
		try (Connection con = db.requireConnection()) {
			verifyTenantMatchesSession(con, shaleClientId);
			try (PreparedStatement ps = con.prepareStatement(sql)) {
				ps.setInt(1, organizationId);
				ps.setInt(2, shaleClientId);
				try (ResultSet rs = ps.executeQuery()) {
					if (!rs.next()) return null;
					Integer compatibilityId = nullableInt(rs, "CompatibilityOrganizationTypeId");
					List<AssignedOrganizationTypeRow> assignments = new ArrayList<>();
					Integer primaryTypeId = null;
					do {
						Long assignmentId = nullableLong(rs, "AssignmentId");
						if (assignmentId == null) continue;
						Integer assignedTypeId = nullableInt(rs, "AssignedOrganizationTypeId");
						Integer definitionTypeId = nullableInt(rs, "OrganizationTypeId");
						if (assignedTypeId == null || definitionTypeId == null || !assignedTypeId.equals(definitionTypeId)) {
							throw new IllegalStateException("Organization Type assignment references an unavailable definition.");
						}
						boolean primary = rs.getBoolean("AssignmentIsPrimary");
						if (primary) primaryTypeId = assignedTypeId;
						OrganizationTypeDefinitionRow definition = mapOrganizationTypeDefinition(rs,
								"DefinitionShaleClientId", "DefinitionSortOrder", "DefinitionRowVer");
						assignments.add(new AssignedOrganizationTypeRow(assignmentId, assignedTypeId, primary,
								requiredInt(rs, "AssignmentSortOrder"), definition, rs.getBytes("AssignmentRowVer")));
					} while (rs.next());
					boolean consistent = compatibilityId != null && compatibilityId.equals(primaryTypeId);
					return new OrganizationTypeProfileRow(organizationId, shaleClientId, compatibilityId,
							consistent, assignments);
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load Organization Type profile (id=" + organizationId + ")", e);
		}
	}

	public List<OrganizationOptionRow> findSelectableOrganizations() {
		String sql = """
				SELECT o.Id, o.Name
				FROM %s o
				WHERE o.ShaleClientId = ?
				  AND (o.IsDeleted = 0 OR o.IsDeleted IS NULL)
				ORDER BY o.Name ASC, o.Id ASC;
				""".formatted(ORGANIZATIONS_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			ps.setInt(1, requireCurrentShaleClientId(con));

			List<OrganizationOptionRow> out = new ArrayList<>();
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					out.add(new OrganizationOptionRow(getNullableInt(rs, "Id"), rs.getString("Name")));
				}
			}
			return out;
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load organization options", e);
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

	private static DirectoryOrganizationRow mapDirectoryOrganization(ResultSet rs) throws SQLException {
		return new DirectoryOrganizationRow(
				getNullableInt(rs, "Id"),
				rs.getString("Name"),
				getNullableInt(rs, "OrganizationTypeId"),
				rs.getString("OrganizationTypeName"),
				rs.getString("Phone"),
				rs.getString("Email"),
				rs.getString("Website"),
				rs.getString("City"),
				rs.getString("State"));
	}

	private static OrganizationTypeDefinitionRow mapOrganizationTypeDefinition(ResultSet rs) throws SQLException {
		return mapOrganizationTypeDefinition(rs, "ShaleClientId", "SortOrder", "RowVer");
	}

	private static OrganizationTypeDefinitionRow mapOrganizationTypeDefinition(ResultSet rs,
			String tenantColumn, String sortColumn, String rowVerColumn) throws SQLException {
		return new OrganizationTypeDefinitionRow(requiredInt(rs, "OrganizationTypeId"), nullableInt(rs, tenantColumn),
				rs.getString("SystemKey"), rs.getString("Name"), rs.getString("Description"), rs.getString("Color"),
				requiredInt(rs, sortColumn), rs.getBoolean("IsActive"), rs.getBoolean("IsDeleted"),
				rs.getBytes(rowVerColumn));
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

	private static String normalizePhoneDigits(String value) {
		if (value == null) {
			return "";
		}
		StringBuilder digits = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isDigit(c)) {
				digits.append(c);
			}
		}
		return digits.toString();
	}

	private static String phoneDigitsExpression(String columnExpression) {
		return "REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(COALESCE(" + columnExpression
				+ ", ''), ' ', ''), '-', ''), '(', ''), ')', ''), '.', ''), '+', ''), '/', '')";
	}

	private static String containsPattern(String normalizedSearch) {
		if (normalizedSearch == null || normalizedSearch.isBlank()) {
			return "%";
		}
		return "%" + normalizedSearch + "%";
	}


	private static long perfStart() {
		return PerformanceLogging.start();
	}

	private static void logPerf(String area, String fields, long startNanos) {
		long elapsedMs = PerformanceLogging.elapsedMs(startNanos);
		if (!PerformanceLogging.shouldLogElapsed(elapsedMs)) {
			return;
		}
		System.Logger.Level level = PerformanceLogging.isSlow(elapsedMs)
				? System.Logger.Level.WARNING
				: System.Logger.Level.DEBUG;
		System.getLogger("PERF").log(level, "PERF " + area + " " + fields + " elapsedMs=" + elapsedMs);
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

	private static void validateTenantId(int shaleClientId) {
		if (shaleClientId <= 0) throw new IllegalArgumentException("shaleClientId must be > 0");
	}

	private static void verifyTenantMatchesSession(Connection con, int shaleClientId) throws SQLException {
		if (requireCurrentShaleClientId(con) != shaleClientId) {
			throw new IllegalArgumentException("shaleClientId does not match current session");
		}
	}

	private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
		Number value = (Number) rs.getObject(column);
		return value == null ? null : value.intValue();
	}

	private static int requiredInt(ResultSet rs, String column) throws SQLException {
		Integer value = nullableInt(rs, column);
		if (value == null) throw new SQLException(column + " must not be null");
		return value;
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		Number value = (Number) rs.getObject(column);
		return value == null ? null : value.longValue();
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

}
