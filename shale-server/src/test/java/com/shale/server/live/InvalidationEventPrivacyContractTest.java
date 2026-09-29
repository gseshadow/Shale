package com.shale.server.live;

import static org.junit.jupiter.api.Assertions.*;import java.nio.file.*;import org.junit.jupiter.api.Test;

class InvalidationEventPrivacyContractTest {
	@Test void wirePayloadsAreVersionedInvalidationsWithoutSensitiveFields()throws Exception{String source=Files.readString(Path.of("src/main/java/com/shale/server/live/HttpInvalidationPublisher.java"));for(String required:new String[]{"SESSION_INVALIDATED","APPLICATION_POLICY_CHANGED","schemaVersion","sessionId","channel"})assertTrue(source.contains(required),required);for(String forbidden:new String[]{"accessToken","currentAccessJti","password","email","displayName","machineId","ipAddress","location","auditMetadata"})assertFalse(source.contains("\\\""+forbidden+"\\\""),forbidden);}
}
