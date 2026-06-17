package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class MyShaleControllerBoardLayoutTest {

    @Test
    void myCasesBoardUsesWiderStatusColumnsAndHorizontalScroll() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/shale/ui/controller/MyShaleController.java"));

        assertTrue(source.contains("MY_CASES_STATUS_COLUMN_MIN_WIDTH = 320"),
                "My Cases board lane minimum width should be widened from the previous 245px value");
        assertTrue(source.contains("MY_CASES_STATUS_COLUMN_PREF_WIDTH = 360"),
                "My Cases board lane preferred width should be widened from the previous 280px value");
        assertTrue(source.contains("MY_CASES_STATUS_COLUMN_MAX_WIDTH = 400"),
                "My Cases board lane maximum width should be widened from the previous 320px value");
        assertTrue(source.contains("myCasesBoardScroll.setFitToWidth(false)"),
                "The board should horizontally scroll instead of fitting/compressing all status lanes into the viewport");
        assertTrue(source.contains("myCasesBoardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)"));
        assertTrue(source.contains("body.setFillWidth(true)"));
        assertTrue(source.contains("buildMyCasesBoardCard"));
        assertTrue(source.contains("region.setMaxWidth(Double.MAX_VALUE)"),
                "Board cards should be allowed to fill the widened status lane body");
    }
}
