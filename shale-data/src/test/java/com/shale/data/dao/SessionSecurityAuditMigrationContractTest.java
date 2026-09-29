package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import org.junit.jupiter.api.Test;
class SessionSecurityAuditMigrationContractTest{
	@Test void migrationIsBoundedTenantProtectedAndAddsOnlyRequiredReason()throws Exception{String s=Files.readString(Path.of("../docs/sql/2026-09-29_session_security_audit_phase8a.sql"));for(String x:new String[]{"SessionSecurityAuditLog","USER_REVOKED","ADMIN_SESSION_LIST","ADMIN_REVOKE","sec.fn_FilterByTenant","AFTER INSERT","AFTER UPDATE"})assertTrue(s.contains(x),x);assertFalse(s.contains("Token"));assertFalse(s.contains("Jti"));}
}
