package com.shale.ui.controller;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class SettingsUserManagementNameCellContractTest {
    private static final String SOURCE;
    static { try { SOURCE=Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java")); } catch(Exception e){throw new ExceptionInInitializerError(e);} }

    @Test void nameColumnUsesSharedPassiveTableMiniAndCleansReusedCells(){
        String method=method("configureUserManagementTable");
        assertTrue(method.contains("userManagementCardFactory.createTableMini"));
        assertTrue(method.contains("new UserCardModel(row.id(), row.name(), row.color(), row.initials())"));
        assertTrue(method.contains("\"User ID #\" + row.id()"));
        assertTrue(method.contains("row.deleted()"));
        assertTrue(method.contains("setText(null); setGraphic(null)"));
        assertTrue(method.contains("if (empty || row == null) return"));
        assertTrue(method.contains("createTableMini"), "embedded identity must use the shared passive renderer");
        assertFalse(method.contains("new Circle"), "Settings must not imitate the shared mini card");
        assertFalse(method.contains("new Label"), "Settings must not imitate the shared mini card");
    }

    @Test void rowSelectionAndActivationRemainOnImmutableDtoBackedTableRow(){
        String method=method("configureUserManagementTable");
        assertTrue(method.contains("selectedItemProperty"));
        assertTrue(method.contains("getClickCount()==2"));
        assertTrue(method.contains("KeyCode.ENTER"));
        assertTrue(SOURCE.contains("selected.id()"));
        assertTrue(SOURCE.contains("managedUserRows.stream().filter(r->r.id()==selectedId)"));
        assertTrue(SOURCE.contains("COALESCE(IsRemoved,0)=0") || SOURCE.contains("listUsersForManagement"));
    }

    @Test void filteringRemainsDtoBasedRatherThanReadingRenderedNodes(){
        String filter=method("applyUserFilter");
        assertTrue(filter.contains("managedUserRows.stream()"));
        assertTrue(filter.contains("r.searchText()"));
        assertFalse(filter.contains("getGraphic"));
        assertFalse(filter.contains("lookup("));
    }

    private static String method(String name){int start=SOURCE.indexOf(" "+name+"(");int brace=SOURCE.indexOf('{',start),depth=0;for(int i=brace;i<SOURCE.length();i++){char c=SOURCE.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return SOURCE.substring(start,i+1);}throw new AssertionError(name);}
}
