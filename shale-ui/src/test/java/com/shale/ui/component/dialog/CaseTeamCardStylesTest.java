package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CaseTeamCardStylesTest {
    @Test void validUserColorProducesRestrainedGradientWithQuietActionEdge() {
        String style=CaseTeamCardStyles.memberCardStyle("#CC3300");
        assertTrue(style.contains("linear-gradient(to right"));
        assertTrue(style.contains("rgba(204,51,0,0.240) 0%"),"configured color should lead at restrained opacity");
        assertTrue(style.contains("rgba(255,255,255,0.98) 100%"),"action edge should fade to the quiet card surface");
        assertFalse(style.contains("#CC3300"),"normal text must not sit on the unmodified saturated color");
    }

    @Test void missingAndInvalidColorsUseNeutralBlueGrayFallback() {
        assertEquals(CaseTeamCardStyles.memberCardStyle(null),CaseTeamCardStyles.memberCardStyle("not-css"));
        assertTrue(CaseTeamCardStyles.memberCardStyle(null).contains("rgba(100,116,139,0.240)"));
        assertTrue(CaseTeamCardStyles.accentStyle("bad;color").contains("#64748BFF"),"dynamic CSS must contain only a sanitized fallback value");
    }
}
