package com.shale.ui.component;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpandedTextEditTest {
    @Test void draftStartsWithOriginalAndDoesNotMutateIt() {
        ExpandedTextEdit edit = new ExpandedTextEdit("original");
        assertEquals("original", edit.draft());
        edit.setDraft("changed");
        assertEquals("original", edit.original());
        assertEquals("changed", edit.draft());
    }
}
