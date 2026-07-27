package com.shale.data.service.adapter;

import com.shale.core.service.MaterialRequestServicePort;
import com.shale.data.dao.MaterialRequestDao;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MaterialRequestRequestedFromValidationTest {
    @Test void createRejectsMissingAndAmbiguousRequestedFromBeforeOpeningSqlConnection() {
        AtomicInteger connections = new AtomicInteger();
        var service = service(connections);
        assertEquals("Requested From is required.", assertThrows(IllegalArgumentException.class, () -> service.createMaterialRequest(create(null,null))).getMessage());
        assertEquals(0,connections.get());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.createMaterialRequest(create(1,2))).getMessage().contains("not both"));
        assertEquals(0,connections.get());
    }

    @Test void updateRejectsMissingAndAmbiguousRequestedFromBeforeOpeningSqlConnection() {
        AtomicInteger connections = new AtomicInteger();
        var service = service(connections);
        assertEquals("Requested From is required.", assertThrows(IllegalArgumentException.class, () -> service.updateMaterialRequest(update(null,null))).getMessage());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> service.updateMaterialRequest(update(1,2))).getMessage().contains("not both"));
        assertEquals(0,connections.get());
    }

    private static MaterialRequestServiceAdapter service(AtomicInteger calls) {
        return new MaterialRequestServiceAdapter(new MaterialRequestDao(() -> { calls.incrementAndGet(); throw new AssertionError("SQL must not be reached"); }));
    }
    private static MaterialRequestServicePort.CreateMaterialRequestCommand create(Integer contact,Integer organization) {
        return new MaterialRequestServicePort.CreateMaterialRequestCommand(1,2,3L,4,"Title",null,contact,organization,null,"EMAIL","REQUESTED",5,null,LocalDateTime.now(),null,null,null);
    }
    private static MaterialRequestServicePort.UpdateMaterialRequestCommand update(Integer contact,Integer organization) {
        return new MaterialRequestServicePort.UpdateMaterialRequestCommand(1,2,3L,6L,4,"Title",null,contact,organization,null,"EMAIL","REQUESTED",5,null,LocalDateTime.now(),null,null,null,null,null,null,null,null,null,null,null,new byte[]{1});
    }
}
