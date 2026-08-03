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
        assertTrue(method.contains("userManagementCardFactory.create("));
        assertTrue(method.contains("new UserCardModel(row.id(), row.name(), row.color(), row.initials())"));
        assertTrue(method.contains("UserCardFactory.Variant.MINI"));
        assertTrue(method.contains("card.setInactive(row.deleted())"));
        assertTrue(method.contains("setText(null); setGraphic(null)"));
        assertTrue(method.contains("if (empty || row == null) return"));
        assertFalse(method.contains("createTableMini"), "the removed table-specific large card must not return");
        assertFalse(method.contains("User ID #"), "authoritative IDs must not be rendered as secondary metadata");
        assertFalse(method.contains("new Circle"), "Settings must not imitate the shared mini card");
        assertFalse(method.contains("new Label"), "Settings must not imitate the shared mini card");
    }

    @Test void userManagementRowsUseTheFoundationHeightRatherThanTheRemovedLargeCardHeight() throws Exception {
        String fxml=Files.readString(Path.of("src/main/resources/fxml/settings.fxml"));
        String table=fxml.substring(fxml.indexOf("<TableView fx:id=\"userManagementTable\""), fxml.indexOf("</TableView>", fxml.indexOf("<TableView fx:id=\"userManagementTable\"")));
        assertTrue(table.contains("fixedCellSize=\"36\""));
        assertFalse(table.contains("fixedCellSize=\"50\""));
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
