package com.shale.ui.component;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CalendarEventCardTooltipTest {
    @Test
    void persistedEventTooltipIncludesTitleAndDetailsOnlyWhenPresent() throws Exception {
        String factory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CalendarEventCardFactory.java"));
        String feedItem = Files.readString(Path.of("../shale-core/src/main/java/com/shale/core/model/CalendarFeedItem.java"));
        String feedDao = Files.readString(Path.of("../shale-data/src/main/java/com/shale/data/dao/CalendarFeedDao.java"));

        assertTrue(feedItem.contains("String details"), "Calendar feed items should carry persisted event details.");
        assertTrue(feedDao.contains("e.Description AS Details"), "Persisted calendar event details should flow from the feed query.");
        assertTrue(factory.contains("String details = normalizeTooltipValue(item == null ? null : item.details())"));
        assertTrue(factory.contains("if (details.isBlank()) return title"), "Blank details should not render an empty details row.");
        assertTrue(factory.contains("return title + \"\\n\" + details"), "Present details should appear below the full title with line breaks preserved.");
    }

    @Test
    void persistedEventTooltipIsBoundedWrappedAndAttachedToWholeCard() throws Exception {
        String factory = Files.readString(Path.of("src/main/java/com/shale/ui/component/factory/CalendarEventCardFactory.java"));

        assertTrue(factory.contains("applyCalendarItemTooltip(card, item)"), "Timed and all-day cards should install the tooltip on the card node.");
        assertTrue(factory.contains("content.setWrapText(true)"), "Long details should wrap.");
        assertTrue(factory.contains("content.setMaxWidth(360)"), "Tooltip content should have a bounded max width.");
        assertTrue(factory.contains("tooltip.setMaxWidth(380)"), "Tooltip window should have a bounded max width.");
        assertTrue(factory.contains("Tooltip.install(card, buildEventDetailsTooltip(item))"));
    }

    @Test
    void caseCalendarUsesSharedTooltipHelperWithoutReplacingClickRouting() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseController.java"));

        assertTrue(controller.contains("CalendarEventCardFactory.applyCalendarItemTooltip(row, item)"));
        assertTrue(controller.contains("configureCaseCalendarClick(row, item)"));
        assertTrue(controller.indexOf("CalendarEventCardFactory.applyCalendarItemTooltip(row, item)")
                < controller.indexOf("configureCaseCalendarClick(row, item)"),
                "Tooltip installation should not replace existing click/edit routing.");
    }
}
