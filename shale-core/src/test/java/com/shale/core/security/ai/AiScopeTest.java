package com.shale.core.security.ai;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AiScopeTest {
    @Test void unknownAndPrivilegedScopeStringsNeverAcquireMeaning() {
        for (String value : new String[]{"admin","all","read_all","database","sql","CASES.SEARCH","cases.search ","",null})
            assertThrows(IllegalArgumentException.class, () -> AiScope.parse(value));
        assertEquals(AiScope.CASES_SEARCH, AiScope.parse("cases.search"));
        assertEquals(AiScope.CASES_OVERVIEW_READ, AiScope.parse("cases.overview.read"));
    }
    @Test void exactIntersectionCannotAddAuthority() {
        for (int a=0;a<4;a++) for (int b=0;b<4;b++) for (int c=0;c<4;c++)
            assertEquals(a&b&c, AiScope.mask(AiScope.intersect(AiScope.fromMask(a),AiScope.fromMask(b),AiScope.fromMask(c))));
        assertThrows(IllegalArgumentException.class, () -> AiScope.fromMask(4));
        assertThrows(UnsupportedOperationException.class, () -> AiScope.fromMask(3).clear());
    }
}
