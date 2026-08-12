package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import com.shale.core.model.*;

class AuthoritativeCaseDateEditorTest {
    private static CaseDateAggregateResult snapshot(byte token, boolean present) {
        EnumMap<MigratedCaseDateKey, CompatibilityCaseDateState> states = new EnumMap<>(MigratedCaseDateKey.class);
        for (MigratedCaseDateKey key : MigratedCaseDateKey.values()) states.put(key, present
                ? new CompatibilityCaseDateState(key,key.systemKey(),LocalDateTime.of(2026,1,1,0,0),null,!key.supportsTime(),10L+key.ordinal(),100+key.ordinal(),new byte[]{token},null)
                : new CompatibilityCaseDateState(key,key.systemKey(),null,null,true,null,null,null,new CompatibilityCaseDateMutation.ExpectedAbsent(new byte[]{token})));
        return new CaseDateAggregateResult(new byte[]{token}, states);
    }

    @Test void noOpDoesNotEnterSavingOrBuildCommand() {
        var editor=new AuthoritativeCaseDateEditor(); editor.replace(snapshot((byte)1,true));
        assertNull(editor.beginSave(2,3,4,AuthoritativeCaseDateEditor.values(editor.states())));
        assertFalse(editor.isSaving());
    }

    @Test void returnedSnapshotFullyReplacesConcurrencyForSecondEdit() {
        var editor=new AuthoritativeCaseDateEditor(); editor.replace(snapshot((byte)1,false));
        var values=new EnumMap<>(AuthoritativeCaseDateEditor.values(editor.states()));
        values.put(MigratedCaseDateKey.DATE_OF_INJURY,new CompatibilityCaseDateEditor.EditedValue(LocalDateTime.of(2026,2,2,0,0),null,true));
        var first=editor.beginSave(2,3,4,values);
        assertArrayEquals(new byte[]{1},first.expectedCaseRowVer());
        assertInstanceOf(CompatibilityCaseDateMutation.Create.class,first.dates().get(MigratedCaseDateKey.DATE_OF_INJURY));
        editor.replace(snapshot((byte)9,true));
        values=new EnumMap<>(AuthoritativeCaseDateEditor.values(editor.states()));
        values.put(MigratedCaseDateKey.DATE_OF_INJURY,new CompatibilityCaseDateEditor.EditedValue(LocalDateTime.of(2026,3,3,0,0),null,true));
        var second=editor.beginSave(2,3,4,values);
        assertArrayEquals(new byte[]{9},second.expectedCaseRowVer());
        var update=assertInstanceOf(CompatibilityCaseDateMutation.Update.class,second.dates().get(MigratedCaseDateKey.DATE_OF_INJURY));
        assertArrayEquals(new byte[]{9},update.expectedRowVer());
        assertEquals(9,second.dates().size());
    }

    @Test void invalidateRejectsSaveAndDuplicateSaveIsBlocked() {
        var editor=new AuthoritativeCaseDateEditor(); editor.replace(snapshot((byte)1,false));
        var values=new EnumMap<>(AuthoritativeCaseDateEditor.values(editor.states()));
        values.put(MigratedCaseDateKey.CALLER_DATE,new CompatibilityCaseDateEditor.EditedValue(LocalDateTime.of(2026,1,1,9,30),null,false));
        editor.beginSave(2,3,4,values);
        assertThrows(IllegalStateException.class,()->editor.beginSave(2,3,4,values));
        editor.invalidate();
        assertThrows(IllegalStateException.class,()->editor.beginSave(2,3,4,values));
    }
}
