package com.shale.ui.component.dialog;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class TeamEditorDialogPhase3ContractTest {
 private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
 private static String read(String p)throws Exception{return Files.readString(ROOT.resolve(p)).replace("\r\n","\n");}
 @Test void dialogHasSingleSearchMemberListInlineRolesStableFooterAndDirtyCloseProtection()throws Exception{String s=read("shale-ui/src/main/java/com/shale/ui/component/dialog/TeamEditorDialog.java");assertTrue(s.contains("Add team member"));assertTrue(s.contains("No roles assigned"));assertTrue(s.contains("+ Add role"));assertTrue(s.contains("Remove member"));assertTrue(s.contains("stage.setOnCloseRequest"));assertTrue(s.contains("KeyCode.ESCAPE"));assertTrue(s.contains("confirmDiscard()"));assertFalse(s.contains("lvAvailable"));assertFalse(s.contains("cbPrimary"));assertFalse(s.contains("Selected member role"));}
 @Test void saveUsesOneCompleteActorAwareCommandAndPreventsDoubleSubmit()throws Exception{String s=read("shale-ui/src/main/java/com/shale/ui/component/dialog/TeamEditorDialog.java");assertTrue(s.contains("saving.compareAndSet(false,true)"));assertTrue(s.contains("service.updateCaseTeam(new CaseTeamUpdateCommand"));assertTrue(s.contains("catch(RuntimeException ex)"));assertTrue(s.contains("setSaving(false)"));}
 @Test void overviewReadsAuthoritativeMembershipsOnceAndShowsAllRolesIncludingRoleless()throws Exception{String s=read("shale-ui/src/main/java/com/shale/ui/controller/CaseController.java");assertTrue(s.contains("listCaseTeamMemberships"));assertTrue(s.contains("renderAuthoritativeTeam"));assertTrue(s.contains("No roles assigned"));assertTrue(s.contains("member.roles().stream()"));}
}
