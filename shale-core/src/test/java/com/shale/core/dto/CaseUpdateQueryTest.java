package com.shale.core.dto;

import com.shale.core.service.CaseServicePort;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class CaseUpdateQueryTest {
    @Test void defaultsAreBoundedNewestActiveAndFiltersRejectInvalidInputs() {
        var query = new CaseUpdateQuery(1,7,31,"  text  ",null,null,null,null,null,null,50);
        assertEquals("text",query.text()); assertEquals(CaseUpdateQuery.Sort.NEWEST,query.sort());
        assertEquals(CaseUpdateQuery.Visibility.ACTIVE,query.visibility());
        for (int size : new int[]{0,101}) assertThrows(IllegalArgumentException.class,()->CaseUpdateQuery.newest(1,7,31,"",size));
        assertThrows(IllegalArgumentException.class,()->CaseUpdateQuery.newest(0,7,31,"",50));
        assertThrows(IllegalArgumentException.class,()->CaseUpdateQuery.newest(1,0,31,"",50));
        assertThrows(IllegalArgumentException.class,()->CaseUpdateQuery.newest(1,7,0,"",50));
        assertThrows(IllegalArgumentException.class,()->CaseUpdateQuery.newest(1,7,31,"x".repeat(1001),50));
        assertThrows(IllegalArgumentException.class,()->new CaseUpdateQuery(1,7,31,"",null,LocalDate.of(2026,10,21),LocalDate.of(2026,10,20),null,null,null,50));
        assertThrows(IllegalArgumentException.class,()->new CaseUpdateQuery.Cursor(null,1));
    }
    @Test void authorsAreImmutableIdSetsAndEmptyMeansAll() {
        var authors = new java.util.HashSet<>(java.util.Set.of(17,18));
        var q = new CaseUpdateQuery(1,7,31,"",null,null,null,authors,null,null,50);
        authors.clear();
        assertEquals(java.util.Set.of(17,18), q.authorUserIds());
        assertEquals(q.authorUserIds(), q.inZone(java.time.ZoneId.of("America/Denver")).authorUserIds());
        assertThrows(UnsupportedOperationException.class, () -> q.authorUserIds().add(19));
        assertTrue(CaseUpdateQuery.newest(1,7,31,"",50).authorUserIds().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new CaseUpdateQuery(1,7,31,"",null,null,null,java.util.Set.of(0),null,null,50));
        assertThrows(IllegalArgumentException.class, () -> new CaseUpdateQuery(1,7,31,"",null,null,null,new java.util.HashSet<>(java.util.Arrays.asList(17,null)),null,null,50));
        assertThrows(IllegalArgumentException.class, () -> new CaseUpdateQuery(1,7,31,"",null,null,null,java.util.stream.IntStream.rangeClosed(1,1001).boxed().collect(java.util.stream.Collectors.toSet()),null,null,50));
    }
    @Test void deletionRequiresIdentityAndDefensiveVersion() {
        byte[] version = new byte[8];
        var command = new CaseServicePort.DeleteCaseUpdateCommand(1,2,7,31,version);
        version[0] = 1; assertEquals(0,command.expectedRowVersion()[0]);
        command.expectedRowVersion()[0] = 2; assertEquals(0,command.expectedRowVersion()[0]);
        assertThrows(IllegalArgumentException.class, () -> new CaseServicePort.DeleteCaseUpdateCommand(1,2,7,31,null));
        assertThrows(IllegalArgumentException.class, () -> new CaseServicePort.DeleteCaseUpdateCommand(1,2,7,31,new byte[7]));
        assertThrows(IllegalArgumentException.class, () -> new CaseServicePort.DeleteCaseUpdateCommand(1,2,7,0,new byte[8]));
    }
    @Test void versionsAreRequiredAndDefensivelyCopiedWhileLegacyBeanShapeIsPreserved() throws Exception {
        byte[] version = {0,0,0,0,0,0,0,1};
        var command = new CaseServicePort.UpdateCaseNoteCommand(1,2,7,31," draft ",version);
        version[7]=2;
        assertEquals(1,command.expectedRowVersion()[7]); command.expectedRowVersion()[7]=3;
        assertEquals(1,command.expectedRowVersion()[7]); assertEquals("draft",command.text());
        assertThrows(IllegalArgumentException.class,()->new CaseServicePort.UpdateCaseNoteCommand(1,2,7,31,"",new byte[8]));
        assertThrows(IllegalArgumentException.class,()->new CaseServicePort.UpdateCaseNoteCommand(1,2,7,31,"draft",null));
        var legacy = new CaseUpdateDto(1,2,"text",null,null,31,"author");
        assertNull(legacy.rowVersion()); assertFalse(legacy.deleted());
        var properties=Arrays.stream(CaseUpdateDto.class.getMethods()).map(java.lang.reflect.Method::getName).toList();
        assertFalse(properties.contains("getRowVersion") || properties.contains("isDeleted") || properties.contains("getDeleted"),
                "Desktop-only concurrency/lifecycle fields must not expand the legacy API bean shape");
    }
}
