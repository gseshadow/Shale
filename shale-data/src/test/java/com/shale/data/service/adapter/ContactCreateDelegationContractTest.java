package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ContactCreateDelegationContractTest {
    @Test
    void productionAdapterAndGatewayReachTransactionalAggregateCreation() throws Exception {
        String adapter=Files.readString(Path.of("src/main/java/com/shale/data/service/adapter/ContactServiceAdapter.java"));
        String dao=Files.readString(Path.of("src/main/java/com/shale/data/dao/ContactDao.java"));
        assertTrue(adapter.contains("contactGateway.createContactProfile(c)"),
                "the service adapter must delegate complete creation to its production gateway");
        assertTrue(adapter.contains("contactDao.createContactProfile(c)"),
                "the production gateway must delegate complete creation to ContactDao");
        assertTrue(dao.contains("mutationDao.createAggregate(c)"),
                "ContactDao must terminate complete creation at the transactional aggregate DAO");
    }
}
