package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class CaseCardFactoryTortNoticeTest {
    private static final Path CASE_CARD = Path.of("src/main/java/com/shale/ui/component/CaseCard.java");
    private static final Path FACTORY = Path.of("src/main/java/com/shale/ui/component/factory/CaseCardFactory.java");

    @Test
    void cardWithTortNoticeDeadlineRendersTcnLine() throws Exception {
        String source = Files.readString(CASE_CARD);

        assertTrue(source.contains("tortNoticeLabel.setText(show ? \"TCN: \" + tortNoticeDeadline : \"\");"));
        assertTrue(source.contains("boolean show = tortNoticeDeadline != null;"));
    }

    @Test
    void cardWithNullTortNoticeDeadlineDoesNotRenderTcnLine() throws Exception {
        String source = Files.readString(CASE_CARD);

        assertTrue(source.contains("tortNoticeLabel.setManaged(show);"));
        assertTrue(source.contains("tortNoticeLabel.setVisible(show);"));
        assertTrue(source.contains("tortNoticeLabel.setText(show ? \"TCN: \" + tortNoticeDeadline : \"\");"));
    }

    @Test
    void tcnLineUsesSameDeadlineStylePathAsSolLine() throws Exception {
        String source = Files.readString(CASE_CARD);
        String factory = Files.readString(FACTORY);

        assertTrue(source.contains("solLabel.setStyle(deadlineLabelStyle(color));"));
        assertTrue(source.contains("tortNoticeLabel.setStyle(deadlineLabelStyle(color));"));
        assertTrue(factory.contains("card.setTortNoticeDeadline(vm.tortNoticeDeadline());"));
    }
}
