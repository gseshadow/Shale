package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;

final class MaterialRequestSearchContractTest {
    private static final String UI=read("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    @Test void searchIsLocalAndComposesWithDeletedLoading(){
        assertTrue(UI.contains("Search requests"));
        assertTrue(UI.contains("Show deleted"));
        assertTrue(UI.contains("matchesSearch(r,query,statuses,methods)"));
        assertTrue(UI.contains("svc.listMaterialRequests(cid,tid,include)"));
        assertTrue(UI.contains("showDeleted.isSelected())!=include"));
        assertFalse(UI.contains("search.textProperty().addListener((o,a,b)->refresh()"));
    }
    @Test void deletedDetailIsReadOnlyAndDeleteIsGuarded(){
        assertTrue(UI.contains("Deleted record — historical review only."));
        assertTrue(UI.contains("!deleting.compareAndSet(false,true)"));
        assertTrue(UI.contains("d.id(),d.rowVer()"));
    }
    private static String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new AssertionError(e);}}
}
