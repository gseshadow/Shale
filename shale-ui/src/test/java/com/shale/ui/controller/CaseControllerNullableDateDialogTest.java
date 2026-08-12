package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.shale.core.model.CaseDateAggregateResult;
import com.shale.core.model.CompatibilityCaseDateEditor;
import com.shale.core.model.CompatibilityCaseDateMutation;
import com.shale.core.model.CompatibilityCaseDateState;
import com.shale.core.model.MigratedCaseDateKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class CaseControllerNullableDateDialogTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");

    @Test
    void statuteAndTortClearsUseAuthoritativeClearIntents() {
        AuthoritativeCaseDateEditor editor = new AuthoritativeCaseDateEditor();
        editor.replace(snapshot((byte) 4, true));
        EnumMap<MigratedCaseDateKey, CompatibilityCaseDateEditor.EditedValue> values =
                new EnumMap<>(AuthoritativeCaseDateEditor.values(editor.states()));
        values.put(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS, cleared());
        values.put(MigratedCaseDateKey.TORT_NOTICE_DEADLINE, cleared());

        var command = editor.beginSave(7, 8, 9, values);

        assertArrayEquals(new byte[]{4}, command.expectedCaseRowVer());
        var sol = assertInstanceOf(CompatibilityCaseDateMutation.Clear.class,
                command.dates().get(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS));
        var tort = assertInstanceOf(CompatibilityCaseDateMutation.Clear.class,
                command.dates().get(MigratedCaseDateKey.TORT_NOTICE_DEADLINE));
        assertArrayEquals(new byte[]{4}, sol.expectedRowVer());
        assertArrayEquals(new byte[]{4}, tort.expectedRowVer());
    }

    @Test
    void blankAuthoritativeSlotsRemainUnchangedRatherThanCreatingOrClearing() {
        AuthoritativeCaseDateEditor editor = new AuthoritativeCaseDateEditor();
        editor.replace(snapshot((byte) 5, false));

        assertNull(editor.beginSave(7, 8, 9, AuthoritativeCaseDateEditor.values(editor.states())));
        assertFalse(editor.isSaving());
    }

    @Test
    void overviewAndDetailsSaveThroughTheAggregateEditor() throws Exception {
        String source = Files.readString(SOURCE);
        String save = method(source, "private void saveAuthoritativeValues(");

        assertTrue(source.contains("saveAuthoritativeDate(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS, value)"));
        assertTrue(source.contains("saveAuthoritativeDate(MigratedCaseDateKey.TORT_NOTICE_DEADLINE, value)"));
        assertTrue(save.contains("compatibilityDates.beginSave("));
        assertTrue(save.contains("caseService.mutateMigratedCompatibilityDates(command)"));
        assertFalse(save.contains("saveCoreOverviewField"));
        assertFalse(save.contains("caseDao.updateCase"));
    }

    @Test
    void overviewAndDetailsHydrateOnlyFromAuthoritativeSnapshot() throws Exception {
        String source = Files.readString(SOURCE);
        String render = method(source, "private void renderCompatibilityDates()");

        assertTrue(render.contains("compatibilityDates.states()"));
        assertTrue(render.contains("s.get(MigratedCaseDateKey.STATUTE_OF_LIMITATIONS)"));
        assertTrue(render.contains("s.get(MigratedCaseDateKey.TORT_NOTICE_DEADLINE)"));
        assertTrue(render.contains("ovTortNoticeDeadlineValue"));
        assertFalse(render.contains("CaseOverviewDto"));
        assertFalse(render.contains("getTortNoticeDeadline()"));
        assertFalse(render.contains("current.get"));
    }

    private static CompatibilityCaseDateEditor.EditedValue cleared() {
        return new CompatibilityCaseDateEditor.EditedValue(null, null, true);
    }

    private static CaseDateAggregateResult snapshot(byte token, boolean present) {
        EnumMap<MigratedCaseDateKey, CompatibilityCaseDateState> states = new EnumMap<>(MigratedCaseDateKey.class);
        for (MigratedCaseDateKey key : MigratedCaseDateKey.values()) {
            states.put(key, present
                    ? new CompatibilityCaseDateState(key, key.systemKey(), LocalDateTime.of(2026, 1, 1, 0, 0),
                            null, !key.supportsTime(), 100L + key.ordinal(), 200 + key.ordinal(), new byte[]{token}, null)
                    : new CompatibilityCaseDateState(key, key.systemKey(), null, null, true, null, null, null,
                            new CompatibilityCaseDateMutation.ExpectedAbsent(new byte[]{token})));
        }
        return new CaseDateAggregateResult(new byte[]{token}, states);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, signature);
        int open = source.indexOf('{', start);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Unclosed method: " + signature);
    }
}
