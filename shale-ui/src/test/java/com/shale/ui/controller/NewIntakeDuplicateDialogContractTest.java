package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import com.shale.data.dao.CaseDao;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Protects the user's three-way decision and safe multi-match disambiguation before mutation. */
final class NewIntakeDuplicateDialogContractTest {
    @Test void singleAndMultipleMatchesHaveSafeExplicitMergeLabels() {
        var match=new CaseDao.IntakeDuplicateCase(41,"Smith v Jones","2026-17","Open","Alex Smith",LocalDate.of(2026,9,1));
        assertEquals("Merge Into Existing Case",NewIntakeController.mergeActionLabel(match,1));
        assertEquals("Merge Into Case 2026-17",NewIntakeController.mergeActionLabel(match,2));
        String details=NewIntakeController.duplicateDescription(match);
        assertAll(()->assertTrue(details.contains("2026-17")),()->assertTrue(details.contains("Open")),
                ()->assertTrue(details.contains("Alex Smith")),()->assertTrue(details.contains("2026-09-01")));
    }

    @Test void promptPrecedesEitherMutationAndOffersSeparateAndCancel() throws Exception {
        String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewIntakeController.java"));
        String start=method(s,"private void startPrimaryIntakeSave");
        assertTrue(start.contains("findIntakeDuplicateCases"));
        assertFalse(start.contains("createIntake(request)"));
        String resolve=method(s,"private void resolveDuplicateAndSave");
        assertTrue(resolve.contains("Create Separate Case"));
        assertTrue(resolve.contains("DialogAction.cancel(\"Cancel\""));
        assertTrue(resolve.contains("for(CaseDao.IntakeDuplicateCase duplicate:duplicates)"));
        assertTrue(resolve.contains("setSaving(false)"),"cancel must stop without submitting a mutation");
    }

    @Test void selectedMergeNavigatesToExistingCaseAndUsesDistinctConfirmation() throws Exception {
        String s=Files.readString(Path.of("src/main/java/com/shale/ui/controller/NewIntakeController.java"));
        String submit=method(s,"private void submitIntakeMutation");
        assertTrue(submit.contains("mergeIntake(mergeCaseId,request)"));
        assertTrue(s.contains("Intake information was added to the existing Case."));
        assertTrue(s.contains("onCaseCreated.accept(Math.toIntExact(result.caseId()))"));
    }

    private static String method(String source,String signature){int start=source.indexOf(signature);assertTrue(start>=0);int open=source.indexOf('{',start),depth=0;for(int i=open;i<source.length();i++){char c=source.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return source.substring(start,i+1);}throw new AssertionError("unterminated method");}
}
