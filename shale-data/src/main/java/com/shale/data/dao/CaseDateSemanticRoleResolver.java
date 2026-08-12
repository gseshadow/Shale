package com.shale.data.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.shale.core.model.CaseDateSemanticRole;

/** Tenant-safe, ambiguity-detecting authority for protected Case Date meanings. */
final class CaseDateSemanticRoleResolver {
    private CaseDateSemanticRoleResolver() {}

    static int requireEffectiveTypeId(Connection con, int tenant, CaseDateSemanticRole role) throws SQLException {
        String sql = """
                SELECT m.CaseDateTypeId
                FROM dbo.CaseDateTypeSemanticRoleMappings m
                JOIN dbo.CaseDateTypes t ON t.Id=m.CaseDateTypeId
                WHERE m.SemanticRoleKey=? AND (m.ShaleClientId=? OR m.ShaleClientId IS NULL)
                  AND m.IsActive=1 AND m.IsDeleted=0 AND t.IsActive=1 AND t.IsDeleted=0
                  AND (t.ShaleClientId=? OR t.ShaleClientId IS NULL)
                  AND NOT (m.ShaleClientId IS NULL AND EXISTS (
                    SELECT 1 FROM dbo.CaseDateTypeSemanticRoleMappings tenant_mapping
                    JOIN dbo.CaseDateTypes tenant_type ON tenant_type.Id=tenant_mapping.CaseDateTypeId
                    WHERE tenant_mapping.ShaleClientId=? AND tenant_mapping.SemanticRoleKey=m.SemanticRoleKey
                      AND tenant_mapping.IsActive=1 AND tenant_mapping.IsDeleted=0
                      AND tenant_type.ShaleClientId=? AND tenant_type.IsActive=1 AND tenant_type.IsDeleted=0))
                """;
        try (PreparedStatement ps=con.prepareStatement(sql)) {
            ps.setString(1,role.persistedKey()); ps.setInt(2,tenant); ps.setInt(3,tenant); ps.setInt(4,tenant); ps.setInt(5,tenant);
            try (ResultSet rs=ps.executeQuery()) {
                if (!rs.next()) throw new IllegalStateException("No effective Case Date type is configured for semantic role " + role.persistedKey() + ".");
                int id=rs.getInt(1);
                if (rs.next()) throw new IllegalStateException("Ambiguous Case Date type mappings for semantic role " + role.persistedKey() + ".");
                return id;
            }
        }
    }
}
