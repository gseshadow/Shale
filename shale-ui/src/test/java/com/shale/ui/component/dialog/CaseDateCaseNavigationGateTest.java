package com.shale.ui.component.dialog;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CaseDateCaseNavigationGateTest {
    @Test void unchangedNavigatesImmediatelyAndOnlyOnceByStableId() {
        AtomicInteger confirms=new AtomicInteger(), closes=new AtomicInteger(), routed=new AtomicInteger();
        var gate=gate(71,false,false,()->{confirms.incrementAndGet();return true;},closes,routed);
        gate.activate(71); gate.activate(71);
        assertAll(()->assertEquals(0,confirms.get()),()->assertEquals(1,closes.get()),()->assertEquals(71,routed.get()));
    }

    @Test void dirtyConfirmationCancellationKeepsEditorAndValuesInPlaceThenAllowsRetry() {
        AtomicInteger closes=new AtomicInteger(), routed=new AtomicInteger(), confirms=new AtomicInteger();
        AtomicBoolean discard=new AtomicBoolean(false);
        var gate=gate(71,true,false,()->{confirms.incrementAndGet();return discard.get();},closes,routed);
        gate.activate(71);
        assertAll(()->assertEquals(1,confirms.get()),()->assertEquals(0,closes.get()),()->assertEquals(0,routed.get()));
        discard.set(true); gate.activate(71);
        assertAll(()->assertEquals(2,confirms.get()),()->assertEquals(1,closes.get()),()->assertEquals(71,routed.get()));
    }

    @Test void savingAndMismatchedDisplayedIdentityCannotNavigate() {
        AtomicInteger closes=new AtomicInteger(), routed=new AtomicInteger();
        var saving=gate(71,false,true,()->true,closes,routed); saving.activate(71);
        var mismatch=gate(71,false,false,()->true,closes,routed); mismatch.activate(72);
        assertAll(()->assertEquals(0,closes.get()),()->assertEquals(0,routed.get()));
    }

    private static CaseDateOccurrenceDialog.CaseNavigationGate gate(int id,boolean dirty,boolean saving,
            java.util.function.Supplier<Boolean> confirm,AtomicInteger closes,AtomicInteger routed){
        return new CaseDateOccurrenceDialog.CaseNavigationGate(id,()->dirty,()->saving,confirm,closes::incrementAndGet,routed::set);
    }
}
