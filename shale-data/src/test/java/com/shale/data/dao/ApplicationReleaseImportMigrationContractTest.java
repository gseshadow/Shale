package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class ApplicationReleaseImportMigrationContractTest {
	@Test void additiveMetadataAndGlobalAuditHaveNoTenantRls()throws Exception{String s=Files.readString(Path.of("../docs/sql/2026-10-02_application_release_catalog_import.sql"));for(String token:new String[]{"Title nvarchar(200)","ReleaseDate date","GlobalControlPlaneAuditLog","OperatorId nvarchar(128)","SET XACT_ABORT ON","BEGIN TRANSACTION","ROLLBACK TRANSACTION","must not have tenant RLS"})assertTrue(s.contains(token),token);assertFalse(s.contains("ShaleClientId"));}
}
