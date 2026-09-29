package com.shale.data.dao;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdministrativeReadAuditEventTest {
	private static final Instant SINCE=Instant.parse("2026-09-01T00:00:00Z");
	@Test void recentListMetadataIsDeterministicAllowlistedAndBounded() {
		var event=AdministrativeReadAuditEvent.recentList(7,31,50,2,50,"DESKTOP","1.2.3",true,true,SINCE);
		assertEquals(AdministrativeReadAuditEvent.ReadType.APPLICATION_INSTANCE_RECENT_LIST,event.readType());
		assertEquals(50,event.resultCount());
		assertEquals("{\"activeOnly\":true,\"applicationVersionFilter\":\"1.2.3\",\"clientTypeFilter\":\"DESKTOP\",\"page\":2,\"pageSize\":50,\"since\":\"2026-09-01T00:00:00Z\",\"userFilterPresent\":true}",event.metadata());
		for(String prohibited:new String[]{"machine","email","displayName","instanceId","heartbeat","activity","token"}) assertFalse(event.metadata().toLowerCase().contains(prohibited.toLowerCase()),prohibited);
		assertTrue(event.metadata().length()<=AdministrativeReadAuditEvent.MAX_METADATA_LENGTH);
	}
	@Test void distributionRecordsOnlyWindowAndBucketCount() {
		var event=AdministrativeReadAuditEvent.versionDistribution(7,31,75,SINCE);
		assertEquals(AdministrativeReadAuditEvent.ReadType.APPLICATION_INSTANCE_VERSION_DISTRIBUTION,event.readType());
		assertEquals(75,event.resultCount());
		assertEquals("{\"since\":\"2026-09-01T00:00:00Z\"}",event.metadata());
	}
	@Test void rejectsInvalidAuthorityCountAndOversizedMetadata() {
		assertThrows(IllegalArgumentException.class,()->new AdministrativeReadAuditEvent(0,1,AdministrativeReadAuditEvent.ReadType.APPLICATION_INSTANCE_RECENT_LIST,0,null));
		assertThrows(IllegalArgumentException.class,()->new AdministrativeReadAuditEvent(1,1,AdministrativeReadAuditEvent.ReadType.APPLICATION_INSTANCE_RECENT_LIST,-1,null));
		assertThrows(IllegalArgumentException.class,()->new AdministrativeReadAuditEvent(1,1,AdministrativeReadAuditEvent.ReadType.APPLICATION_INSTANCE_RECENT_LIST,0,"x".repeat(1001)));
	}
}
