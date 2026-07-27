package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import com.shale.core.dto.MaterialTypeDto;
import org.junit.jupiter.api.Test;

class CaseMaterialsNewRequestWindowTest {
    private static final Path SOURCE = Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java");
    private static final Path CASE_CONTROLLER = Path.of("src/main/java/com/shale/ui/controller/CaseController.java");
    private static final Path USER_SELECTION_FIELD = Path.of("src/main/java/com/shale/ui/component/UserSelectionField.java");
    private static final Path ASSIGNED_PICKER = Path.of("src/main/java/com/shale/ui/component/dialog/AssignedUserPickerDialog.java");

    @Test
    void newRequestWindowDefinesFinalFieldsInOrder() throws Exception {
        String method = methodBody(Files.readString(SOURCE), "VBox newRequestBody");
        assertTrue(method.contains("new TextField()"));
        assertTrue(method.contains("titleField.setPromptText(\"New Request\")"));
        assertFalse(method.contains("new TextField(\"New Request\")"));
        assertFalse(method.contains("titleField.setText(\"New Request\")"));
        assertInOrder(method,
                "add(fields,0,\"Title:\",titleField)",
                "add(fields,1,\"Requested From *:\",requestedFromBox)",
                "add(fields,2,\"Material Type:\",materialType)",
                "add(fields,3,\"Request Method:\",requestMethod)",
                "add(fields,4,\"Status:\",requestStatus)",
                "add(fields,5,\"Requested By:\",requestedBy)",
                "add(fields,6,\"Assigned To:\",assignedTo)",
                "add(fields,7,\"Follow-up Interval:\",followUpInterval)",
                "add(fields,8,\"Next Follow-up:\",nextFollowUpDisplay)",
                "add(fields,9,\"Description:\",description)");
        assertFalse(method.contains("\"Requested At:\""));
        assertFalse(method.contains("\"Due At:\""));
        assertFalse(method.contains("\"Next Follow-up At:\""));
        assertFalse(method.contains("new DatePicker"));
    }

    @Test
    void newRequestWindowUsesSharedComponentsForLookupsAndUsers() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");
        String userFactory = methodBody(source, "private static UserSelectionField<CaseTaskService.AssignableUserOption> newUserSelectionField");
        String fieldSource = Files.readString(USER_SELECTION_FIELD);
        String pickerSource = Files.readString(ASSIGNED_PICKER);
        assertTrue(source.contains("import com.shale.ui.component.ColorCodedComboBox;"));
        assertTrue(source.contains("import com.shale.ui.component.UserSelectionField;"));
        assertTrue(fieldSource.contains("import com.shale.ui.component.factory.UserCardFactory;"));
        assertTrue(pickerSource.contains("new UserSelector<>("));
        assertTrue(method.contains("ColorCodedComboBox<MaterialTypeDto> materialType=newLookupSelector(MaterialTypeDto::name,MaterialTypeDto::color,MaterialTypeDto::description)"));
        assertTrue(method.contains("ColorCodedComboBox<RequestMethodDto> requestMethod=newLookupSelector(RequestMethodDto::name,RequestMethodDto::color,item->null)"));
        assertTrue(method.contains("ColorCodedComboBox<RequestStatusDto> requestStatus=newLookupSelector(RequestStatusDto::name,RequestStatusDto::color,item->null)"));
        String editMethod = methodBody(source, "VBox editRequestBody");
        assertTrue(editMethod.contains("ColorCodedComboBox<RequestStatusDto> requestStatus=newLookupSelector(RequestStatusDto::name,RequestStatusDto::color,item->null)"));
        assertTrue(method.contains("UserSelectionField<CaseTaskService.AssignableUserOption> requestedBy=newUserSelectionField(stage,false)"));
        assertTrue(method.contains("UserSelectionField<CaseTaskService.AssignableUserOption> assignedTo=newUserSelectionField(stage,true)"));
        assertTrue(userFactory.contains("new UserSelectionField<>(CaseTaskService.AssignableUserOption::id,CaseTaskService.AssignableUserOption::displayName,CaseTaskService.AssignableUserOption::color"));
        assertTrue(userFactory.contains("AssignedUserPickerDialog.show(pickerOwner,candidates,CaseMaterialRequestsTabController.class)"));
        assertFalse(method.contains("new ComboBox<CaseTaskService.AssignableUserOption>"));
        assertFalse(method.contains("newUserSelector("));
        assertFalse(source.contains("private VBox userSelector"));
    }

    @Test
    void userCandidatesLoadOnceThroughServicePortOffFxThreadForBothSelectors() throws Exception {
        String source = Files.readString(SOURCE);
        String body = methodBody(source, "VBox newRequestBody");
        String loader = methodBody(source, "private void loadNewRequestUsers");
        String caseController = Files.readString(CASE_CONTROLLER);
        assertTrue(body.contains("loadNewRequestUsers(requestedBy,assignedTo,stage,loading,validate)"));
        assertTrue(loader.contains("caseTaskService.loadAssignableUsers(tid)"));
        assertEquals(1, count(loader, "loadAssignableUsers(tid)"));
        assertTrue(loader.contains("ex.submit"));
        assertTrue(loader.contains("if(Platform.isFxApplicationThread())"));
        assertTrue(loader.contains("Platform.runLater"));
        assertTrue(loader.contains("requestedBy.setCandidates(safe)"));
        assertTrue(loader.contains("assignedTo.setCandidates(safe)"));
        assertTrue(loader.contains("setUserSelectorsLoading(requestedBy,assignedTo,true)"));
        assertTrue(loader.contains("setUserSelectorsLoading(requestedBy,assignedTo,false)"));
        assertTrue(caseController.contains("caseMaterialRequestsTabController.init(materialRequestService, caseTaskService, caseService, appState, caseDao, contactDao, organizationDao"));
        assertFalse(loader.toLowerCase().contains("dao"));
    }

    @Test
    void requestedByDefaultsOnlyByCurrentUserStableIdAndAssignedToStartsUnselected() throws Exception {
        String loader = methodBody(Files.readString(SOURCE), "private void loadNewRequestUsers");
        assertTrue(loader.contains("Integer currentUserId=state==null?null:state.getUserId()"));
        assertTrue(loader.contains("u!=null&&u.id()==currentUserId"));
        assertTrue(loader.contains("ifPresent(requestedBy::setSelectedUser)"));
        assertTrue(loader.contains("assignedTo.clearSelection()"));
        assertFalse(loader.contains("displayName"));
        assertFalse(loader.contains("email"));
        assertFalse(loader.contains("selectFirst"));
    }

    @Test
    void userLoadingFailureLeavesBothSelectorsUnavailableAndShowsOneRequestsError() throws Exception {
        String loader = methodBody(Files.readString(SOURCE), "private void loadNewRequestUsers");
        assertTrue(loader.contains("requestedBy.clearSelection()"));
        assertTrue(loader.contains("assignedTo.clearSelection()"));
        assertTrue(loader.contains("requestedBy.setDisable(true)"));
        assertTrue(loader.contains("assignedTo.setDisable(true)"));
        assertEquals(1, count(loader, "AppDialogs.showError(dialogOwner,\"Requests\",\"User choices could not be loaded. Please try again.\")"));
        assertFalse(loader.contains("stage.close()"));
    }

    @Test
    void lookupsRemainServicePortLoadedAndStatusDefaultsToRequestedSystemKey() throws Exception {
        String source = Files.readString(SOURCE);
        String body = methodBody(source, "VBox newRequestBody");
        String loader = methodBody(source, "private void loadNewRequestLookups");
        String select = methodBody(source, "private static void selectRequestedStatus");
        assertTrue(body.contains("loadNewRequestLookups(materialType,requestMethod,requestStatus,stage,loading,validate)"));
        assertTrue(loader.contains("svc.listEffectiveMaterialTypes(tenant())"));
        assertTrue(loader.contains("svc.listEffectiveRequestMethods(tenant())"));
        assertTrue(loader.contains("svc.listEffectiveRequestStatuses(tenant())"));
        assertTrue(loader.contains("CaseMaterialRequestsTabController::selectRequestedStatus"));
        assertTrue(select.contains("requestStatus.getSelectionModel().clearSelection()"));
        assertTrue(select.contains("s!=null&&\"requested\".equalsIgnoreCase(s.systemKey())"));
        assertFalse(select.contains("name()"));
    }

    @Test
    void footerSaveCreatesRequestAndCancelStillConfirmsDiscard() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");
        String confirmMethod = methodBody(source, "boolean confirmDiscardNewRequest");
        assertTrue(source.contains("Button save=ActionButtonFactory.primary(\"Save\",null)"));
        assertTrue(method.contains("if(confirmDiscardNewRequest(stage))stage.close()"));
        assertTrue(confirmMethod.contains("AppDialogs.showChoice"));
        assertTrue(confirmMethod.contains("\"Discard New Request?\""));
        assertTrue(source.contains("svc.createMaterialRequest(cmd)"));
        assertFalse(method.contains("updateMaterialRequest"));
        assertFalse(method.contains("mutate("));
    }

    @Test
    void followUpIntervalIsEnabledAndDisplaysCalculatedOccurrence() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");
        assertTrue(method.contains("ChoiceBox<String> followUpInterval=new ChoiceBox<>()"));
        assertTrue(method.contains("configureFollowUpInterval(followUpInterval)"));
        assertTrue(method.contains("add(fields,7,\"Follow-up Interval:\",followUpInterval)"));
        assertTrue(method.contains("add(fields,8,\"Next Follow-up:\",nextFollowUpDisplay)"));
        assertFalse(method.contains("followUpInterval.setDisable(true)"));
        assertTrue(source.contains("case \"Daily\"->1"));
        assertTrue(source.contains("case \"Every 30 days\"->30"));
        assertTrue(source.contains("Calculated when saved"));
    }

    @Test
    void requestControllerDoesNotIntroducePersistenceDatabaseOrPermissionChanges() throws Exception {
        String source = Files.readString(SOURCE);
        String requestController = source.substring(source.indexOf("final class CaseMaterialRequestsTabController"), source.indexOf("final class CaseMaterialItemsTabController"));
        for (String forbidden : new String[]{"MaterialRequestDao","materialRequestDao","UserDao","userDao","addCaseParty"}) {
            assertFalse(requestController.contains(forbidden), forbidden);
        }
    }

    @Test
    void compactUserRowsDoNotEmbedExpandedSelectorsAndUsePickerActions() throws Exception {
        String source = Files.readString(SOURCE);
        String method = methodBody(source, "VBox newRequestBody");
        String fieldSource = Files.readString(USER_SELECTION_FIELD);
        String pickerSource = Files.readString(ASSIGNED_PICKER);
        assertFalse(method.contains("newUserSelector(\"Select requested by\""));
        assertFalse(method.contains("newUserSelector(\"Select assigned user\""));
        assertTrue(fieldSource.contains("private final Button addButton = secondary(\"Add\")"));
        assertTrue(fieldSource.contains("private final Button changeButton = secondary(\"Change\")"));
        assertTrue(fieldSource.contains("private final Button removeButton = secondary(\"Remove\")"));
        assertTrue(fieldSource.contains("UserCardFactory.Variant.MINI"));
        assertTrue(fieldSource.contains("picker.apply(this, List.copyOf(candidates)).ifPresent(this::setSelectedUser)"));
        assertTrue(pickerSource.contains("selector.selectedUserProperty().addListener"));
        assertTrue(pickerSource.contains("stage.close()"));
    }

    @Test
    void compactUserRowsKeepCandidatesSharedStableAndNonPersisted() throws Exception {
        String source = Files.readString(SOURCE);
        String loader = methodBody(source, "private void loadNewRequestUsers");
        String fieldSource = Files.readString(USER_SELECTION_FIELD);
        assertEquals(1, count(loader, "caseTaskService.loadAssignableUsers(tid)"));
        assertTrue(loader.contains("requestedBy.setCandidates(safe)"));
        assertTrue(loader.contains("assignedTo.setCandidates(safe)"));
        assertTrue(loader.contains("u!=null&&u.id()==currentUserId"));
        assertFalse(loader.contains("setExcludedUserIds"));
        assertFalse(fieldSource.contains("createMaterialRequest"));
        assertFalse(fieldSource.contains("updateMaterialRequest"));
        assertFalse(fieldSource.toLowerCase().contains("dao"));
    }


    @Test
    void sharedFormGridKeepsReadableStableLabelColumnAndGrowingControls() throws Exception {
        String source = Files.readString(SOURCE);
        String grid = methodBody(source, "static GridPane grid");
        String add = methodBody(source, "static void add");

        assertTrue(grid.contains("ColumnConstraints labels=new ColumnConstraints(132,132,132)"));
        assertTrue(grid.contains("labels.setHgrow(Priority.NEVER)"));
        assertTrue(grid.contains("controls.setHgrow(Priority.ALWAYS)"));
        assertTrue(grid.contains("controls.setFillWidth(true)"));
        assertTrue(grid.contains("g.getColumnConstraints().setAll(labels,controls)"));
        assertTrue(add.contains("Label l=new Label(label)"));
        assertTrue(add.contains("l.setWrapText(false)"));
        assertTrue(add.contains("l.setMinWidth(Region.USE_PREF_SIZE)"));
        assertFalse(add.contains("label.equals"));
        assertFalse(add.contains("setPrefWidth"));
        assertFalse(add.contains("setWrapText(true)"));
    }

    @Test
    void newRequestWindowUsesFixedHeaderFlexibleScrollAndFixedFooter() throws Exception {
        String source = Files.readString(SOURCE);
        String open = methodBody(source, "private void openNewRequestWindow");
        String body = methodBody(source, "VBox newRequestBody");
        assertTrue(open.contains("AppDialogs.createSecondaryWindowShell(stage,\"New Request\",stage::close,body)"));
        assertTrue(open.contains("stage.setResizable(true)"));
        assertTrue(open.contains("AppDialogs.installSecondaryWindowResizeHandlers(stage,root)"));
        assertTrue(body.contains("ScrollPane formScroll=new ScrollPane(formContent)"));
        assertTrue(body.contains("formScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED)"));
        assertTrue(body.contains("formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER)"));
        assertTrue(body.contains("formScroll.setFitToWidth(true)"));
        assertTrue(body.contains("formContent.prefWidthProperty().bind(formScroll.viewportBoundsProperty().map(b->b.getWidth()))"));
        assertTrue(body.contains("VBox.setVgrow(formScroll,Priority.ALWAYS)"));
        assertTrue(body.contains("VBox body=new VBox(12,formScroll,footer)"));
        assertFalse(body.contains("new VBox(18,fields,formError,footer)"));
        assertTrue(body.indexOf("ScrollPane formScroll") < body.indexOf("VBox body=new VBox(12,formScroll,footer)"));
    }

    @Test
    void newRequestSizingClampsToVisualBoundsAndCentersAfterLayout() throws Exception {
        String source = Files.readString(SOURCE);
        String sizing = methodBody(source, "private static void applyNewRequestStageSize");
        assertTrue(sizing.contains("applyCss()"));
        assertTrue(sizing.contains("layout()"));
        assertTrue(sizing.contains("root.prefHeight(width)"));
        assertTrue(sizing.contains("getVisualBounds()"));
        assertTrue(sizing.contains("stage.getOwner()"));
        assertTrue(sizing.contains("owner.getX()+(owner.getWidth()-width)/2.0"));
        assertTrue(sizing.contains("bounds.getMaxY()-stage.getHeight()"));
    }

    @Test
    void newRequestDialogUsesCompactHeightInsteadOfExpandedSelectorHeight() throws Exception {
        String source = Files.readString(SOURCE);
        assertTrue(source.contains("NEW_REQUEST_WIDTH=560, NEW_REQUEST_HEIGHT=640, NEW_REQUEST_MIN_WIDTH=500, NEW_REQUEST_MIN_HEIGHT=420"));
        assertFalse(source.contains("NEW_REQUEST_HEIGHT=760"));
        assertFalse(source.contains("NEW_REQUEST_MIN_HEIGHT=720"));
    }

    private static int count(String source, String needle) {
        int count = 0, pos = 0;
        while ((pos = source.indexOf(needle, pos)) >= 0) { count++; pos += needle.length(); }
        return count;
    }

    private static void assertInOrder(String source, String... snippets) {
        int pos = -1;
        for (String snippet : snippets) {
            int next = source.indexOf(snippet, pos + 1);
            assertTrue(next > pos, "Expected in-order snippet: " + snippet);
            pos = next;
        }
    }
    @Test
    void automaticTitleSuggestionFormatsUseMaterialTypeAndRequestedFromDisplayNamesOnly() throws Exception {
        CaseMaterialRequestsTabController.NewRequestTitleSuggester s = new CaseMaterialRequestsTabController.NewRequestTitleSuggester();
        MaterialTypeDto police = new MaterialTypeDto(10, null, "police_report", "Police Report", "", "#fff", 0);
        MaterialTypeDto medical = new MaterialTypeDto(11, null, "medical_records", "Medical Records", "", "#000", 0);
        var contact = new CaseMaterialRequestsTabController.RequestedFromSelection("contact", 1L, "John Smith", null, null);
        var org = new CaseMaterialRequestsTabController.RequestedFromSelection("organization", 2L, "Blue Cross Blue Shield", null, null);
        assertEquals("Police Report request", s.suggest(police, null));
        assertEquals("Request from John Smith", s.suggest(null, contact));
        assertEquals("Medical Records requested from Blue Cross Blue Shield", s.suggest(medical, org));
        assertEquals("", s.suggest(null, null));
    }

    @Test
    void automaticTitleListenersAreAttachedOnceAndDrivenOnlyByTypeAndRequestedFromChanges() throws Exception {
        String method = methodBody(Files.readString(SOURCE), "VBox newRequestBody");
        assertEquals(1, count(method, "new NewRequestTitleSuggester()"));
        assertEquals(1, count(method, "titleField.textProperty().addListener"));
        assertEquals(1, count(method, "materialType.valueProperty().addListener"));
        assertEquals(2, count(method, "updateSuggestedTitleRef.get().run()"));
        assertEquals(1, count(method, "requestedFrom.set(null); updateSuggestedTitleRef.get().run()"));
        assertFalse(method.contains("requestMethod.valueProperty().addListener((o,a,b)->{updateSuggestedTitle"));
        assertFalse(method.contains("requestStatus.valueProperty().addListener((o,a,b)->{updateSuggestedTitle"));
        assertFalse(method.contains("requestedBy.selectedUserProperty().addListener((o,a,b)->{updateSuggestedTitle"));
        assertFalse(method.contains("assignedTo.selectedUserProperty().addListener((o,a,b)->{updateSuggestedTitle"));
        assertFalse(method.contains("description.textProperty().addListener((o,a,b)->{updateSuggestedTitle"));
    }

    @Test
    void automaticTitleManualOverrideProgrammaticUpdateAndValidationContract() throws Exception {
        String source = Files.readString(SOURCE);
        String suggester = source.substring(source.indexOf("static final class NewRequestTitleSuggester"), source.indexOf("    VBox newRequestBody"));
        String body = methodBody(source, "VBox newRequestBody");
        String mapping = methodBody(source, "private MaterialRequestServicePort.CreateMaterialRequestCommand toCreateCommand");
        String validation = methodBody(source, "private String validateRequest");
        assertTrue(suggester.contains("private String lastAutomaticTitle = \"\""));
        assertTrue(suggester.contains("private boolean programmaticUpdate"));
        assertTrue(suggester.contains("private boolean manualOverride"));
        assertTrue(suggester.contains("if (programmaticUpdate) return"));
        assertTrue(suggester.contains("if (current.isBlank()) { manualOverride = false; return; }"));
        assertTrue(suggester.contains("manualOverride = !Objects.equals(current, lastAutomaticTitle)"));
        assertTrue(suggester.contains("if (manualOverride && !current.isBlank() && !Objects.equals(current, lastAutomaticTitle)) return"));
        assertTrue(suggester.contains("try { titleField.setText(next); } finally { programmaticUpdate = false; }"));
        assertTrue(body.contains("titleField.textProperty().addListener((o,a,b)->{titleSuggester.userEdited(a,b);validate.run();})"));
        assertTrue(mapping.contains("materialType.id(),title,description"));
        assertTrue(body.contains("toCreateCommand(rf,materialType.getValue(),titleField.getText(),description.getText()"));
        assertTrue(validation.contains("if(titleField.getText()==null||titleField.getText().trim().isEmpty())return \"Title is required.\""));
    }


    private static String methodBody(String source, String signatureStart) {
        int start = source.indexOf(signatureStart);
        assertTrue(start >= 0, "Missing method " + signatureStart);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) return source.substring(brace, i + 1);
        }
        fail("Could not parse method body for " + signatureStart);
        return "";
    }
}
