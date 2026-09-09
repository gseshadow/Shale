package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

/** Explicit inventory guard for the only production Organization SQL write owner. */
final class OrganizationWriteOwnershipTest {
	@Test void organizationInsertAndCompatibilityUpdateAreOwnedByAggregateMutationDao()throws Exception{
		String aggregate=read("OrganizationTypeMutationDao.java"),organizations=read("OrganizationDao.java"),cases=read("CaseDao.java");
		assertTrue(aggregate.contains("INSERT dbo.Organizations("));
		assertTrue(aggregate.contains("INSERT dbo.OrganizationOrganizationTypes("));
		assertTrue(aggregate.contains("UPDATE dbo.Organizations SET OrganizationTypeId=?"));
		assertFalse(organizations.matches("(?s).*INSERT\\s+(?:INTO\\s+)?(?:dbo\\.)?Organizations\\s*\\(.*"),"OrganizationDao must delegate creation to the aggregate worker");
		assertFalse(cases.matches("(?s).*INSERT\\s+(?:INTO\\s+)?dbo\\.Organizations\\s*\\(.*"),"Case workflows must use the connection-bound aggregate worker");
		assertTrue(cases.contains("createSingleTypeOnConnection(con"),"embedded Case creation stays in its owning transaction");
	}
	private static String read(String file)throws Exception{return Files.readString(Path.of("src/main/java/com/shale/data/dao",file));}
}
