package com.shale.ui.controller;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class SettingsUserManagementNameCellContractTest {
    private static final String SOURCE;
    static { try { SOURCE=Files.readString(Path.of("src/main/java/com/shale/ui/controller/SettingsController.java")); } catch(Exception e){throw new ExceptionInInitializerError(e);} }

    @Test void nameColumnUsesTheEstablishedSharedMiniVariantAndCleansReusedCells(){
        String method=method("configureUserManagementTable");
        assertTrue(containsCode(method, "userManagementCardFactory.create("));
        assertTrue(containsCode(method, "new UserCardModel(row.id(), row.name(), row.color(), row.initials())"));
        assertTrue(containsCode(method, "UserCardFactory.Variant.MINI"));
        assertTrue(containsCode(method, "card.setInactive(row.deleted())"));
        assertTrue(containsCode(method, "setText(null); setGraphic(null)"));
        assertTrue(containsCode(method, "if (empty || row == null) return"));
        assertFalse(containsCode(method, "createTableMini"), "the removed table-specific large card must not return");
        assertFalse(containsCode(method, "User ID #"), "authoritative IDs must not be rendered as secondary metadata");
        assertFalse(containsCode(method, "new Circle"), "Settings must not imitate the shared mini card");
        assertFalse(containsCode(method, "new Label"), "Settings must not imitate the shared mini card");
    }

    @Test void userManagementRowsUseTheFoundationHeightRatherThanTheRemovedLargeCardHeight() throws Exception {
        String fxml=Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));
        String table=fxml.substring(fxml.indexOf("<TableView fx:id=\"userManagementTable\""), fxml.indexOf("</TableView>", fxml.indexOf("<TableView fx:id=\"userManagementTable\"")));
        assertTrue(table.contains("fixedCellSize=\"36\""));
        assertFalse(table.contains("fixedCellSize=\"50\""));
    }

    @Test void rowSelectionAndActivationRemainOnImmutableDtoBackedTableRow(){
        String method=method("configureUserManagementTable");
        assertTrue(containsCode(method, "selectedItemProperty"));
        assertTrue(containsCode(method, "getClickCount()==2"));
        assertTrue(containsCode(method, "KeyCode.ENTER"));
        assertTrue(containsCode(SOURCE, "selected.id()"));
        assertTrue(containsCode(SOURCE, "managedUserRows.stream().filter(r->r.id()==selectedId)"));
        assertTrue(containsCode(SOURCE, "COALESCE(IsRemoved,0)=0") || containsCode(SOURCE, "listUsersForManagement"));
    }

    @Test void filteringRemainsDtoBasedRatherThanReadingRenderedNodes(){
        String filter=method("applyUserFilter");
        assertTrue(containsCode(filter, "managedUserRows.stream()"));
        assertTrue(containsCode(filter, "r.searchText()"));
        assertFalse(containsCode(filter, "getGraphic"));
        assertFalse(containsCode(filter, "lookup("));
    }

    private static String method(String name){int start=SOURCE.indexOf(" "+name+"(");int brace=SOURCE.indexOf('{',start),depth=0;for(int i=brace;i<SOURCE.length();i++){char c=SOURCE.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return SOURCE.substring(start,i+1);}throw new AssertionError(name);}

    private static boolean containsCode(String source, String expected) {
        return source.replaceAll("\\s+", "").contains(expected.replaceAll("\\s+", ""));
    }
}
