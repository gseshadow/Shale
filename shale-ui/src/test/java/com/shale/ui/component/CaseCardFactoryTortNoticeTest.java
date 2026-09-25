package com.shale.ui.component;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

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

        assertTrue(source.contains("applyDeadlineState(solLabel, solDate);"));
        assertTrue(source.contains("applyDeadlineState(tortNoticeLabel, tortNoticeDeadline);"));
        assertTrue(source.contains("case-card__deadline-urgent"));
        assertTrue(factory.contains("card.setTortNoticeDeadline(vm.tortNoticeDeadline());"));
    }

    @Test
    void orderedPresentationDatesPreserveArbitraryNamesAndExplicitEmpty() {
        var dates=List.of(
                new com.shale.ui.component.factory.CaseCardFactory.PresentationDate("TYPE:42",null,"Mediation",LocalDate.of(2027,2,3),false),
                new com.shale.ui.component.factory.CaseCardFactory.PresentationDate("SYSTEM:intake","intake","Intake",LocalDate.of(2026,9,1),true));
        var model=new com.shale.ui.component.factory.CaseCardFactory.CaseCardModel(1,"A","","",false,"","","",dates);
        assertEquals(List.of("Mediation","Intake"),model.presentationDates().stream().map(com.shale.ui.component.factory.CaseCardFactory.PresentationDate::displayName).toList());
        assertTrue(model.presentationDates().get(1).pendingConfirmation());
        var empty=new com.shale.ui.component.factory.CaseCardFactory.CaseCardModel(1,"A","","",false,"","","",List.of());
        assertNotNull(empty.presentationDates(),"an explicitly empty configuration must not fall back to fixed Intake/SOL/TCN rows");
        assertTrue(empty.presentationDates().isEmpty());
    }
}
