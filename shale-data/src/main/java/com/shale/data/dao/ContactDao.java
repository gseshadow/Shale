package com.shale.data.dao;

import com.shale.core.runtime.DbSessionProvider;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ContactDao {

	private static final String CONTACTS_TABLE = "Contacts";

	private final DbSessionProvider db;

	public ContactDao(DbSessionProvider dbSessionProvider) {
		this.db = Objects.requireNonNull(dbSessionProvider, "dbSessionProvider");
	}

	public record ContactRow(
			int id,
			int shaleClientId,
			String name,
			String firstName,
			String lastName,
			String email,
			String phone,
			boolean deleted,
			Instant updatedAt
	) {
		public String displayName() {
			String first = firstName == null ? "" : firstName.trim();
			String last = lastName == null ? "" : lastName.trim();
			String full = (first + " " + last).trim();
			if (!full.isBlank()) {
				return full;
			}
			if (name == null || name.isBlank()) {
				return "—";
			}
			return name;
		}
	}

	public record PagedResult<T>(List<T> items, int page, int pageSize, long total) {
	}

	/** page is 0-based */
	public PagedResult<ContactRow> findPage(int page, int pageSize, String searchQuery) {
		if (page < 0) {
			throw new IllegalArgumentException("page must be >= 0");
		}
		if (pageSize <= 0) {
			throw new IllegalArgumentException("pageSize must be > 0");
		}

		String normalizedSearch = normalizeSearch(searchQuery);
		long total = countAll(normalizedSearch);
		if (total == 0) {
			return new PagedResult<>(List.of(), page, pageSize, 0);
		}

		int offset = page * pageSize;
		String sql = """
				SELECT
				  c.Id,
				  c.ShaleClientId,
				  c.Name,
				  c.FirstName,
				  c.LastName,
				  c.Email,
				  c.Phone,
				  c.IsDeleted,
				  c.UpdatedAt
				FROM %s c
				WHERE c.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  AND (
				    ? = ''
				    OR COALESCE(c.Name, '') LIKE ?
				    OR COALESCE(c.FirstName, '') LIKE ?
				    OR COALESCE(c.LastName, '') LIKE ?
				    OR COALESCE(c.Email, '') LIKE ?
				    OR COALESCE(c.Phone, '') LIKE ?
				  )
				ORDER BY
				  COALESCE(NULLIF(LTRIM(RTRIM(c.LastName)), ''), NULLIF(LTRIM(RTRIM(c.Name)), ''), '') ASC,
				  COALESCE(NULLIF(LTRIM(RTRIM(c.FirstName)), ''), '') ASC,
				  c.Id ASC
				OFFSET ? ROWS FETCH NEXT ? ROWS ONLY;
				""".formatted(CONTACTS_TABLE);

		List<ContactRow> items = new ArrayList<>(pageSize);
		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int currentShaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			String likePattern = containsPattern(normalizedSearch);
			ps.setInt(idx++, currentShaleClientId);
			ps.setString(idx++, normalizedSearch);
			ps.setString(idx++, likePattern);
			ps.setString(idx++, likePattern);
			ps.setString(idx++, likePattern);
			ps.setString(idx++, likePattern);
			ps.setString(idx++, likePattern);
			ps.setInt(idx++, offset);
			ps.setInt(idx++, pageSize);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					items.add(mapContact(rs));
				}
			}

			return new PagedResult<>(items, page, pageSize, total);
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load contacts page (page=" + page + ", pageSize=" + pageSize + ")", e);
		}
	}

	public ContactRow findById(int contactId) {
		if (contactId <= 0) {
			throw new IllegalArgumentException("contactId must be > 0");
		}

		String sql = """
				SELECT
				  c.Id,
				  c.ShaleClientId,
				  c.Name,
				  c.FirstName,
				  c.LastName,
				  c.Email,
				  c.Phone,
				  c.IsDeleted,
				  c.UpdatedAt
				FROM %s c
				WHERE c.Id = ?
				  AND c.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL);
				""".formatted(CONTACTS_TABLE);

		try (Connection con = db.requireConnection();
				 PreparedStatement ps = con.prepareStatement(sql)) {
			int currentShaleClientId = requireCurrentShaleClientId(con);

			int idx = 1;
			ps.setInt(idx++, contactId);
			ps.setInt(idx++, currentShaleClientId);

			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}

				return mapContact(rs);
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to load contact by id (id=" + contactId + ")", e);
		}
	}

	private long countAll(String normalizedSearch) {
		String sql = """
				SELECT COUNT(*)
				FROM %s c
				WHERE c.ShaleClientId = ?
				  AND (c.IsDeleted = 0 OR c.IsDeleted IS NULL)
				  AND (
				    ? = ''
				    OR COALESCE(c.Name, '') LIKE ?
				    OR COALESCE(c.FirstName, '') LIKE ?
				    OR COALESCE(c.LastName, '') LIKE ?
				    OR COALESCE(c.Email, '') LIKE ?
				    OR COALESCE(c.Phone, '') LIKE ?
				  );
				""".formatted(CONTACTS_TABLE);

		try (Connection con = db.requireConnection();
				PreparedStatement ps = con.prepareStatement(sql)) {
			int currentShaleClientId = requireCurrentShaleClientId(con);
			int idx = 1;
			String likePattern = containsPattern(normalizedSearch);
			ps.setInt(idx++, currentShaleClientId);
			ps.setString(idx++, normalizedSearch);
			ps.setString(idx++, likePattern);
			ps.setString(idx++, likePattern);
			ps.setString(idx++, likePattern);
			ps.setString(idx++, likePattern);
			ps.setString(idx++, likePattern);

			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getLong(1);
				}
				return 0;
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to count contacts", e);
		}
	}

	private static ContactRow mapContact(ResultSet rs) throws SQLException {
		return new ContactRow(
				rs.getInt("Id"),
				rs.getInt("ShaleClientId"),
				rs.getString("Name"),
				rs.getString("FirstName"),
				rs.getString("LastName"),
				rs.getString("Email"),
				rs.getString("Phone"),
				toBoolean(rs.getObject("IsDeleted")),
				toInstant(rs.getTimestamp("UpdatedAt"))
		);
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

	private static Integer getNullableInt(ResultSet rs, int colIndex) throws SQLException {
		int value = rs.getInt(colIndex);
		return rs.wasNull() ? null : value;
	}

	private static String normalizeSearch(String searchQuery) {
		if (searchQuery == null) {
			return "";
		}
		return searchQuery.trim();
	}

	private static String containsPattern(String normalizedSearch) {
		if (normalizedSearch == null || normalizedSearch.isBlank()) {
			return "%";
		}
		return "%" + normalizedSearch + "%";
	}

	private static boolean toBoolean(Object value) {
		if (value == null) {
			return false;
		}
		if (value instanceof Boolean b) {
			return b;
		}
		if (value instanceof Number n) {
			return n.intValue() != 0;
		}
		return Boolean.parseBoolean(value.toString());
	}

	private static Instant toInstant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}
}
