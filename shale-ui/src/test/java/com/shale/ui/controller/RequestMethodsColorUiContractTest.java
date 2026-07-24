package com.shale.ui.controller;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

final class RequestMethodsColorUiContractTest {
    private static final Path SETTINGS = Path.of("src/main/java/com/shale/ui/controller/SettingsController.java");
    private static final Path MATERIALS = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");

    @Test void settingsRequestMethodCardsAndDialogUseSharedColorPresentation() throws Exception {
        String source = Files.readString(SETTINGS);
        assertTrue(source.contains("static RequestLookupSelection method(RequestMethodDto d){return new RequestLookupSelection(d.id(),safe(d.name()),\"\",safe(d.color())"));
        assertTrue(source.contains("header.getChildren().add(LinkTypeIndicatorFactory.createLinkTypePill(row.name(), row.color(), LinkTypeIndicatorFactory.PillSize.COMPACT));"));
        assertTrue(source.contains("grid.add(new Label(\"Color\"),0,r);grid.add(colorPicker,1,r++);"));
        assertTrue(source.contains("RequestMethodCommand(null,requireTenantId(),requireActorUserId(),input.name(),input.color(),input.active()"));
        assertTrue(source.contains("RequestMethodCommand(row.id(),requireTenantId(),requireActorUserId(),input.name(),input.color(),input.active()"));
        assertFalse(source.contains("kind==RequestLookupKind.REQUEST_METHOD?null:fxColorToDb"));
    }

    @Test void newAndEditRequestUseRequestMethodColorCodedComboBoxAndKeepTextContract() throws Exception {
        String source = Files.readString(MATERIALS);
        assertEquals(2, count(source, "ColorCodedComboBox<RequestMethodDto> requestMethod=newLookupSelector(RequestMethodDto::name,RequestMethodDto::color,null)"));
        assertTrue(source.contains("requestMethod.getItems().setAll(withLegacyMethod(methods,d.requestMethod()))"));
        assertTrue(source.contains("selectMethod(requestMethod,d.requestMethod())"));
        assertTrue(source.contains("requestMethod.systemKey()==null?requestMethod.name():requestMethod.systemKey()"));
        assertTrue(source.contains("effective(requestMethod.systemKey(),requestMethod.name())"));
        assertFalse(source.contains("requestMethod.id()"));
        assertTrue(source.contains("new RequestMethodDto(0,null,v,v,null,0,true,false,null)"));
    }

    private static int count(String text, String needle) {
        int c = 0, i = 0;
        while ((i = text.indexOf(needle, i)) >= 0) { c++; i += needle.length(); }
        return c;
    }
}
